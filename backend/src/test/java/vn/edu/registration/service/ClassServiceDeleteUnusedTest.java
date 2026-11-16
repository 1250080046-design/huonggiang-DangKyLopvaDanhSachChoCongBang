package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.ClassSection;
import vn.edu.registration.repository.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassServiceDeleteUnusedTest {
    private final ClassSectionRepository classes = mock(ClassSectionRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final WaitlistRepository waitlist = mock(WaitlistRepository.class);
    private final DecisionLogRepository decisions = mock(DecisionLogRepository.class);
    private final ClassService service = new ClassService(classes, mock(CourseRepository.class), mock(TermRepository.class),
            mock(TeacherRepository.class), mock(RegistrationService.class), mock(RegistrationTermPolicy.class),
            mock(CurriculumPolicy.class), mock(CurrentAccountService.class), registrations, waitlist, decisions);

    @Test void deletesOnlyUnusedClass() {
        ClassSection section = new ClassSection(); section.setId(9L);
        when(classes.findByIdForUpdate(9L)).thenReturn(Optional.of(section));
        service.deleteUnusedClass(9L);
        verify(classes).delete(section);
        verify(classes).flush();
    }
    @Test void refusesClassWithHistoricalRegistrations() {
        when(classes.findByIdForUpdate(9L)).thenReturn(Optional.of(new ClassSection()));
        when(registrations.existsByClassSection_Id(9L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.deleteUnusedClass(9L));
        verify(classes, never()).delete(any());
    }
    @Test void refusesClassWithWaitlistEvenWithoutRegistrations() {
        when(classes.findByIdForUpdate(9L)).thenReturn(Optional.of(new ClassSection()));
        when(waitlist.existsByClassSection_Id(9L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.deleteUnusedClass(9L));
        verify(classes, never()).delete(any());
    }
    @Test void refusesClassWithDecisionLog() {
        when(classes.findByIdForUpdate(9L)).thenReturn(Optional.of(new ClassSection()));
        when(decisions.existsByClassId(9L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.deleteUnusedClass(9L));
        verify(classes, never()).delete(any());
    }
    @Test void rejectsUnknownClass() {
        when(classes.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.deleteUnusedClass(99L));
        verify(classes, never()).delete(any());
    }
}
