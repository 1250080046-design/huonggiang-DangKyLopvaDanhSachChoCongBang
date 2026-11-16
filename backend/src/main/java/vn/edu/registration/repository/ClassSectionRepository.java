package vn.edu.registration.repository;
import vn.edu.registration.domain.ClassSection; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.util.List; import java.util.Optional;
public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from ClassSection c where c.id = :id") Optional<ClassSection> findByIdForUpdate(@Param("id") Long id);
 List<ClassSection> findByTeacher_Id(Long teacherId);
 boolean existsByCourse_Id(Long courseId);
 boolean existsByTerm_Id(Long termId);
 /** Count directly in SQL: do not traverse lazy Term references after leaving a session. */
 @Query("select count(c) from ClassSection c join c.term t where c.status = :status "
      + "and lower(t.name) = lower(:termName) "
      + "and lower(t.academicYear) = lower(:academicYear)")
 long countInTermWithStatus(@Param("status") vn.edu.registration.domain.ClassStatus status,
                            @Param("termName") String termName,
                            @Param("academicYear") String academicYear);

}
