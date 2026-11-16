package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminMonitoringDecisionViewTest {
    @Test void decisionHistoryContainsReadableClassCourseAndStudentNames() {
        var waitlist = mock(WaitlistRepository.class);
        var logs = mock(DecisionLogRepository.class);
        var classes = mock(ClassSectionRepository.class);
        var students = mock(StudentRepository.class);
        var service = new AdminMonitoringService(waitlist, logs, classes, students);

        var course = new Course(); course.setId(4L); course.setCode("CO2017"); course.setName("Hệ điều hành");
        var section = new ClassSection(); section.setId(10L); section.setName("CO2017-HK2-01"); section.setCourse(course);
        var account = new Account(); account.setEmail("2240010001@sv.hcm.edu.vn");
        var student = new Student(); student.setId(20L); student.setFullName("Bùi Đức An"); student.setAccount(account);
        var log = new DecisionLog(); log.setId(30L); log.setClassId(10L); log.setStudentId(20L);
        log.setOccurredAt(LocalDateTime.of(2026, 11, 15, 10, 0)); log.setResult("PROMOTED_ROUND_ROBIN");
        when(logs.findTop100ByOrderByOccurredAtDesc()).thenReturn(List.of(log));
        when(classes.findAllById(List.of(10L))).thenReturn(List.of(section));
        when(students.findAllById(List.of(20L))).thenReturn(List.of(student));

        var view = service.recentDecisions().get(0);
        assertEquals("CO2017-HK2-01", view.className());
        assertEquals("CO2017", view.courseCode());
        assertEquals("Hệ điều hành", view.courseName());
        assertEquals("Bùi Đức An", view.studentName());
        assertEquals("2240010001@sv.hcm.edu.vn", view.studentEmail());
        assertEquals("PROMOTED_ROUND_ROBIN", view.result());
    }

    @Test void historicalRecordStillLoadsAfterClassOrStudentIsMissing() {
        var logs = mock(DecisionLogRepository.class);
        var classes = mock(ClassSectionRepository.class);
        var students = mock(StudentRepository.class);
        var service = new AdminMonitoringService(mock(WaitlistRepository.class), logs, classes, students);
        var log = new DecisionLog(); log.setId(1L); log.setClassId(11L); log.setStudentId(22L);
        log.setResult("REGISTERED");
        when(logs.findTop100ByOrderByOccurredAtDesc()).thenReturn(List.of(log));
        var result = service.recentDecisions().get(0);
        assertNull(result.className());
        assertNull(result.studentName());
        assertEquals(11L, result.classId());
        assertEquals(22L, result.studentId());
    }
}
