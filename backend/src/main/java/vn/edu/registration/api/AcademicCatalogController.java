package vn.edu.registration.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Term;
import vn.edu.registration.service.AcademicCatalogService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog")
public class AcademicCatalogController {
    private final AcademicCatalogService service;

    public AcademicCatalogController(AcademicCatalogService service) {
        this.service = service;
    }

    @GetMapping("/courses")
    public List<Course> courses() {
        return service.courses();
    }

    @PostMapping("/courses")
    public Course createCourse(@Valid @RequestBody ApiDtos.CourseRequest req) {
        return service.createCourse(req);
    }

    @PutMapping("/courses/{id}")
    public Course updateCourse(@PathVariable Long id,
            @Valid @RequestBody ApiDtos.CourseRequest req) {
        return service.updateCourse(id, req);
    }

    @DeleteMapping("/courses/{id}")
    public void deleteCourse(@PathVariable Long id) {
        service.deleteCourse(id);
    }

    @GetMapping("/terms")
    public List<Term> terms() {
        return service.terms();
    }

    @PostMapping("/terms")
    public Term createTerm(@Valid @RequestBody ApiDtos.TermRequest req) {
        return service.createTerm(req);
    }

    @PutMapping("/terms/{id}")
    public Term updateTerm(@PathVariable Long id,
            @Valid @RequestBody ApiDtos.TermRequest req) {
        return service.updateTerm(id, req);
    }

    @PutMapping("/terms/{id}/registration")
    public Term registrationWindow(@PathVariable Long id,
            @Valid @RequestBody ApiDtos.RegistrationWindowRequest req) {
        return service.setRegistrationWindow(id, req);
    }

    @DeleteMapping("/terms/{id}")
    public void deleteTerm(@PathVariable Long id) {
        service.deleteTerm(id);
    }
}
