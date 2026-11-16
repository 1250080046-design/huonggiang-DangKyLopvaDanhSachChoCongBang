package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.ClassSection;
import vn.edu.registration.domain.ClassStatus;
import vn.edu.registration.repository.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassServiceBulkCloseTest {
    private final ClassSectionRepository classes = mock(ClassSectionRepository.class);
    private final ClassService service = new ClassService(classes, mock(CourseRepository.class), mock(TermRepository.class),
            mock(TeacherRepository.class), mock(RegistrationService.class), mock(RegistrationTermPolicy.class),
            mock(CurriculumPolicy.class), mock(CurrentAccountService.class),
            mock(RegistrationRepository.class), mock(WaitlistRepository.class), mock(DecisionLogRepository.class));

    private ClassSection section(long id, ClassStatus status) {
        ClassSection section = new ClassSection();
        section.setId(id); section.setStatus(status); section.setEnrolled(9);
        return section;
    }

    @Test void closesOnlyOpenClassesWithoutRemovingEnrollmentAndDeduplicatesIds() {
        ClassSection open = section(1L, ClassStatus.OPEN);
        ClassSection alreadyClosed = section(2L, ClassStatus.CLOSED);
        when(classes.findByIdForUpdate(1L)).thenReturn(Optional.of(open));
        when(classes.findByIdForUpdate(2L)).thenReturn(Optional.of(alreadyClosed));
        ClassService.BulkCloseResult result = service.closeClasses(List.of(2L, 1L, 1L));
        assertEquals(1, result.closed());
        assertEquals(1, result.skipped());
        assertEquals(ClassStatus.CLOSED, open.getStatus());
        assertEquals(9, open.getEnrolled());
        assertEquals(ClassStatus.CLOSED, alreadyClosed.getStatus());
        verify(classes, times(1)).findByIdForUpdate(1L);
        verify(classes, times(1)).findByIdForUpdate(2L);
        verify(classes, never()).delete(any());
    }

    @Test void refusesToModifyAnyClassIfOneSelectedIdIsMissing() {
        ClassSection open = section(1L, ClassStatus.OPEN);
        when(classes.findByIdForUpdate(1L)).thenReturn(Optional.of(open));
        when(classes.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.closeClasses(List.of(1L, 2L)));
        assertEquals(ClassStatus.OPEN, open.getStatus());
    }

    @Test void rejectsEmptyAndOversizedRequests() {
        assertThrows(IllegalArgumentException.class, () -> service.closeClasses(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.closeClasses(java.util.Collections.nCopies(201, 1L)));
        verifyNoInteractions(classes);
    }
}
