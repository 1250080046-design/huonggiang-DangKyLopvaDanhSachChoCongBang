package vn.edu.registration.repository;
import vn.edu.registration.domain.ChatMessage; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> { List<ChatMessage> findByConversation_IdOrderBySentAtAscIdAsc(Long conversationId); }
