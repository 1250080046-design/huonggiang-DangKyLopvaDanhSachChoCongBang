package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleConflictPolicyTest {
    @Test void detectsOverlappingClasses() {
        assertTrue(ScheduleConflictPolicy.conflicts("Thứ 2 07:30-09:30", "Thứ 2 08:00-10:00"));
        assertTrue(ScheduleConflictPolicy.conflicts("Thứ 2 07:30-09:30; Thứ 4 12:00-14:00", "Thứ 4 13:00-15:00"));
    }
    @Test void permitsAdjacentOrDifferentDay() {
        assertFalse(ScheduleConflictPolicy.conflicts("Thứ 2 07:30-09:30", "Thứ 2 09:30-11:30"));
        assertFalse(ScheduleConflictPolicy.conflicts("Thứ 2 07:30-09:30", "Thứ 3 08:00-09:00"));
        assertFalse(ScheduleConflictPolicy.conflicts(null, "Thứ 3 08:00-09:00"));
    }
    @Test void acceptsSingleDigitHourWithoutReturningServerError() {
        var slots = ScheduleConflictPolicy.parse("thứ 3 7:00-9:30");
        assertEquals(1, slots.size());
        assertEquals(java.time.LocalTime.of(7, 0), slots.get(0).start());
        assertEquals(java.time.LocalTime.of(9, 30), slots.get(0).end());
        assertTrue(ScheduleConflictPolicy.conflicts("thứ 3 7:00-9:30", "Thứ 3 08:30-10:00"));
    }
    @Test void invalidHourReturnsClearValidationError() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleConflictPolicy.parse("Thứ 3 25:00-26:30"));
    }
    @Test void rejectsInvalidScheduleInsteadOfSilentlySkippingIt() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleConflictPolicy.parse("sáng thứ hai"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleConflictPolicy.parse("Thứ 2 10:00-08:00"));
    }
}
