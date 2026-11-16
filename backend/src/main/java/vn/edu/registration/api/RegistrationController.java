package vn.edu.registration.api;
import org.springframework.web.bind.annotation.*; import vn.edu.registration.service.RegistrationService; import java.util.List;
@RestController
public class RegistrationController {
 private final RegistrationService service; public RegistrationController(RegistrationService service) { this.service=service; }
 @PostMapping("/api/classes/{classId}/register") public ApiDtos.ActionResult register(@PathVariable Long classId) { return service.register(classId); }
 @GetMapping("/api/me/registrations") public List<ApiDtos.RegistrationView> mine() { return service.myRegistrations(); }
 @GetMapping("/api/me/schedule") public ApiDtos.StudySchedule mySchedule() { return service.mySchedule(); }
 @DeleteMapping("/api/registrations/{id}") public ApiDtos.ActionResult cancel(@PathVariable Long id) { return service.cancelRegistration(id); }
 @GetMapping("/api/me/waitlist") public List<ApiDtos.WaitlistView> myWaitlist() { return service.myWaitlist(); }
 @PostMapping("/api/waitlist/{id}/rejoin") public ApiDtos.ActionResult rejoin(@PathVariable Long id) { return service.rejoinWaitlist(id); }
 @DeleteMapping("/api/waitlist/{id}") public ApiDtos.ActionResult withdraw(@PathVariable Long id) { return service.withdrawWaitlist(id); }
}
