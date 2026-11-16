package vn.edu.registration.repository;
import vn.edu.registration.domain.Account; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface AccountRepository extends JpaRepository<Account, Long> { Optional<Account> findByEmailIgnoreCase(String email); Optional<Account> findByUsernameIgnoreCase(String username); boolean existsByEmailIgnoreCase(String email); }
