package vn.edu.registration.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.time.LocalDateTime;
import java.util.List;
@RestController @RequestMapping("/api/admin/round-robin-runs")
public class RoundRobinAuditController {
 private final RoundRobinRunRepository runs;
 private final RoundRobinCandidateLogRepository candidates;
 private final ClassSectionRepository classes;
 private final StudentRepository students;
 public RoundRobinAuditController(RoundRobinRunRepository runs, RoundRobinCandidateLogRepository candidates,
          ClassSectionRepository classes, StudentRepository students) {
   this.runs=runs; this.candidates=candidates; this.classes=classes; this.students=students;
 }
 public record RunView(Long id,Long classId,String className,LocalDateTime occurredAt,String trigger,int enrolledBefore,int enrolledAfter,int capacity,Long cursorBefore,Long cursorAfter,int promotedCount,String outcome) {}
 public record CandidateView(int sequence,Long studentId,String studentName,Long waitlistId,String result,String reason) {}
 @GetMapping @Transactional(readOnly=true)
 public List<RunView> recent() { return runs.findTop100ByOrderByOccurredAtDesc().stream().map(r->new RunView(
    r.getId(),r.getClassId(),classes.findById(r.getClassId()).map(ClassSection::getName).orElse("Lớp #"+r.getClassId()),
    r.getOccurredAt(),r.getTrigger(),r.getEnrolledBefore(),r.getEnrolledAfter(),r.getCapacity(),r.getCursorBefore(),r.getCursorAfter(),r.getPromotedCount(),r.getOutcome())).toList(); }
 @GetMapping("/{id}/candidates") @Transactional(readOnly=true)
 public List<CandidateView> detail(@PathVariable Long id) {
    if (!runs.existsById(id)) throw new jakarta.persistence.EntityNotFoundException("Không tìm thấy lượt xét Round Robin.");
    return candidates.findByRun_IdOrderBySequenceAsc(id).stream().map(c->new CandidateView(c.getSequence(),c.getStudentId(),
      students.findById(c.getStudentId()).map(Student::getFullName).orElse("Sinh viên #"+c.getStudentId()),c.getWaitlistId(),c.getResult(),c.getReason())).toList();
 }
}
