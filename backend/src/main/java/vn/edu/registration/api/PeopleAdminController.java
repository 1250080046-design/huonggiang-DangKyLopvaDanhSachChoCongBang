package vn.edu.registration.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/admin")
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class PeopleAdminController {
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final vn.edu.registration.service.PeopleExportService exportService;
    public PeopleAdminController(StudentRepository students, TeacherRepository teachers,
            vn.edu.registration.service.PeopleExportService exportService) {
        this.students=students; this.teachers=teachers; this.exportService=exportService;
    }
    public record PersonView(Long id, String username, String fullName, String email, String faculty, String major,
                             String cohort, LocalDate dateOfBirth, String status) {}
    public record PeopleStats(long total, long active) {}
    public record PeopleFilters(java.util.List<String> studentFaculties,
                                java.util.List<String> studentMajors,
                                java.util.List<String> studentCohorts,
                                java.util.List<String> teacherFaculties,
                                java.util.List<String> teacherSpecialties,
                                long studentMajorCount) {}
    private java.util.List<String> normalizedChoices(java.util.List<String> raw) {
        return raw.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    /** Values come from SQL Server across all accounts, not the current paginated view. */
    @GetMapping("/people-filters") public PeopleFilters peopleFilters() {
        var majors = normalizedChoices(students.findDistinctMajors());
        return new PeopleFilters(normalizedChoices(students.findDistinctFaculties()), majors,
                normalizedChoices(students.findDistinctCohorts()),
                normalizedChoices(teachers.findDistinctFaculties()),
                normalizedChoices(teachers.findDistinctSpecialties()), majors.size());
    }
    public record PersonUpdate(@jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max=150) String fullName,
            @jakarta.validation.constraints.Size(max=150) String faculty,
            @jakarta.validation.constraints.Size(max=150) String major,
            @jakarta.validation.constraints.Size(max=30) String cohort,
            java.time.LocalDate dateOfBirth) {}
    private void validateBirth(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now())) throw new IllegalArgumentException("Ngày sinh không hợp lệ.");
    }
    private String value(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    @PutMapping("/students/{id}")
    @org.springframework.transaction.annotation.Transactional
    public PersonView updateStudent(@PathVariable Long id, @jakarta.validation.Valid @RequestBody PersonUpdate req) {
        Student s = students.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        validateBirth(req.dateOfBirth());
        s.setFullName(req.fullName().trim()); s.setFaculty(value(req.faculty()));
        s.setMajor(value(req.major())); s.setCohort(value(req.cohort())); s.setDateOfBirth(req.dateOfBirth());
        return view(s);
    }
    @PutMapping("/teachers/{id}")
    @org.springframework.transaction.annotation.Transactional
    public PersonView updateTeacher(@PathVariable Long id, @jakarta.validation.Valid @RequestBody PersonUpdate req) {
        Teacher t = teachers.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        validateBirth(req.dateOfBirth());
        t.setFullName(req.fullName().trim()); t.setFaculty(value(req.faculty()));
        t.setMajor(value(req.major())); t.setDateOfBirth(req.dateOfBirth());
        return new PersonView(t.getId(),t.getAccount().getUsername(),t.getFullName(),t.getAccount().getEmail(),
                t.getFaculty(),t.getMajor(),null,t.getDateOfBirth(),t.getAccount().getStatus().name());
    }
    /** Xuất toàn bộ kết quả theo cùng bộ lọc danh sách, không chỉ một trang phân trang. */
    @GetMapping("/export/students")
    public org.springframework.http.ResponseEntity<byte[]> exportStudents(
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String faculty, @RequestParam(defaultValue="") String major,
            @RequestParam(defaultValue="") String cohort, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="xlsx") String format) {
        var people = students.findAll(studentSpec(q,faculty,major,cohort,status),Sort.by("id").ascending())
                .stream().map(this::view).toList();
        return exportService.export(people,true,format);
    }
    @GetMapping("/export/teachers")
    public org.springframework.http.ResponseEntity<byte[]> exportTeachers(
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String faculty, @RequestParam(defaultValue="") String major,
            @RequestParam(defaultValue="") String specialty,
            @RequestParam(defaultValue="xlsx") String format) {
        var people = teachers.findAll(teacherSpec(q,faculty,major,specialty),Sort.by("id").ascending())
                .stream().map(t -> new PersonView(t.getId(),t.getAccount().getUsername(),t.getFullName(),
                        t.getAccount().getEmail(),t.getFaculty(),t.getMajor(),null,t.getDateOfBirth(),
                        t.getAccount().getStatus().name())).toList();
        return exportService.export(people,false,format);
    }
    @GetMapping("/students") public Page<PersonView> students(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String faculty, @RequestParam(defaultValue="") String major,
            @RequestParam(defaultValue="") String cohort, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        var spec=studentSpec(q,faculty,major,cohort,status);
        return students.findAll(spec,PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size)),Sort.by("id").ascending())).map(this::view);
    }
    @GetMapping("/students/{id}") public PersonView student(@PathVariable Long id) {
        return students.findById(id).map(this::view).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    public record AccountStatusUpdate(@jakarta.validation.constraints.NotNull AccountStatus status) {}
    /** Lock/unlock an existing account. Activation is never bypassed. */
    @PatchMapping("/students/{id}/status")
    @org.springframework.transaction.annotation.Transactional
    public PersonView changeStudentStatus(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody AccountStatusUpdate request) {
        Student student = students.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        changeStatus(student.getAccount(), request.status());
        return view(student);
    }
    @PatchMapping("/teachers/{id}/status")
    @org.springframework.transaction.annotation.Transactional
    public PersonView changeTeacherStatus(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody AccountStatusUpdate request) {
        Teacher teacher = teachers.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        changeStatus(teacher.getAccount(), request.status());
        return new PersonView(teacher.getId(), teacher.getAccount().getUsername(), teacher.getFullName(),
                teacher.getAccount().getEmail(), teacher.getFaculty(), teacher.getMajor(), null,
                teacher.getDateOfBirth(), teacher.getAccount().getStatus().name());
    }
    private void changeStatus(Account account, AccountStatus status) {
        if (status != AccountStatus.ACTIVE && status != AccountStatus.LOCKED)
            throw new IllegalArgumentException("Chỉ hỗ trợ khóa và mở khóa tài khoản.");
        if (!account.isActivated())
            throw new IllegalArgumentException("Tài khoản chưa kích hoạt. Không thể tự ý mở khóa hoặc kích hoạt.");
        if (account.getRole() != Role.STUDENT && account.getRole() != Role.TEACHER)
            throw new IllegalArgumentException("Chỉ quản lý trạng thái Sinh viên và Giảng viên tại đây.");
        account.setStatus(status);
    }

    @GetMapping("/students/stats") public PeopleStats studentStats() {
        long total=students.count();
        long active=students.count((root, query, cb)->cb.equal(root.get("account").get("status"),AccountStatus.ACTIVE));
        return new PeopleStats(total,active);
    }
    @GetMapping("/teachers") public Page<PersonView> teachers(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String faculty, @RequestParam(defaultValue="") String major,
            @RequestParam(defaultValue="") String specialty,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        // "specialty" là chuyên môn (GiangVien.BoMon); chấp nhận "major" cho client V20.26.
        var spec = teacherSpec(q,faculty,major,specialty);
        return teachers.findAll(spec,PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size)),Sort.by("id"))).map(t -> new PersonView(t.getId(),t.getAccount().getUsername(),t.getFullName(),t.getAccount().getEmail(),t.getFaculty(),t.getMajor(),null,t.getDateOfBirth(),t.getAccount().getStatus().name()));
    }
    @GetMapping("/teachers/{id}") public PersonView teacher(@PathVariable Long id) {
        return teachers.findById(id).map(t -> new PersonView(t.getId(), t.getAccount().getUsername(),
                t.getFullName(), t.getAccount().getEmail(), t.getFaculty(), t.getMajor(), null,
                t.getDateOfBirth(), t.getAccount().getStatus().name()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private PersonView view(Student s) {return new PersonView(s.getId(),s.getAccount().getUsername(),s.getFullName(),s.getAccount().getEmail(),s.getFaculty(),s.getMajor(),s.getCohort(),s.getDateOfBirth(),s.getAccount().getStatus().name());}
    private Specification<Teacher> teacherSpec(String q, String faculty, String major, String specialty) {
        String selectedSpecialty = (!specialty.isBlank() ? specialty : major).trim()
                .toLowerCase(java.util.Locale.ROOT);
        return (root,query,cb)-> {
            var p=cb.conjunction();
            if(!q.isBlank()) {
                String like="%"+q.trim().toLowerCase(java.util.Locale.ROOT)+"%";
                p=cb.and(p,cb.or(cb.like(cb.lower(root.get("fullName")),like),
                        cb.like(cb.lower(root.get("account").get("username")),like),
                        cb.like(cb.lower(root.get("account").get("email")),like)));
            }
            if(!faculty.isBlank()) p=cb.and(p,cb.like(cb.lower(root.get("faculty")),
                    "%"+faculty.trim().toLowerCase(java.util.Locale.ROOT)+"%"));
            if(!selectedSpecialty.isEmpty()) p=cb.and(p,cb.equal(cb.lower(cb.trim(root.<String>get("major"))),selectedSpecialty));
            return p;
        };
    }
    private Specification<Student> studentSpec(String q,String faculty,String major,String cohort,String status) {
        return (root,query,cb)-> {
            var p=cb.conjunction();
            if(!q.isBlank()) {String like="%"+q.toLowerCase()+"%";p=cb.and(p,cb.or(cb.like(cb.lower(root.get("fullName")),like),cb.like(cb.lower(root.get("account").get("username")),like),cb.like(cb.lower(root.get("account").get("email")),like)));}
            if(!faculty.isBlank()) p=cb.and(p,cb.like(cb.lower(root.get("faculty")),"%"+faculty.toLowerCase()+"%"));
            if(!major.isBlank()) p=cb.and(p,cb.like(cb.lower(root.get("major")),"%"+major.toLowerCase()+"%"));
            if(!cohort.isBlank()) p=cb.and(p,cb.like(cb.lower(root.get("cohort")),"%"+cohort.toLowerCase()+"%"));
            if(!status.isBlank()) {try {p=cb.and(p,cb.equal(root.get("account").get("status"),AccountStatus.valueOf(status)));} catch(IllegalArgumentException ex) {throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Trạng thái không hợp lệ");}}
            return p;
        };
    }
}
