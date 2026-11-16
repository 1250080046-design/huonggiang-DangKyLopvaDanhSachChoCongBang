package vn.edu.registration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Term;
import vn.edu.registration.service.AcademicCatalogService;
import java.util.List;

/** Catalog stored in SQL Server; read-only to any authenticated role. */
@RestController
@RequestMapping("/api/catalog")
public class PublicCatalogController {
    private final AcademicCatalogService catalog;

    public PublicCatalogController(AcademicCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/courses")
    public List<Course> courses() {
        return catalog.courses();
    }

    @GetMapping("/terms")
    public List<Term> terms() {
        return catalog.terms();
    }
}
