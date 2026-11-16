package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PeopleImportRowServiceTest {
    private final ActivationService activation = mock(ActivationService.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final PeopleImportRowService service = new PeopleImportRowService(activation, accounts, students, teachers);

    @Test void createsPendingStudentThroughExistingActivationFlowAndPersistsCohort() {
        String email = "newstudent@example.edu";
        Account account = new Account(); account.setEmail(email); account.setRole(Role.STUDENT);
        Student student = new Student(); student.setAccount(account);
        when(accounts.findByEmailIgnoreCase(email)).thenReturn(Optional.empty(), Optional.of(account));
        when(accounts.findByUsernameIgnoreCase("2240011234")).thenReturn(Optional.empty());
        when(students.findByAccount_EmailIgnoreCase(email)).thenReturn(Optional.of(student));
        var data = new PeopleImportRowService.ImportPerson("2240011234", email, "Nguyễn Văn A", "Công nghệ thông tin",
                "Kỹ thuật phần mềm", "K22", "2004-01-01", "STUDENT");

        assertTrue(service.importRow(data, Role.STUDENT));
        verify(activation).createAccount(any(ApiDtos.CreateAccountRequest.class));
        assertEquals("2240011234", account.getUsername());
        assertEquals("K22", student.getCohort());
        assertEquals(LocalDate.of(2004, 1, 1), student.getDateOfBirth());
    }

    @Test void existingStudentIsSkippedWithoutAnyChanges() {
        Account account = new Account(); account.setRole(Role.STUDENT); account.setUsername("2240011234");
        when(accounts.findByEmailIgnoreCase("existing@example.edu")).thenReturn(Optional.of(account));
        var data = new PeopleImportRowService.ImportPerson("2240011234", "existing@example.edu", "Tên thay đổi", "", "", "K22", "", "STUDENT");
        assertFalse(service.importRow(data, Role.STUDENT));
        verifyNoInteractions(activation, students, teachers);
    }

    @Test void existingEmailWithOtherRoleIsRejected() {
        Account account = new Account(); account.setRole(Role.ADMIN);
        when(accounts.findByEmailIgnoreCase("admin@example.edu")).thenReturn(Optional.of(account));
        var data = new PeopleImportRowService.ImportPerson("", "admin@example.edu", "Giảng viên", "", "", "", "", "TEACHER");
        assertThrows(IllegalArgumentException.class, () -> service.importRow(data, Role.TEACHER));
        verifyNoInteractions(activation);
    }
}
