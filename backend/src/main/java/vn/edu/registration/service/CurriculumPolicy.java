package vn.edu.registration.service;

import org.springframework.stereotype.Component;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Student;

import java.util.Locale;
import java.util.Optional;
import vn.edu.registration.repository.StudyProgressRepository;
import vn.edu.registration.repository.RetakePermissionRepository;

/**
 * Quy tắc đăng ký học phần đọc trực tiếp metadata của entity HocPhan từ SQL Server.
 * Không đọc CSV, không hard-code khóa K22..K26. Không có metadata thì từ chối an toàn.
 */
@Component
public class CurriculumPolicy {
    public record Assignment(int year, int semester) {}

    private final RegistrationTermPolicy registrationTerm;
    private final StudyProgressRepository progress;
    private final RetakePermissionRepository retakes;

    @org.springframework.beans.factory.annotation.Autowired
    public CurriculumPolicy(RegistrationTermPolicy registrationTerm,
                            StudyProgressRepository progress, RetakePermissionRepository retakes) {
        this.registrationTerm = registrationTerm;
        this.progress = progress;
        this.retakes = retakes;
    }

    /** Actual study year is approved by Admin; admission cohort is never rewritten. */
    public int effectiveYear(Student student) {
        if (student == null) return 0;
        var approved = progress == null ? java.util.Optional.<vn.edu.registration.domain.StudyProgress>empty() : progress.findById(student.getId());
        if (approved.isPresent() && approved.get().getEffectiveYear() != null)
            return approved.get().getEffectiveYear();
        return cohortYear(student.getCohort());
    }

    public boolean suspended(Student student) {
        return student != null && progress != null && progress.findById(student.getId())
            .map(p -> p.getStatus() == vn.edu.registration.domain.StudyProgress.Status.SUSPENDED)
            .orElse(false);
    }

    public boolean hasRetakePermission(Student student, Course course) {
        return student != null && course != null && retakes != null &&
            retakes.existsByStudent_IdAndCourse_Id(student.getId(), course.getId());
    }

    /** Compatibility constructor for isolated policy tests. Production uses injected repositories. */
    public CurriculumPolicy(RegistrationTermPolicy registrationTerm) {
        this.registrationTerm = registrationTerm;
        this.progress = null;
        this.retakes = null;
    }

    /** Không cache: sửa trong Admin sẽ tác động ngay lần truy vấn tiếp theo. */
    public Optional<Assignment> assignment(Course course) {
        if (course == null || course.getYear() == null || course.getSemester() == null) return Optional.empty();
        int year = course.getYear(), semester = course.getSemester();
        if (year < 1 || year > 4 || (semester != 1 && semester != 2)) return Optional.empty();
        return Optional.of(new Assignment(year, semester));
    }

    /** K26 hoặc K2026 đều tương ứng khóa nhập học 2026. */
    public int cohortYear(String cohort) {
        if (cohort == null) return 0;
        String value = cohort.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("K(?:\\d{2}|\\d{4})")) return 0;
        int start = Integer.parseInt(value.substring(1));
        if (start < 100) start += 2000;
        int year = registrationTerm.openAcademicStartYear() - start + 1;
        if (year < 1 || year > 4) return 0; // Không ép khóa đã học quá năm 4 về năm 4.
        return year;
    }

    private static String normalizedMajor(String major) {
        return major == null ? "" : major.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public boolean canRegister(Student student, Course course) {
        if (student == null || course == null || suspended(student)) return false;
        var plan = assignment(course);
        if (plan.isEmpty() || plan.get().semester() != registrationTerm.openSemester()) return false;
        // Chỉ mở môn của đúng năm đào tạo theo khóa nhập học, không trộn môn năm trước.
        if (effectiveYear(student) != plan.get().year() && !hasRetakePermission(student,course)) return false;
        String studentMajor = normalizedMajor(student.getMajor());
        if (studentMajor.isEmpty()) return false; // Hồ sơ chưa có ngành: không mở cả môn dùng chung.
        String courseMajor = normalizedMajor(course.getMajor());
        return courseMajor.isEmpty() || courseMajor.equals(studentMajor);
    }

    public String rejectionReason(Student student, Course course) {
        var plan = assignment(course);
        if (plan.isEmpty())
            return "Học phần chưa được Admin phân năm đào tạo và học kỳ trong SQL Server.";
        if (plan.get().semester() != registrationTerm.openSemester())
            return "Học phần thuộc học kỳ " + plan.get().semester() + ", hiện chỉ mở " + registrationTerm.openPeriod() + ".";
        if (student != null && suspended(student))
            return "Sinh viên đang bảo lưu, chưa được đăng ký học phần.";
        if (student == null || (effectiveYear(student) == 0 && !hasRetakePermission(student,course)))
            return "Chưa có năm đào tạo thực tế hợp lệ. Vui lòng nhờ Admin cập nhật tiến độ học tập.";
        if (normalizedMajor(student.getMajor()).isEmpty())
            return "Hồ sơ sinh viên chưa có ngành đào tạo. Vui lòng liên hệ quản trị viên cập nhật.";
        if (!normalizedMajor(course.getMajor()).isEmpty()
                && !normalizedMajor(course.getMajor()).equals(normalizedMajor(student.getMajor())))
            return "Học phần không thuộc ngành đào tạo của sinh viên.";
        return "Học phần thuộc năm " + plan.get().year() + "; tiến độ học tập hiện tại là năm " + effectiveYear(student) + ". Nếu cần học lại, hãy liên hệ Admin để được cấp quyền theo học phần.";
    }
}
