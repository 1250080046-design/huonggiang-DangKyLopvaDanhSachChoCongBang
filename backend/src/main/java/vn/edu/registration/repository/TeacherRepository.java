package vn.edu.registration.repository;
import vn.edu.registration.domain.Teacher; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface TeacherRepository extends JpaRepository<Teacher, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Teacher> { Optional<Teacher> findByAccount_EmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Query("select distinct t.faculty from Teacher t where t.faculty is not null")
    java.util.List<String> findDistinctFaculties();
    @org.springframework.data.jpa.repository.Query("select distinct t.major from Teacher t where t.major is not null")
    java.util.List<String> findDistinctSpecialties();
}
