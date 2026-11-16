package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import vn.edu.registration.security.JwtService;
import java.time.LocalDateTime;

@Service
public class AuthService {
    private final AccountRepository accounts; private final StudentRepository students; private final TeacherRepository teachers;
    private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthService(AccountRepository accounts, StudentRepository students, TeacherRepository teachers, PasswordEncoder encoder, JwtService jwt) {
        this.accounts=accounts; this.students=students; this.teachers=teachers; this.encoder=encoder; this.jwt=jwt;
    }
    @Transactional
    public ApiDtos.LoginResponse login(ApiDtos.LoginRequest req) {
        Account account=accounts.findByUsernameIgnoreCase(req.email().trim()).or(() -> accounts.findByEmailIgnoreCase(req.email().trim())).orElseThrow(()->new IllegalArgumentException("Email hoặc mật khẩu không đúng."));
        if (account.getStatus()!=AccountStatus.ACTIVE || !account.isActivated()) throw new IllegalArgumentException("Tài khoản chưa kích hoạt hoặc đã bị khóa.");
        if (account.getPasswordHash()==null || !encoder.matches(req.password(), account.getPasswordHash())) throw new IllegalArgumentException("Email hoặc mật khẩu không đúng.");
        account.setLastLoginAt(LocalDateTime.now());
        return new ApiDtos.LoginResponse(jwt.generate(account.getEmail(), account.getRole().name()), account.getEmail(), account.getRole(), displayName(account), account.isMustChangePassword());
    }
    @Transactional(readOnly=true)
    public ApiDtos.LoginResponse me(String email) {
        Account account=accounts.findByEmailIgnoreCase(email).orElseThrow(()->new EntityNotFoundException("Không tìm thấy tài khoản."));
        return new ApiDtos.LoginResponse(null, account.getEmail(), account.getRole(), displayName(account), account.isMustChangePassword());
    }
    @Transactional(readOnly=true)
    public ApiDtos.ProfileView profile(String email) {
        Account account = accounts.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài khoản."));
        if (account.getRole() == Role.STUDENT) {
            Student student = students.findByAccount_EmailIgnoreCase(email)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ sinh viên."));
            return new ApiDtos.ProfileView(account.getId(), account.getUsername(), account.getEmail(), student.getFullName(), account.getRole(),
                    student.getFaculty(), student.getMajor(), student.getCohort(), account.getStatus().name(), null, student.getDateOfBirth());
        }
        if (account.getRole() == Role.TEACHER) {
            Teacher teacher = teachers.findByAccount_EmailIgnoreCase(email)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ giảng viên."));
            return new ApiDtos.ProfileView(account.getId(), account.getUsername(), account.getEmail(), teacher.getFullName(), account.getRole(),
                    teacher.getFaculty(), teacher.getMajor(), null, account.getStatus().name(), null, teacher.getDateOfBirth());
        }
        return new ApiDtos.ProfileView(account.getId(), account.getUsername(), account.getEmail(), "Quản trị viên", account.getRole(),
                null, null, null, account.getStatus().name(), null, null);
    }
    @Transactional
    public void changePassword(String email, ApiDtos.ChangePasswordRequest req) {
        Account account=accounts.findByEmailIgnoreCase(email)
                .orElseThrow(()->new EntityNotFoundException("Không tìm thấy tài khoản."));
        if (!encoder.matches(req.currentPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        if (encoder.matches(req.newPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        account.setPasswordHash(encoder.encode(req.newPassword()));
        account.setMustChangePassword(false);
    }
    private String displayName(Account a) {
        if (a.getRole()==Role.STUDENT) return students.findByAccount_EmailIgnoreCase(a.getEmail()).map(Student::getFullName).orElse(a.getEmail());
        if (a.getRole()==Role.TEACHER) return teachers.findByAccount_EmailIgnoreCase(a.getEmail()).map(Teacher::getFullName).orElse(a.getEmail());
        return "Quản trị viên";
    }
}
