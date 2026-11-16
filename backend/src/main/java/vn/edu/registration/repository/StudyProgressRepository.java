package vn.edu.registration.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.registration.domain.StudyProgress;
public interface StudyProgressRepository extends JpaRepository<StudyProgress,Long> {}
