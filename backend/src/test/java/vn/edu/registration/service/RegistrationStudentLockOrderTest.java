package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

/** Checks the lock acquisition contract, not a substitute for a real SQL Server concurrency test. */
class RegistrationStudentLockOrderTest {
    @Test void locksAllWaitingStudentsAndRequesterInAscendingStudentIdOrder() throws Exception {
        StudentRepository students = mock(StudentRepository.class);
        WaitlistRepository queue = mock(WaitlistRepository.class);
        RegistrationService service = new RegistrationService(mock(ClassSectionRepository.class),
                mock(RegistrationRepository.class), queue, students, mock(DecisionLogRepository.class),
                mock(CurrentAccountService.class), mock(RegistrationTermPolicy.class), mock(CurriculumPolicy.class),
                mock(RoundRobinRunRepository.class),mock(RoundRobinCandidateLogRepository.class),24);
        ClassSection section = new ClassSection(); section.setId(99L);
        when(queue.findByClassSection_IdAndStatus(99L,WaitlistStatus.WAITING)).thenReturn(
                List.of(entry(40L),entry(10L),entry(30L)));
        for (long id : new long[] {10,20,30,40}) {
            Student student = new Student(); student.setId(id);
            when(students.findByIdForUpdate(id)).thenReturn(Optional.of(student));
        }
        Method method=RegistrationService.class.getDeclaredMethod("lockStudentsInOrder",ClassSection.class,Long[].class);
        method.setAccessible(true);
        method.invoke(service, section, new Long[]{20L,30L});
        var inOrder=inOrder(students);
        inOrder.verify(students).findByIdForUpdate(10L);
        inOrder.verify(students).findByIdForUpdate(20L);
        inOrder.verify(students).findByIdForUpdate(30L);
        inOrder.verify(students).findByIdForUpdate(40L);
        verify(students,times(4)).findByIdForUpdate(anyLong());
    }
    private WaitlistEntry entry(Long id) {
        Student s=new Student(); s.setId(id);
        WaitlistEntry e=new WaitlistEntry(); e.setStudent(s); return e;
    }
}
