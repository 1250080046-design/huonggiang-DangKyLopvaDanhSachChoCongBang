package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassServiceChangeCourseTest {
    private final ClassSectionRepository sections = mock(ClassSectionRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final WaitlistRepository waitlist = mock(WaitlistRepository.class);
    private final DecisionLogRepository decisions = mock(DecisionLogRepository.class);
    private final ClassService service = new ClassService(sections, courses, mock(TermRepository.class),
            mock(TeacherRepository.class), mock(RegistrationService.class), mock(RegistrationTermPolicy.class),
            mock(CurriculumPolicy.class), mock(CurrentAccountService.class), registrations, waitlist, decisions);

    private ClassSection original() {
        Course course = new Course(); course.setId(1L);
        ClassSection section = new ClassSection(); section.setId(10L); section.setCourse(course);
        section.setName("OLD-01"); section.setStatus(ClassStatus.OPEN);
        return section;
    }

    private ApiDtos.UpdateClassRequest change() {
        return new ApiDtos.UpdateClassRequest("NEW-01", 5L, "A201", "Thứ 3 07:00-09:00", 30,
                ClassStatus.OPEN, 2L);
    }

    @Test void blocksCourseChangeWhenRegistrationHistoryExists() {
        ClassSection section = original();
        when(sections.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(registrations.existsByClassSection_Id(10L)).thenReturn(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateDetails(10L, change()));
        assertTrue(ex.getMessage().contains("Không thể đổi học phần"));
        assertEquals(1L, section.getCourse().getId());
        verifyNoInteractions(courses);
    }

    @Test void blocksCourseChangeWhenWaitlistHistoryExists() {
        ClassSection section = original();
        when(sections.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(waitlist.existsByClassSection_Id(10L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.updateDetails(10L, change()));
        assertEquals(1L, section.getCourse().getId());
        verifyNoInteractions(courses);
    }

    @Test void blocksCourseChangeWhenRoundRobinDecisionHistoryExists() {
        ClassSection section = original();
        when(sections.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(decisions.existsByClassId(10L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.updateDetails(10L, change()));
        assertEquals(1L, section.getCourse().getId());
        verifyNoInteractions(courses);
    }
}
