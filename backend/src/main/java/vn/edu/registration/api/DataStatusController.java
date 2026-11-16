package vn.edu.registration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import vn.edu.registration.domain.ClassStatus;
import vn.edu.registration.repository.*;
import java.util.Map;

/**
 * Read-only, ADMIN-only SQL diagnostics. No fabricated counts, no mutations.
 */
@RestController
@RequestMapping("/api/admin/data-status")
public class DataStatusController {
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final CourseRepository courses;
    private final TermRepository terms;
    private final ClassSectionRepository classes;
    private final RegistrationRepository registrations;
    private final WaitlistRepository waitlist;
    @Value("${app.academic-data.enabled:false}")
    private boolean academicImportEnabled;

    public DataStatusController(StudentRepository students, TeacherRepository teachers,
            CourseRepository courses, TermRepository terms,
            ClassSectionRepository classes, RegistrationRepository registrations,
            WaitlistRepository waitlist) {
        this.students = students;
        this.teachers = teachers;
        this.courses = courses;
        this.terms = terms;
        this.classes = classes;
        this.registrations = registrations;
        this.waitlist = waitlist;
    }

    @GetMapping
    public Map<String, Object> status() {
        // These aggregates execute in the database. Calling c.getTerm().getName()
        // on the LAZY relation outside a transaction caused HTTP 500 once classes
        // existed (spring.jpa.open-in-view=false).
        // Reflect the database's active registration round, not stale environment
        // defaults.
        var activeTerm = terms.findByRegistrationOpenTrue().stream().findFirst().orElse(null);
        String openTerm = activeTerm == null ? "Chưa mở" : activeTerm.getName();
        String openYear = activeTerm == null ? "—" : activeTerm.getAcademicYear();
        boolean withinWindow = activeTerm != null
                && (activeTerm.getRegistrationStartsAt() == null
                        || !java.time.LocalDateTime.now().isBefore(activeTerm.getRegistrationStartsAt()))
                && (activeTerm.getRegistrationEndsAt() == null
                        || !java.time.LocalDateTime.now().isAfter(activeTerm.getRegistrationEndsAt()));
        long openInPeriod = withinWindow ? classes.countInTermWithStatus(ClassStatus.OPEN, openTerm, openYear) : 0;
        long noCurriculum = courses.countMissingCurriculumMetadata();
        return Map.ofEntries(
                Map.entry("students", students.count()),
                Map.entry("teachers", teachers.count()),
                Map.entry("courses", courses.count()),
                Map.entry("terms", terms.count()),
                Map.entry("classes", classes.count()),
                Map.entry("openClassesInConfiguredPeriod", openInPeriod),
                Map.entry("coursesMissingYearOrSemester", noCurriculum),
                Map.entry("registrations", registrations.count()),
                Map.entry("waitlistEntries", waitlist.count()),
                Map.entry("configuredOpenTerm", openTerm),
                Map.entry("configuredOpenYear", openYear),
                Map.entry("academicImportEnabled", academicImportEnabled));
    }
}
