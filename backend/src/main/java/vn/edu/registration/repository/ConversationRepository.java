package vn.edu.registration.repository;
import vn.edu.registration.domain.Conversation; import vn.edu.registration.domain.ConversationStatus; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface ConversationRepository extends JpaRepository<Conversation, Long> { List<Conversation> findByUserAccount_IdOrderByUpdatedAtDesc(Long accountId); List<Conversation> findAllByOrderByUpdatedAtDesc(); Optional<Conversation> findFirstByUserAccount_IdAndStatusOrderByUpdatedAtDesc(Long accountId, ConversationStatus status); }
