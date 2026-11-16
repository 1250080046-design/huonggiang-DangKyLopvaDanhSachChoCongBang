package vn.edu.registration.repository;
import vn.edu.registration.domain.Student; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface StudentRepository extends JpaRepository<Student, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Student> { Optional<Student> findByAccount_EmailIgnoreCase(String email);
    // Lock a stable student row for all enrollment/placement decisions. Lock order: class -> student.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select distinct s.faculty from Student s where s.faculty is not null")
    java.util.List<String> findDistinctFaculties();
    @org.springframework.data.jpa.repository.Query("select distinct s.major from Student s where s.major is not null")
    java.util.List<String> findDistinctMajors();
    @org.springframework.data.jpa.repository.Query("select distinct s.cohort from Student s where s.cohort is not null")
    java.util.List<String> findDistinctCohorts();
}
