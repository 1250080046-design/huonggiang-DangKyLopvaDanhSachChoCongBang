package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyProgressPolicyTest {
    private final StudyProgressRepository progress=mock(StudyProgressRepository.class);
    private final RetakePermissionRepository retakes=mock(RetakePermissionRepository.class);
    private final CurriculumPolicy policy=new CurriculumPolicy(new RegistrationTermPolicy("Học kỳ 2","2026-2027"),progress,retakes);
    private Student student() {
        var s=new Student();s.setId(102L);s.setCohort("K22");s.setMajor("Kỹ thuật phần mềm");return s;
    }
    private Course course(int year,int semester) {
        var c=new Course();c.setId(300L);c.setYear(year);c.setSemester(semester);c.setCode("CO2017");return c;
    }
    @Test void k22YearTwoApprovedCanRegisterWithoutChangingCohort() {
        var s=student();var p=new StudyProgress();p.setStatus(StudyProgress.Status.DELAYED);p.setEffectiveYear(2);
        when(progress.findById(102L)).thenReturn(Optional.of(p));
        assertTrue(policy.canRegister(s,course(2,2)));
        assertEquals("K22",s.getCohort());
        assertFalse(policy.canRegister(s,course(4,2)));
    }
    @Test void suspendedStudentsCannotRegisterEvenWithRetakeApproval() {
        var s=student();var p=new StudyProgress();p.setStatus(StudyProgress.Status.SUSPENDED);p.setEffectiveYear(2);
        when(progress.findById(102L)).thenReturn(Optional.of(p));
        when(retakes.existsByStudent_IdAndCourse_Id(102L,300L)).thenReturn(true);
        assertFalse(policy.canRegister(s,course(2,2)));
        assertTrue(policy.rejectionReason(s,course(2,2)).contains("bảo lưu"));
    }
    @Test void approvedRetakeStillHonorsMajorAndOpenSemester() {
        var s=student();var p=new StudyProgress();p.setStatus(StudyProgress.Status.RETURNED);p.setEffectiveYear(3);
        when(progress.findById(102L)).thenReturn(Optional.of(p));
        when(retakes.existsByStudent_IdAndCourse_Id(102L,300L)).thenReturn(true);
        assertTrue(policy.canRegister(s,course(2,2)));
        assertFalse(policy.canRegister(s,course(2,1)));
        var unrelated=course(2,2);unrelated.setMajor("Ngành khác");
        assertFalse(policy.canRegister(s,unrelated));
    }
}
