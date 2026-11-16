package vn.edu.registration.repository;
import vn.edu.registration.domain.DecisionLog; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface DecisionLogRepository extends JpaRepository<DecisionLog, Long> { boolean existsByClassId(Long classId); List<DecisionLog> findTop100ByOrderByOccurredAtDesc(); }
