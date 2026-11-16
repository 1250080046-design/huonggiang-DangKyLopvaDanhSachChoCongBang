package vn.edu.registration.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.registration.domain.RoundRobinRun;
import java.util.List;
public interface RoundRobinRunRepository extends JpaRepository<RoundRobinRun,Long> {
 List<RoundRobinRun> findTop100ByOrderByOccurredAtDesc();
}
