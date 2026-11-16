package vn.edu.registration.repository;
import vn.edu.registration.domain.ActivationToken; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> { Optional<ActivationToken> findByTokenHashAndUsedAtIsNull(String tokenHash); }
