package vn.edu.registration.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import vn.edu.registration.domain.*; import vn.edu.registration.repository.*; import vn.edu.registration.service.*; import java.util.List;
@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final RegistrationService registrations; private final AdminService admin; private final ClassService classes; private final ActivationService activation;
 private final CourseRepository courses; private final TermRepository terms; private final TeacherRepository teachers; private final AdminMonitoringService monitoring;
 public AdminController(AdminService admin, ClassService classes, ActivationService activation, CourseRepository courses, TermRepository terms, TeacherRepository teachers, AdminMonitoringService monitoring, RegistrationService registrations) {
  this.admin=admin; this.classes=classes; this.activation=activation; this.courses=courses; this.terms=terms; this.teachers=teachers; this.monitoring=monitoring; this.registrations=registrations;
 }
 @PostMapping("/accounts") public ApiDtos.AccountCreated createAccount(
        @Valid @RequestBody ApiDtos.CreateAccountRequest request) { return activation.createAccount(request); }
 /** Non-sensitive build marker to detect an outdated Spring Boot process behind Next.js. */
 @GetMapping("/runtime") public RuntimeInfo runtime() { return new RuntimeInfo("V20.68", true, true); }
 public record RuntimeInfo(String version, boolean classReopenIndependentOfRegistrationWindow, boolean deleteUnusedClassSupported) {}
 @GetMapping("/summary") public ApiDtos.AdminSummary summary() { return admin.summary(); }
 @GetMapping("/waitlist") public List<ApiDtos.AdminWaitlistView> waitlist(@RequestParam(required=false) WaitlistStatus status) { return monitoring.waitlist(status); }
 @GetMapping("/decisions") public List<ApiDtos.DecisionView> decisions() { return monitoring.recentDecisions(); }
 @GetMapping("/classes") public List<ApiDtos.ClassView> allClasses() { return classes.search(null); }
 @PostMapping("/classes") public ApiDtos.ClassView createClass(@Valid @RequestBody ApiDtos.CreateClassRequest req) { return classes.create(req); }
 @DeleteMapping("/classes/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteClass(@PathVariable Long id) { classes.deleteUnusedClass(id); }
 @PutMapping("/classes/{id}") public ApiDtos.ClassView updateClass(@PathVariable Long id, @Valid @RequestBody ApiDtos.UpdateClassRequest req) { return classes.updateDetails(id, req); }
 @PatchMapping("/classes/{id}/capacity") public ApiDtos.ClassView capacity(@PathVariable Long id, @Valid @RequestBody ApiDtos.UpdateCapacityRequest req) { return classes.changeCapacity(id, req.capacity()); }
 @PatchMapping("/classes/{id}/status") public java.util.Map<String,String> status(@PathVariable Long id, @RequestParam ClassStatus value) { classes.changeStatus(id,value); return java.util.Map.of("message","Đã cập nhật trạng thái lớp."); }
 /** Chỉ đóng lớp đang mở, không hủy lớp hay xóa đăng ký/danh sách chờ. */
 @PostMapping("/classes/bulk-close") public ClassService.BulkCloseResult bulkClose(@Valid @RequestBody BulkCloseRequest request) {
  return classes.closeClasses(request.classIds());
 }
 public record BulkCloseRequest(@NotEmpty @Size(max=200) List<@NotNull @Positive Long> classIds) {}
 @GetMapping("/classes/{id}/students") public java.util.List<ApiDtos.StudentInClass> registeredStudents(@PathVariable Long id) { return registrations.studentsInClass(id); }
 @GetMapping("/options") public AdminOptions options() {
  return new AdminOptions(courses.findAll().stream().map(c->new Option(c.getId(),c.getCode()+" - "+c.getName())).toList(),
   terms.findAll().stream().map(t->new Option(t.getId(),t.getName()+" "+t.getAcademicYear())).toList(),
   teachers.findAll().stream().map(t->new Option(t.getId(),t.getFullName()+" ("+t.getAccount().getEmail()+")")).toList());
 }
 public record Option(Long id, String label) {}
 public record AdminOptions(List<Option> courses, List<Option> terms, List<Option> teachers) {}
}
