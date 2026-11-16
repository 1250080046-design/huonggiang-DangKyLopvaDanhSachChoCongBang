package vn.edu.registration.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.registration.domain.RoundRobinCandidateLog;
import java.util.List;
public interface RoundRobinCandidateLogRepository extends JpaRepository<RoundRobinCandidateLog,Long> {
 List<RoundRobinCandidateLog> findByRun_IdOrderBySequenceAsc(Long runId);
}
