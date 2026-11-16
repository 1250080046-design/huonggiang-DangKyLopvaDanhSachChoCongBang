package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Student;

import static org.junit.jupiter.api.Assertions.*;

class CurriculumPolicyTest {
    private final CurriculumPolicy policy = new CurriculumPolicy(new RegistrationTermPolicy("Học kỳ 2", "2026-2027"));

    private Student student(String cohort, String major) {
        Student student = new Student();
        student.setCohort(cohort);
        student.setMajor(major);
        return student;
    }

    private Course course(String code, Integer year, Integer semester, String major) {
        Course course = new Course();
        course.setCode(code);
        course.setYear(year);
        course.setSemester(semester);
        course.setMajor(major);
        return course;
    }

    @Test void yearOneCanRegisterYearOneSecondSemester() {
        assertTrue(policy.canRegister(student("K26", "Công nghệ thông tin"), course("NEW101", 1, 2, "Công nghệ thông tin")));
        assertFalse(policy.canRegister(student("K26", "Công nghệ thông tin"), course("NEW201", 2, 2, "Công nghệ thông tin")));
        assertFalse(policy.canRegister(student("K26", "Công nghệ thông tin"), course("NEW102", 1, 1, null)));
    }

    @Test void yearTwoOnlyRegistersCurrentYearInOpenSemester() {
        assertFalse(policy.canRegister(student("K25", "Công nghệ thông tin"), course("RETAKE", 1, 2, null)));
        assertTrue(policy.canRegister(student("K25", "Công nghệ thông tin"), course("YEAR2", 2, 2, null)));
        assertFalse(policy.canRegister(student("K25", "Công nghệ thông tin"), course("YEAR3", 3, 2, null)));
    }

    @Test void majorIsCheckedAndSharedCoursesAreAllowed() {
        Student student = student("K26", "Công nghệ thông tin");
        assertFalse(policy.canRegister(student, course("MKT101", 1, 2, "Marketing")));
        assertTrue(policy.canRegister(student, course("MTH101", 1, 2, null)));
        assertTrue(policy.canRegister(student, course("MTH102", 1, 2, "  CÔNG NGHỆ   THÔNG TIN  ")));
    }

    @Test void studentWithoutMajorCannotSeeEvenSharedSubjects() {
        assertFalse(policy.canRegister(student("K26", null), course("MTH101", 1, 2, null)));
    }

    @Test void noMetadataFailsClosedWithoutCsvFallback() {
        assertFalse(policy.canRegister(student("K24", "Công nghệ thông tin"), course("MTH102", null, null, null)));
        assertTrue(policy.rejectionReason(student("K24", null), course("MTH102", null, null, null)).contains("SQL Server"));
    }

    @Test void cohortIsCalculatedFromConfiguredAcademicYear() {
        assertEquals(1, policy.cohortYear("K26"));
        assertEquals(1, policy.cohortYear("K2026"));
        assertEquals(2, policy.cohortYear("K25"));
        assertEquals(0, policy.cohortYear("K22")); // 2026-2027 là năm thứ 5, không được ép về năm 4.
        assertEquals(4, policy.cohortYear("K23"));
        assertEquals(0, policy.cohortYear("K27"));
        assertEquals(0, policy.cohortYear("KXXX"));
        CurriculumPolicy nextYear = new CurriculumPolicy(new RegistrationTermPolicy("Học kỳ 2", "2027-2028"));
        assertEquals(2, nextYear.cohortYear("K26"));
        assertEquals(1, nextYear.cohortYear("K27"));
    }

    @Test void expiredCohortCannotAccessFourthYearCourses() {
        assertFalse(policy.canRegister(student("K22", "Kỹ thuật phần mềm"), course("THESIS402", 4, 2, null)));
        assertTrue(policy.canRegister(student("K25", "Kỹ thuật phần mềm"), course("CO2017", 2, 2, null)));
        assertTrue(policy.rejectionReason(student("K22", "Kỹ thuật phần mềm"), course("THESIS402", 4, 2, null))
                .contains("tiến độ học tập"));
    }

    @Test void dynamicallyUsesAdminUpdatedCourseMetadata() {
        Student student = student("K26", "Công nghệ thông tin");
        Course course = course("NEW101", 3, 2, null);
        assertFalse(policy.canRegister(student, course));
        course.setYear(1); // Giống thay đổi của Admin được tải lại từ SQL.
        assertTrue(policy.canRegister(student, course));
    }
}
