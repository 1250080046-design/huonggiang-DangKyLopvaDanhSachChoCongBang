package vn.edu.registration.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.edu.registration.domain.ConversationStatus;
import vn.edu.registration.service.ChatService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping("/conversations")
    public ApiDtos.ConversationView create() {
        return service.createOrGet();
    }

    @GetMapping("/conversations")
    public List<ApiDtos.ConversationView> listMine() {
        return service.listMine();
    }

    @GetMapping("/admin/conversations")
    public List<ApiDtos.ConversationView> adminList() {
        return service.listMine();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ApiDtos.MessageView> messages(@PathVariable Long id) {
        return service.messages(id);
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiDtos.MessageView send(@PathVariable Long id, @Valid @RequestBody ApiDtos.CreateMessageRequest req) {
        return service.send(id, req);
    }

    public record TypingRequest(boolean typing) {
    }

    @GetMapping("/conversations/{id}/typing")
    public Map<String, Boolean> typing(@PathVariable Long id) {
        return Map.of("typing", service.otherPartyTyping(id));
    }

    @PostMapping("/conversations/{id}/typing")
    public Map<String, Boolean> setTyping(@PathVariable Long id, @RequestBody TypingRequest req) {
        service.setTyping(id, req.typing());
        return Map.of("typing", req.typing());
    }

    @PatchMapping("/admin/conversations/{id}/status")
    public ApiDtos.ConversationView status(@PathVariable Long id, @RequestParam ConversationStatus value) {
        return service.setStatus(id, value);
    }
}
