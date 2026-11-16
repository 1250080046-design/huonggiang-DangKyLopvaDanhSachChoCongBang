package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaitlistRejoinPolicyTest {
    private final WaitlistRepository waitlist=mock(WaitlistRepository.class);
    private final CurrentAccountService current=mock(CurrentAccountService.class);
    private final ClassSectionRepository classes=mock(ClassSectionRepository.class);
    private final StudentRepository students=mock(StudentRepository.class);
    private final RegistrationService service=new RegistrationService(classes,mock(RegistrationRepository.class),waitlist,students,
            mock(DecisionLogRepository.class),current,mock(RegistrationTermPolicy.class),mock(CurriculumPolicy.class),
            mock(RoundRobinRunRepository.class),mock(RoundRobinCandidateLogRepository.class),24);

    @Test void cannotRejoinAnotherStudentsWaitlist() {
        Student owner=new Student();owner.setId(8L);
        Student caller=new Student();caller.setId(9L);
        WaitlistEntry entry=new WaitlistEntry();entry.setStudent(owner);entry.setStatus(WaitlistStatus.INELIGIBLE);
        when(waitlist.findById(3L)).thenReturn(Optional.of(entry));
        when(current.student()).thenReturn(caller);
        assertThrows(IllegalArgumentException.class,()->service.rejoinWaitlist(3L));
        verifyNoInteractions(classes);
        verify(waitlist,never()).save(any());
    }

    @Test void cannotRejoinAlreadyWaitingEntry() {
        Student owner=new Student();owner.setId(8L);
        ClassSection section=new ClassSection();section.setId(4L);
        WaitlistEntry entry=new WaitlistEntry();entry.setStudent(owner);entry.setClassSection(section);
        entry.setStatus(WaitlistStatus.WAITING);
        when(waitlist.findById(3L)).thenReturn(Optional.of(entry));
        when(current.student()).thenReturn(owner);
        when(classes.findByIdForUpdate(4L)).thenReturn(Optional.of(section));
        when(students.findByIdForUpdate(8L)).thenReturn(Optional.of(owner));
        assertThrows(IllegalArgumentException.class,()->service.rejoinWaitlist(3L));
        verify(waitlist,never()).save(any());
    }
}
