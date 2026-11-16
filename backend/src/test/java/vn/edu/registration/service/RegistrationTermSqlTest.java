package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.Term;
import vn.edu.registration.repository.TermRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationTermSqlTest {
    @Test void noOpenTermBlocksRegistrationSafely() {
        TermRepository repository = mock(TermRepository.class);
        when(repository.findByRegistrationOpenTrue()).thenReturn(List.of());
        RegistrationTermPolicy policy = new RegistrationTermPolicy(repository, "Học kỳ 2", "2026-2027");
        Term term = new Term(); term.setId(10L); term.setName("Học kỳ 2"); term.setAcademicYear("2026-2027");
        assertFalse(policy.canRegister(term));
        assertEquals("Chưa có học kỳ mở đăng ký", policy.openPeriod());
        assertEquals(0, policy.openSemester());
    }

    @Test void openWindowIsUsedInsteadOfLegacyEnvironmentSetting() {
        TermRepository repository = mock(TermRepository.class);
        Term term = new Term(); term.setId(10L); term.setName("Học kỳ 1"); term.setAcademicYear("2027-2028");
        term.setRegistrationOpen(true);
        term.setRegistrationStartsAt(LocalDateTime.now().minusDays(1));
        term.setRegistrationEndsAt(LocalDateTime.now().plusDays(1));
        when(repository.findByRegistrationOpenTrue()).thenReturn(List.of(term));
        RegistrationTermPolicy policy = new RegistrationTermPolicy(repository, "Học kỳ 2", "2026-2027");
        assertTrue(policy.canRegister(term));
        assertEquals(1, policy.openSemester());
        assertEquals(2027, policy.openAcademicStartYear());
    }

    @Test void futureWindowRejectsRegistration() {
        TermRepository repository = mock(TermRepository.class);
        Term term = new Term(); term.setId(10L); term.setName("Học kỳ 2"); term.setAcademicYear("2026-2027");
        term.setRegistrationOpen(true); term.setRegistrationStartsAt(LocalDateTime.now().plusDays(1));
        when(repository.findByRegistrationOpenTrue()).thenReturn(List.of(term));
        assertFalse(new RegistrationTermPolicy(repository, "Học kỳ 2", "2026-2027").canRegister(term));
    }

    @Test void switchingOpenTermClosesPreviousTerm() {
        var repo = mock(TermRepository.class);
        var service = new AcademicCatalogService(mock(vn.edu.registration.repository.CourseRepository.class), repo,
                mock(vn.edu.registration.repository.ClassSectionRepository.class));
        Term old = new Term(); old.setId(1L); old.setRegistrationOpen(true);
        Term target = new Term(); target.setId(2L); target.setName("Học kỳ 1"); target.setAcademicYear("2027-2028");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        when(repo.findByRegistrationOpenTrue()).thenReturn(List.of(old));
        service.setRegistrationWindow(2L, new vn.edu.registration.api.ApiDtos.RegistrationWindowRequest(true, null, null));
        assertFalse(old.isRegistrationOpen()); assertTrue(target.isRegistrationOpen());
    }
}
