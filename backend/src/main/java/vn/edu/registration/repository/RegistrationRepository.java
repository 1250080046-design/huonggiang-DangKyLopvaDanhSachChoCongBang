package vn.edu.registration.repository;
import vn.edu.registration.domain.Registration; import vn.edu.registration.domain.RegistrationStatus; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.util.List; import java.util.Optional;
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
 boolean existsByClassSection_Id(Long classId);
 Optional<Registration> findByStudent_IdAndClassSection_Id(Long studentId, Long classId);
 boolean existsByStudent_IdAndClassSection_IdAndStatus(Long studentId, Long classId, RegistrationStatus status);
 long countByClassSection_IdAndStatus(Long classId, RegistrationStatus status);
 long countByStatus(RegistrationStatus status);
 List<Registration> findByStudent_IdOrderByRegisteredAtDesc(Long studentId);
 List<Registration> findByClassSection_IdAndStatusOrderByRegisteredAtAsc(Long classId, RegistrationStatus status);
 @Query("select r from Registration r join fetch r.student s join fetch s.account a where r.classSection.id=:classId and r.status=:status order by r.registeredAt") List<Registration> findClassRegistrations(@Param("classId") Long classId, @Param("status") RegistrationStatus status);
}
