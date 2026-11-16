package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.Term;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationTermPolicyTest {
    @Test void onlySecondTermInConfiguredYearIsOpen() {
        RegistrationTermPolicy policy = new RegistrationTermPolicy("Học kỳ 2", "2026-2027");
        Term first = new Term(); first.setName("Học kỳ 1"); first.setAcademicYear("2026-2027");
        Term second = new Term(); second.setName("Học kỳ 2"); second.setAcademicYear("2026-2027");
        Term future = new Term(); future.setName("Học kỳ 1"); future.setAcademicYear("2027-2028");
        assertFalse(policy.canRegister(first));
        assertTrue(policy.canRegister(second));
        assertFalse(policy.canRegister(future));
    }
}
