package vn.edu.registration.service;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos; import vn.edu.registration.domain.*; import vn.edu.registration.repository.*;
@Service
public class AdminService {
 private final AccountRepository accounts; private final ClassSectionRepository classes; private final RegistrationRepository registrations; private final WaitlistRepository waitlist;
 public AdminService(AccountRepository a, ClassSectionRepository c, RegistrationRepository r, WaitlistRepository w) { accounts=a; classes=c; registrations=r; waitlist=w; }
 @Transactional(readOnly=true) public ApiDtos.AdminSummary summary() {
  return new ApiDtos.AdminSummary(accounts.count(), classes.count(), registrations.countByStatus(RegistrationStatus.ACTIVE), waitlist.countByStatus(WaitlistStatus.WAITING));
 }
}
