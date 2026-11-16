package vn.edu.registration.repository;
import vn.edu.registration.domain.Term; import org.springframework.data.jpa.repository.JpaRepository;
public interface TermRepository extends JpaRepository<Term, Long> {
 java.util.List<Term> findByRegistrationOpenTrue();
 }
