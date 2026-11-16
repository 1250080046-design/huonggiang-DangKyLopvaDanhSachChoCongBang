package vn.edu.registration.api;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.Term;
import java.util.List;
import vn.edu.registration.domain.ClassStatus;
import vn.edu.registration.repository.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataStatusControllerTest {
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final TermRepository terms = mock(TermRepository.class);
    private final ClassSectionRepository classes = mock(ClassSectionRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final WaitlistRepository waitlist = mock(WaitlistRepository.class);

    private DataStatusController controller() {
        DataStatusController controller = new DataStatusController(
                students, teachers, courses, terms, classes, registrations, waitlist);
        return controller;
    }

    @Test
    void countsAreLoadedFromSqlAggregatesWithoutFetchingEntities() {
        when(students.count()).thenReturn(3000L);
        when(teachers.count()).thenReturn(100L);
        when(courses.count()).thenReturn(55L);
        when(terms.count()).thenReturn(2L);
        when(classes.count()).thenReturn(136L);
        Term selected = new Term();
        selected.setName("Học kỳ 2"); selected.setAcademicYear("2026-2027"); selected.setRegistrationOpen(true);
        when(terms.findByRegistrationOpenTrue()).thenReturn(List.of(selected));
        when(classes.countInTermWithStatus(ClassStatus.OPEN, "Học kỳ 2", "2026-2027"))
                .thenReturn(70L);
        when(courses.countMissingCurriculumMetadata()).thenReturn(3L);

        Map<String, Object> result = controller().status();

        assertEquals(136L, result.get("classes"));
        assertEquals(70L, result.get("openClassesInConfiguredPeriod"));
        assertEquals(3L, result.get("coursesMissingYearOrSemester"));
        assertEquals("Học kỳ 2", result.get("configuredOpenTerm"));
        assertEquals("2026-2027", result.get("configuredOpenYear"));
        verify(classes, never()).findAll();
        verify(courses, never()).findAll();
    }

    @Test
    void emptyDatabaseReturnsZeroWithoutError() {
        Map<String, Object> result = controller().status();
        assertEquals(0L, result.get("classes"));
        assertEquals(0L, result.get("courses"));
        assertEquals(0L, result.get("openClassesInConfiguredPeriod"));
        verify(classes, never()).findAll();
    }
}
