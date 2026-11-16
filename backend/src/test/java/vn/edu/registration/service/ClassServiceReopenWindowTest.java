package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.ClassSection;
import vn.edu.registration.domain.ClassStatus;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Teacher;
import vn.edu.registration.domain.Term;
import vn.edu.registration.repository.ClassSectionRepository;
import vn.edu.registration.repository.CourseRepository;
import vn.edu.registration.repository.TeacherRepository;
import vn.edu.registration.repository.TermRepository;
import vn.edu.registration.repository.RegistrationRepository;
import vn.edu.registration.repository.WaitlistRepository;
import vn.edu.registration.repository.DecisionLogRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassServiceReopenWindowTest {
    private final ClassSectionRepository classes = mock(ClassSectionRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final RegistrationService registrations = mock(RegistrationService.class);
    private final RegistrationTermPolicy termPolicy = mock(RegistrationTermPolicy.class);
    private final ClassService service = new ClassService(
            classes, mock(CourseRepository.class), mock(TermRepository.class), teachers,
            registrations, termPolicy, mock(CurriculumPolicy.class), mock(CurrentAccountService.class),
            mock(RegistrationRepository.class), mock(WaitlistRepository.class), mock(DecisionLogRepository.class));

    private ClassSection section(ClassStatus status) {
        Term term = new Term(); term.setId(7L); term.setName("Học kỳ 2"); term.setAcademicYear("2026-2027");
        Course course = new Course(); course.setId(11L); course.setCode("POL203"); course.setName("Chủ nghĩa xã hội khoa học");
        Teacher teacher = new Teacher(); teacher.setId(19L); teacher.setFullName("Giảng viên A");
        ClassSection section = new ClassSection(); section.setId(42L); section.setTerm(term); section.setCourse(course);
        section.setTeacher(teacher); section.setName("POL203-HK2-01"); section.setRoom("A205");
        section.setSchedule("Thứ 3 13:00-15:00"); section.setCapacity(40); section.setEnrolled(0); section.setStatus(status);
        return section;
    }

    private void arrangeUpdate(ClassSection section, boolean registrationWindowOpen) {
        when(classes.findByIdForUpdate(section.getId())).thenReturn(Optional.of(section));
        when(registrations.activeCount(section.getId())).thenReturn(0L);
        when(teachers.findById(section.getTeacher().getId())).thenReturn(Optional.of(section.getTeacher()));
        when(classes.findByTeacher_Id(section.getTeacher().getId())).thenReturn(List.of());
        when(termPolicy.canRegister(section.getTerm())).thenReturn(registrationWindowOpen);
    }

    @Test
    void adminCanReopenClosedClassAfterRegistrationWindowEndedWithoutPromotingStudents() {
        ClassSection section = section(ClassStatus.CLOSED);
        arrangeUpdate(section, false);
        ApiDtos.UpdateClassRequest request = new ApiDtos.UpdateClassRequest(section.getName(), section.getTeacher().getId(),
                section.getRoom(), section.getSchedule(), section.getCapacity(), ClassStatus.OPEN, section.getCourse().getId());

        ApiDtos.ClassView updated = service.updateDetails(section.getId(), request);

        assertEquals(ClassStatus.OPEN, updated.status());
        assertEquals(ClassStatus.OPEN, section.getStatus());
        verify(termPolicy).canRegister(section.getTerm());
        verify(registrations, never()).promoteWaiting(any(ClassSection.class), anyString());
    }

    @Test
    void reopeningDuringAnActiveWindowStillAllowsExistingRoundRobinPromotion() {
        ClassSection section = section(ClassStatus.CLOSED);
        arrangeUpdate(section, true);
        ApiDtos.UpdateClassRequest request = new ApiDtos.UpdateClassRequest(section.getName(), section.getTeacher().getId(),
                section.getRoom(), section.getSchedule(), section.getCapacity(), ClassStatus.OPEN, section.getCourse().getId());

        service.updateDetails(section.getId(), request);

        assertEquals(ClassStatus.OPEN, section.getStatus());
        verify(registrations).promoteWaiting(eq(section), eq("Admin sửa lớp hoặc mở lại lớp"));
    }

    @Test
    void dedicatedStatusEndpointAlsoAllowsReopenWithoutRegistrationWindow() {
        ClassSection section = section(ClassStatus.CLOSED);
        when(classes.findByIdForUpdate(42L)).thenReturn(Optional.of(section));
        when(termPolicy.canRegister(section.getTerm())).thenReturn(false);

        service.changeStatus(42L, ClassStatus.OPEN);

        assertEquals(ClassStatus.OPEN, section.getStatus());
        verify(registrations, never()).promoteWaiting(any(ClassSection.class), anyString());
    }

    @Test
    void cannotCancelClassWithActiveStudents() {
        ClassSection section = section(ClassStatus.OPEN);
        when(classes.findByIdForUpdate(42L)).thenReturn(Optional.of(section));
        when(registrations.activeCount(42L)).thenReturn(1L);
        ApiDtos.UpdateClassRequest request = new ApiDtos.UpdateClassRequest(section.getName(), section.getTeacher().getId(),
                section.getRoom(), section.getSchedule(), section.getCapacity(), ClassStatus.CANCELLED, section.getCourse().getId());

        assertThrows(IllegalArgumentException.class, () -> service.updateDetails(42L, request));
        assertEquals(ClassStatus.OPEN, section.getStatus());
        verify(registrations, never()).promoteWaiting(any(ClassSection.class), anyString());
    }
}
