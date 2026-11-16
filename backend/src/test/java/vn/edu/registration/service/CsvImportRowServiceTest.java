package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.registration.domain.Account;
import vn.edu.registration.domain.Role;
import vn.edu.registration.domain.Student;
import vn.edu.registration.repository.AccountRepository;
import vn.edu.registration.repository.StudentRepository;
import vn.edu.registration.repository.TeacherRepository;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The CSV importer must not require a legacy ID mapping for a fresh database. */
class CsvImportRowServiceTest {
    private static String[] studentRow() {
        return new String[] {
                "2240010001", "ExampleStrongPassword123", "STUDENT", "Nguyen Van A",
                "2004-11-24", "2240010001@sv.hcm.edu.vn", "Cong nghe thong tin",
                "Ky thuat phan mem", "K22"
        };
    }

    @Test void importsTenDigitStudentWithoutLegacyMapping() {
        AccountRepository accounts = mock(AccountRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
        CsvImportRowService importer = new CsvImportRowService(accounts, students, teachers, passwords);

        assertTrue(importer.importRow(studentRow()));
        var accountCaptor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        assertEquals("2240010001", account.getUsername());
        assertEquals(Role.STUDENT, account.getRole());
        assertTrue(passwords.matches("ExampleStrongPassword123", account.getPasswordHash()));
        var studentCaptor = org.mockito.ArgumentCaptor.forClass(Student.class);
        verify(students).save(studentCaptor.capture());
        assertSame(account, studentCaptor.getValue().getAccount());
        assertEquals("K22", studentCaptor.getValue().getCohort());
        verifyNoInteractions(teachers);
    }

    @Test void existingStudentAccountIsNotOverwritten() {
        AccountRepository accounts = mock(AccountRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        Account existing = new Account();
        existing.setUsername("2240010001");
        existing.setRole(Role.STUDENT);
        existing.setPasswordHash("unchanged-hash");
        when(accounts.findByUsernameIgnoreCase("2240010001")).thenReturn(Optional.of(existing));
        CsvImportRowService importer = new CsvImportRowService(accounts, students, teachers,
                new BCryptPasswordEncoder(4));

        assertFalse(importer.importRow(studentRow()));
        assertEquals("unchanged-hash", existing.getPasswordHash());
        verify(accounts, never()).save(any());
        verifyNoInteractions(students, teachers);
    }

    @Test void duplicateEmailIsRejectedBeforeCreatingStudent() {
        AccountRepository accounts = mock(AccountRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        when(accounts.existsByEmailIgnoreCase("2240010001@sv.hcm.edu.vn")).thenReturn(true);
        CsvImportRowService importer = new CsvImportRowService(accounts, students, teachers,
                new BCryptPasswordEncoder(4));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> importer.importRow(studentRow()));
        assertEquals("Email đã tồn tại", e.getMessage());
        verify(accounts, never()).save(any());
        verifyNoInteractions(students, teachers);
    }
}
