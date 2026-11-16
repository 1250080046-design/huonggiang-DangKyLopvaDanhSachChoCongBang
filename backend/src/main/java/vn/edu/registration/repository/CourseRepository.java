package vn.edu.registration.repository;
import vn.edu.registration.domain.Course; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface CourseRepository extends JpaRepository<Course, Long> { Optional<Course> findByCodeIgnoreCase(String code);
 @org.springframework.data.jpa.repository.Query("select count(c) from Course c where c.year is null or c.semester is null")
 long countMissingCurriculumMetadata();
}
