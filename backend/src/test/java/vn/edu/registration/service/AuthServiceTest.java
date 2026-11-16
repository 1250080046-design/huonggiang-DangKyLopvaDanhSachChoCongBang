package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.Account;
import vn.edu.registration.repository.AccountRepository;
import vn.edu.registration.repository.StudentRepository;
import vn.edu.registration.repository.TeacherRepository;
import vn.edu.registration.security.JwtService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test void changesPasswordWithoutChangingAccountIdentity() {
        AccountRepository accounts=mock(AccountRepository.class);
        BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
        Account account=new Account();
        account.setId(42L);
        account.setEmail("sv@example.edu.vn");
        account.setPasswordHash(encoder.encode("OldStrongPassword12"));
        account.setMustChangePassword(true);
        when(accounts.findByEmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(account));
        AuthService service=new AuthService(accounts,mock(StudentRepository.class),mock(TeacherRepository.class),
                encoder,mock(JwtService.class));

        service.changePassword(account.getEmail(),new ApiDtos.ChangePasswordRequest(
                "OldStrongPassword12","NewStrongPassword34"));

        assertEquals(42L,account.getId());
        assertTrue(encoder.matches("NewStrongPassword34",account.getPasswordHash()));
        assertFalse(account.isMustChangePassword());
        assertThrows(IllegalArgumentException.class,() -> service.changePassword(
                account.getEmail(),new ApiDtos.ChangePasswordRequest("wrong","AnotherLongPassword")));
    }
}
