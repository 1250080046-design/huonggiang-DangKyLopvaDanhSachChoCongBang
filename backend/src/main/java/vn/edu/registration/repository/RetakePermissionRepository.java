package vn.edu.registration.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.registration.domain.RetakePermission;
import java.util.List;
public interface RetakePermissionRepository extends JpaRepository<RetakePermission,Long> {
    boolean existsByStudent_IdAndCourse_Id(Long studentId, Long courseId);
    List<RetakePermission> findByStudent_Id(Long studentId);
    void deleteByStudent_Id(Long studentId);
}
