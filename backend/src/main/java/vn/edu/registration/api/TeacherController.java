package vn.edu.registration.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.registration.service.*;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final ClassService classes;
    private final RegistrationService registrations;

    public TeacherController(ClassService c, RegistrationService r) {
        classes = c;
        registrations = r;
    }

    @GetMapping("/classes")
    public List<ApiDtos.ClassView> classes(@AuthenticationPrincipal String email) {
        return classes.teacherClasses(email);
    }

    @GetMapping("/classes/{id}/students")
    public List<ApiDtos.StudentInClass> students(@PathVariable Long id, @AuthenticationPrincipal String email) {
        boolean assigned = classes.teacherClasses(email).stream().anyMatch(c -> c.id().equals(id));
        if (!assigned)
            throw new IllegalArgumentException("Bạn không được phân công giảng dạy lớp này.");
        return registrations.studentsInClass(id);
    }
}
