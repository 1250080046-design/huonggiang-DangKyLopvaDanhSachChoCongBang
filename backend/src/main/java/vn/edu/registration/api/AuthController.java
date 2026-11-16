package vn.edu.registration.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.edu.registration.security.AuthCookieService;
import vn.edu.registration.service.*;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth; private final ActivationService activation; private final AuthCookieService cookies;
 public AuthController(AuthService auth, ActivationService activation, AuthCookieService cookies) { this.auth=auth; this.activation=activation; this.cookies=cookies; }
 @GetMapping("/csrf") public Map<String,String> csrf(@RequestAttribute("_csrf") CsrfToken csrf) {
     return Map.of("token", csrf.getToken());
 }
 @PostMapping("/login") public ApiDtos.LoginResponse login(@Valid @RequestBody ApiDtos.LoginRequest req, HttpServletResponse response) {
     ApiDtos.LoginResponse result = auth.login(req);
     cookies.login(response, result.token());
     return new ApiDtos.LoginResponse(null, result.email(), result.role(), result.displayName(), result.mustChangePassword());
 }
 @PostMapping("/logout") public Map<String,String> logout(HttpServletResponse response) {
     cookies.logout(response);
     return Map.of("message", "Đã đăng xuất.");
 }
 @PostMapping("/activate") public Map<String,String> activate(@Valid @RequestBody ApiDtos.ActivateRequest req) { activation.activate(req); return Map.of("message","Kích hoạt tài khoản thành công. Bạn có thể đăng nhập."); }
 @PostMapping("/change-password") public Map<String,String> changePassword(
      @AuthenticationPrincipal String email, @Valid @RequestBody ApiDtos.ChangePasswordRequest req) {
     auth.changePassword(email, req);
     return Map.of("message", "Đã đổi mật khẩu thành công.");
 }
 // Anonymous callers can check their session without provoking a 403 console error.
 @GetMapping("/session") public Map<String, Boolean> session(Authentication authentication) {
     return Map.of("authenticated", authentication != null
             && authentication.isAuthenticated()
             && !(authentication instanceof AnonymousAuthenticationToken));
 }
 @GetMapping("/me") public ApiDtos.LoginResponse me(@AuthenticationPrincipal String email) { return auth.me(email); }
 @GetMapping("/profile") public ApiDtos.ProfileView profile(@AuthenticationPrincipal String email) { return auth.profile(email); }
 // Giữ endpoint để client cũ nhận lỗi 403 rõ ràng thay vì có thể tự cập nhật hồ sơ.
 @PutMapping("/profile") public void updateProfileDisabled() {
     throw new ResponseStatusException(HttpStatus.FORBIDDEN,
             "Hồ sơ chỉ được xem. Vui lòng liên hệ Quản trị viên để thay đổi thông tin cá nhân.");
 }
}
