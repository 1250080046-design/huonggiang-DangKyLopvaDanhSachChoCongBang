package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos; import vn.edu.registration.domain.*; import vn.edu.registration.repository.*;
import java.time.LocalDateTime; import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class ChatService {
 // Transient typing presence: no message text is stored and entries expire automatically.
 private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Long>> typingPresence = new ConcurrentHashMap<>();
 private static final long TYPING_TTL_MS = 4500L;
 private final ConversationRepository conversations; private final ChatMessageRepository messages; private final CurrentAccountService current; private final AccountRepository accounts;
 public ChatService(ConversationRepository c, ChatMessageRepository m, CurrentAccountService ca, AccountRepository a) { conversations=c; messages=m; current=ca; accounts=a; }
 @Transactional
 public ApiDtos.ConversationView createOrGet() {
  Account a=current.account(); if(a.getRole()!=Role.STUDENT) throw new IllegalArgumentException("Chức năng tạo cuộc trò chuyện dành cho sinh viên.");
  // ADMIN replies change OPEN -> IN_PROGRESS. Always resume the latest existing
  // conversation instead of creating a second thread after page refresh.
  List<Conversation> existing=conversations.findByUserAccount_IdOrderByUpdatedAtDesc(a.getId());
  Conversation c=existing.stream().filter(item->item.getStatus()!=ConversationStatus.CLOSED).findFirst()
      .orElse(existing.isEmpty() ? null : existing.get(0));
  if(c==null) { c=new Conversation(); c.setUserAccount(a); c.setStatus(ConversationStatus.OPEN); c.setCreatedAt(LocalDateTime.now()); c.setUpdatedAt(LocalDateTime.now()); c=conversations.save(c); }
  else if(c.getStatus()==ConversationStatus.CLOSED) { c.setStatus(ConversationStatus.OPEN); c.setUpdatedAt(LocalDateTime.now()); }
  return view(c);
 }
 @Transactional(readOnly=true) public List<ApiDtos.ConversationView> listMine() {
  Account a=current.account(); List<Conversation> list=a.getRole()==Role.ADMIN?conversations.findAllByOrderByUpdatedAtDesc():conversations.findByUserAccount_IdOrderByUpdatedAtDesc(a.getId());
  return list.stream().map(this::view).toList();
 }
 @Transactional(readOnly=true) public List<ApiDtos.MessageView> messages(Long id) { Conversation c=authorizedConversation(id); return messages.findByConversation_IdOrderBySentAtAscIdAsc(c.getId()).stream().map(this::messageView).toList(); }
 @Transactional public ApiDtos.MessageView send(Long id, ApiDtos.CreateMessageRequest req) {
  Conversation c=authorizedConversation(id); Account sender=current.account();
  // Writing in an old CLOSED thread explicitly reopens that same thread.
  // This preserves its SQL-stored message history and allows replies after reload.
  if(c.getStatus()==ConversationStatus.CLOSED) c.setStatus(ConversationStatus.OPEN);
  ChatMessage m=new ChatMessage(); m.setConversation(c); m.setSender(sender); m.setContent(req.content().trim()); m.setSentAt(LocalDateTime.now()); messages.save(m);
  c.setUpdatedAt(LocalDateTime.now()); if(sender.getRole()==Role.ADMIN && c.getStatus()==ConversationStatus.OPEN) c.setStatus(ConversationStatus.IN_PROGRESS);
  return messageView(m);
 }
 @Transactional public ApiDtos.ConversationView setStatus(Long id, ConversationStatus status) {
  Account a=current.account(); if(a.getRole()!=Role.ADMIN) throw new IllegalArgumentException("Chỉ quản trị viên được cập nhật trạng thái.");
  Conversation c=conversations.findById(id).orElseThrow(()->new EntityNotFoundException("Không tìm thấy cuộc trò chuyện.")); c.setStatus(status); c.setUpdatedAt(LocalDateTime.now()); return view(c);
 }
 public void setTyping(Long conversationId, boolean typing) {
  authorizedConversation(conversationId); // Same ownership check as reading messages.
  Account sender=current.account();
  if(sender.getRole()!=Role.ADMIN && sender.getRole()!=Role.STUDENT) throw new IllegalArgumentException("Không có quyền gửi trạng thái soạn tin.");
  ConcurrentHashMap<String,Long> presence=typingPresence.computeIfAbsent(conversationId, unused->new ConcurrentHashMap<>());
  if(typing) presence.put(sender.getEmail().toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis());
  else presence.remove(sender.getEmail().toLowerCase(java.util.Locale.ROOT));
  if(presence.isEmpty()) typingPresence.remove(conversationId, presence);
 }
 public boolean otherPartyTyping(Long conversationId) {
  authorizedConversation(conversationId); // Do not expose presence from other students' conversations.
  Account viewer=current.account();
  Map<String,Long> presence=typingPresence.get(conversationId);
  if(presence==null) return false;
  long cutoff=System.currentTimeMillis()-TYPING_TTL_MS;
  presence.entrySet().removeIf(entry->entry.getValue()<cutoff);
  if(presence.isEmpty()) { typingPresence.remove(conversationId, presence); return false; }
  return presence.entrySet().stream().anyMatch(entry->!entry.getKey().equalsIgnoreCase(viewer.getEmail()));
 }
 private Conversation authorizedConversation(Long id) {
  Conversation c=conversations.findById(id).orElseThrow(()->new EntityNotFoundException("Không tìm thấy cuộc trò chuyện.")); Account a=current.account();
  if(a.getRole()!=Role.ADMIN && !c.getUserAccount().getId().equals(a.getId())) throw new IllegalArgumentException("Bạn không có quyền truy cập cuộc trò chuyện này.");
  return c;
 }
 private ApiDtos.ConversationView view(Conversation c) {
  String name=c.getUserAccount().getEmail();
  return new ApiDtos.ConversationView(c.getId(), c.getUserAccount().getEmail(), name, c.getStatus().name(), c.getUpdatedAt());
 }
 private ApiDtos.MessageView messageView(ChatMessage m) { return new ApiDtos.MessageView(m.getId(),m.getConversation().getId(),m.getSender().getEmail(),m.getSender().getRole().name(),m.getContent(),m.getSentAt()); }
}
