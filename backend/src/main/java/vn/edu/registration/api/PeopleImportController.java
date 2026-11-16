package vn.edu.registration.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.registration.domain.Role;
import vn.edu.registration.service.PeopleImportService;

@RestController
@RequestMapping("/api/admin/import")
public class PeopleImportController {
    private final PeopleImportService service;
    public PeopleImportController(PeopleImportService service) { this.service = service; }

    @PostMapping(path = "/students", consumes = "multipart/form-data")
    public PeopleImportService.ImportSummary students(@RequestParam("file") MultipartFile file) {
        return service.importFile(file, Role.STUDENT);
    }

    @PostMapping(path = "/teachers", consumes = "multipart/form-data")
    public PeopleImportService.ImportSummary teachers(@RequestParam("file") MultipartFile file) {
        return service.importFile(file, Role.TEACHER);
    }
}
