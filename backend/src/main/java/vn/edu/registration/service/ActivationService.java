package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j @Service
public class ActivationService {
    private final AccountRepository accounts; private final StudentRepository students; private final TeacherRepository teachers;
    private final ActivationTokenRepository tokens; private final PasswordEncoder encoder; private final JavaMailSender mailSender;
    private final boolean mailEnabled; private final String frontendUrl; private final String from;
    public ActivationService(AccountRepository accounts, StudentRepository students, TeacherRepository teachers,
            ActivationTokenRepository tokens, PasswordEncoder encoder, JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${MAIL_FROM:no-reply@example.local}") String from) {
        this.accounts=accounts; this.students=students; this.teachers=teachers; this.tokens=tokens; this.encoder=encoder;
        this.mailSender=mailSender; this.mailEnabled=mailEnabled; this.frontendUrl=frontendUrl; this.from=from;
    }
    @Transactional
    public ApiDtos.AccountCreated createAccount(ApiDtos.CreateAccountRequest request) {
        if (request.role() == Role.ADMIN) throw new IllegalArgumentException("Không tạo tài khoản quản trị viên qua API này.");
        if (accounts.existsByEmailIgnoreCase(request.email())) throw new IllegalArgumentException("Email đã tồn tại trong hệ thống.");
        Account account = new Account(); account.setEmail(request.email().trim().toLowerCase()); account.setRole(request.role());
        account.setStatus(AccountStatus.PENDING); account.setActivated(false); account.setMustChangePassword(true);
        account = accounts.save(account);
        if (request.role() == Role.STUDENT) {
            Student student = new Student(); student.setAccount(account); student.setFullName(request.fullName().trim());
            student.setFaculty(request.faculty()); student.setMajor(request.major()); students.save(student);
        } else {
            Teacher teacher = new Teacher(); teacher.setAccount(account); teacher.setFullName(request.fullName().trim());
            teacher.setFaculty(request.faculty()); teacher.setMajor(request.major()); teachers.save(teacher);
        }
        String rawToken = newToken(); ActivationToken token = new ActivationToken(); token.setAccount(account);
        token.setTokenHash(hash(rawToken)); token.setExpiresAt(LocalDateTime.now().plusHours(24)); tokens.save(token);
        String link = frontendUrl + "/activate?token=" + rawToken;
        if (mailEnabled) {
            SimpleMailMessage message = new SimpleMailMessage(); message.setFrom(from); message.setTo(account.getEmail());
            message.setSubject("Kích hoạt tài khoản hệ thống đăng ký lớp học");
            message.setText("Chào " + request.fullName() + ",\n\nVui lòng mở liên kết sau để kích hoạt và tạo mật khẩu mới (có hiệu lực 24 giờ):\n" + link + "\n\nNếu bạn không yêu cầu tài khoản này, hãy bỏ qua email.");
            mailSender.send(message);
        } else {
            log.warn("EMAIL CHUA BAT - lien ket kich hoat tai khoan {}: {}", account.getEmail(), link);
        }
        return new ApiDtos.AccountCreated(mailEnabled ? "Đã tạo tài khoản và gửi email kích hoạt." : "Đã tạo tài khoản. Chế độ email chưa bật; xem liên kết kích hoạt trong log backend.", account.getEmail(), account.getRole());
    }
    @Transactional
    public void activate(ApiDtos.ActivateRequest request) {
        if (request.newPassword() == null || request.newPassword().length() < 8) throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 8 ký tự.");
        ActivationToken token = tokens.findByTokenHashAndUsedAtIsNull(hash(request.token()))
                .orElseThrow(()->new IllegalArgumentException("Liên kết kích hoạt không hợp lệ hoặc đã được sử dụng."));
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("Liên kết kích hoạt đã hết hạn. Vui lòng nhờ quản trị viên tạo lại.");
        Account account=token.getAccount(); account.setPasswordHash(encoder.encode(request.newPassword())); account.setActivated(true);
        account.setStatus(AccountStatus.ACTIVE); account.setMustChangePassword(false); account.setActivatedAt(LocalDateTime.now());
        token.setUsedAt(LocalDateTime.now());
    }
    private String newToken() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Không thể tạo mã kích hoạt.", e); }
    }
}
