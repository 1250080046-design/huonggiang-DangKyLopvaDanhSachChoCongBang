package vn.edu.registration.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import vn.edu.registration.service.CurriculumPolicy;
import java.time.LocalDateTime;
import java.util.List;

@RestController
public class StudyProgressController {
    private final StudyProgressRepository progress;
    private final RetakePermissionRepository retakes;
    private final StudentRepository students;
    private final CourseRepository courses;
    private final CurriculumPolicy policy;

    public StudyProgressController(StudyProgressRepository progress, RetakePermissionRepository retakes,
            StudentRepository students, CourseRepository courses, CurriculumPolicy policy) {
        this.progress=progress; this.retakes=retakes; this.students=students;
        this.courses=courses; this.policy=policy;
    }
    public record ProgressView(String status, Integer effectiveYear, int inferredYear,
            String note, List<String> retakeCourseCodes) {}
    public record UpdateProgress(@NotNull StudyProgress.Status status,
            @Min(1) @Max(4) Integer effectiveYear,
            @Size(max=500) String note, @NotNull List<@NotBlank String> retakeCourseCodes) {}

    private Student student(Long id) {
        return students.findById(id).orElseThrow(() ->
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Không tìm thấy sinh viên"));
    }
    private ProgressView view(Student student) {
        var record=progress.findById(student.getId()).orElse(null);
        return new ProgressView(record==null?"ON_TRACK":record.getStatus().name(),
            record==null?null:record.getEffectiveYear(), policy.cohortYear(student.getCohort()),
            record==null?null:record.getNote(), retakes.findByStudent_Id(student.getId())
                .stream().map(r->r.getCourse().getCode()).sorted().toList());
    }
    @GetMapping("/api/admin/students/{id}/study-progress")
    @Transactional(readOnly=true)
    public ProgressView adminView(@PathVariable Long id) { return view(student(id)); }

    @GetMapping("/api/student/study-progress")
    @Transactional(readOnly=true)
    public ProgressView myView(@AuthenticationPrincipal String email) {
        return view(students.findByAccount_EmailIgnoreCase(email).orElseThrow(() ->
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)));
    }
    @PutMapping("/api/admin/students/{id}/study-progress")
    @Transactional
    public ProgressView update(@PathVariable Long id, @Valid @RequestBody UpdateProgress request) {
        Student student=student(id);
        if ((request.status()==StudyProgress.Status.DELAYED || request.status()==StudyProgress.Status.RETURNED)
                && request.effectiveYear()==null)
            throw new IllegalArgumentException("Học chậm hoặc quay lại học phải chỉ rõ năm đào tạo thực tế (1–4).");
        StudyProgress record=progress.findById(id).orElseGet(() -> {
            var value=new StudyProgress(); value.setStudent(student); return value;
        });
        record.setStatus(request.status()); record.setEffectiveYear(request.effectiveYear());
        record.setNote(request.note()==null?null:request.note().trim());
        record.setUpdatedAt(LocalDateTime.now()); progress.save(record);
        // A complete replacement avoids stale permissions after review by Admin.
        var codes=request.retakeCourseCodes().stream().map(String::trim).map(String::toUpperCase).distinct().toList();
        var catalog=courses.findAll();
        var selected=codes.stream().map(code->catalog.stream().filter(c->c.getCode().equalsIgnoreCase(code))
                .findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy học phần " + code))).toList();
        retakes.deleteByStudent_Id(id);
        retakes.flush();
        for(Course course:selected) {
            RetakePermission permission=new RetakePermission();
            permission.setStudent(student);permission.setCourse(course);retakes.save(permission);
        }
        return view(student);
    }
}
