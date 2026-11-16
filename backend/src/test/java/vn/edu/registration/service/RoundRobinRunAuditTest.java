package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.api.RoundRobinAuditController;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoundRobinRunAuditTest {
 @Test void returnsCursorAndOrderedCandidateDetails() {
    var runs=mock(RoundRobinRunRepository.class);
    var candidates=mock(RoundRobinCandidateLogRepository.class);
    var classes=mock(ClassSectionRepository.class);
    var students=mock(StudentRepository.class);
    RoundRobinRun run=new RoundRobinRun(); run.setId(4L);run.setClassId(7L);
    run.setOccurredAt(LocalDateTime.of(2026,11,15,16,0));run.setTrigger("Sinh viên hủy đăng ký");
    run.setEnrolledBefore(1);run.setEnrolledAfter(2);run.setCapacity(2);run.setCursorBefore(8L);
    run.setCursorAfter(12L);run.setPromotedCount(1);run.setOutcome("PROMOTED");
    ClassSection c=new ClassSection();c.setName("SWE423-HK2-01");
    when(runs.findTop100ByOrderByOccurredAtDesc()).thenReturn(List.of(run));
    when(classes.findById(7L)).thenReturn(Optional.of(c));
    when(runs.existsById(4L)).thenReturn(true);
    RoundRobinCandidateLog first=new RoundRobinCandidateLog();first.setSequence(1);first.setStudentId(9L);
    first.setResult("SKIPPED_INELIGIBLE");first.setReason("Trùng lịch học");
    RoundRobinCandidateLog second=new RoundRobinCandidateLog();second.setSequence(2);second.setStudentId(12L);
    second.setResult("PROMOTED_ROUND_ROBIN");
    when(candidates.findByRun_IdOrderBySequenceAsc(4L)).thenReturn(List.of(first,second));
    Student s9=new Student();s9.setFullName("Sinh viên thử 9");
    Student s12=new Student();s12.setFullName("Sinh viên thử 12");
    when(students.findById(9L)).thenReturn(Optional.of(s9));
    when(students.findById(12L)).thenReturn(Optional.of(s12));
    var controller=new RoundRobinAuditController(runs,candidates,classes,students);
    var result=controller.recent();
    assertEquals("SWE423-HK2-01",result.get(0).className());
    assertEquals(8L,result.get(0).cursorBefore());
    assertEquals(12L,result.get(0).cursorAfter());
    assertEquals(1,result.get(0).promotedCount());
    var details=controller.detail(4L);
    assertEquals(2,details.size());
    assertEquals("SKIPPED_INELIGIBLE",details.get(0).result());
    assertEquals("Sinh viên thử 12",details.get(1).studentName());
 }
}
