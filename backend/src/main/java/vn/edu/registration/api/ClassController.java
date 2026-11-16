package vn.edu.registration.api;

import org.springframework.web.bind.annotation.*;
import vn.edu.registration.service.ClassService;
import java.util.List;

@RestController
@RequestMapping("/api/classes")
public class ClassController {
    private final ClassService service;

    public ClassController(ClassService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApiDtos.ClassView> search(@RequestParam(required = false) String q) {
        return service.searchOpen(q);
    }
}
