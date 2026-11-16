package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.registration.api.AuthController;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import vn.edu.registration.security.AuthCookieService;
import vn.edu.registration.security.JwtService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** V20.54: hồ sơ Sinh viên/Giảng viên chỉ đọc, Admin cập nhật bằng API quản trị riêng. */
class ProfileUpdateServiceTest {
    @Test void studentProfileCanBeReadWithoutChangingStoredData() {
        AccountRepository accounts = mock(AccountRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        Account account = new Account();
        account.setId(1L);
        account.setEmail("2240010001@sv.hcm.edu.vn");
        account.setRole(Role.STUDENT);
        account.setStatus(AccountStatus.ACTIVE);
        Student student = new Student();
        student.setAccount(account);
        student.setFullName("Bùi Đức An");
        student.setMajor("Kỹ thuật phần mềm");
        student.setDateOfBirth(LocalDate.of(2004, 11, 24));
        when(accounts.findByEmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(account));
        when(students.findByAccount_EmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(student));
        AuthService auth = new AuthService(accounts, students, teachers,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(JwtService.class));

        var view = auth.profile(account.getEmail());
        assertEquals("Bùi Đức An", view.fullName());
        assertEquals(LocalDate.of(2004, 11, 24), view.dateOfBirth());
        assertEquals("Kỹ thuật phần mềm", view.major());
        verify(students, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test void legacyProfileUpdateEndpointAlwaysRejectsWithForbidden() {
        AuthController controller = new AuthController(mock(AuthService.class),
                mock(ActivationService.class), mock(AuthCookieService.class));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                controller::updateProfileDisabled);
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }
}
