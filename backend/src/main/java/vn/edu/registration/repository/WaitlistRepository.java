package vn.edu.registration.repository;
import vn.edu.registration.domain.WaitlistEntry; import vn.edu.registration.domain.WaitlistStatus; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
 boolean existsByClassSection_Id(Long classId);
 Optional<WaitlistEntry> findByStudent_IdAndClassSection_Id(Long studentId, Long classId);
 List<WaitlistEntry> findByStudent_IdOrderByJoinedAtDesc(Long studentId);
 List<WaitlistEntry> findByStatus(WaitlistStatus status);
 List<WaitlistEntry> findByClassSection_IdAndStatus(Long classId, WaitlistStatus status);
 long countByClassSection_IdAndStatus(Long classId, WaitlistStatus status);
 long countByStatus(WaitlistStatus status);
}
