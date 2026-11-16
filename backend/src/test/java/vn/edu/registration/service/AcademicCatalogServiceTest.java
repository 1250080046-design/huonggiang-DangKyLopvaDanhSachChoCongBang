package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Term;
import vn.edu.registration.repository.CourseRepository;
import vn.edu.registration.repository.TermRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AcademicCatalogServiceTest {
    private final CourseRepository courses=Mockito.mock(CourseRepository.class);
    private final TermRepository terms=Mockito.mock(TermRepository.class);
    private final vn.edu.registration.repository.ClassSectionRepository classes=Mockito.mock(vn.edu.registration.repository.ClassSectionRepository.class);
    private final AcademicCatalogService service=new AcademicCatalogService(courses,terms,classes);

    @Test void rejectsDuplicateCourseCode() {
        when(courses.findByCodeIgnoreCase("JAVA101")).thenReturn(Optional.of(new Course()));
        assertThrows(IllegalArgumentException.class,
                () -> service.createCourse(new ApiDtos.CourseRequest("java101","Lập trình Java",3)));
        verify(courses,never()).save(any());
    }

    @Test void normalizesCodeAndSavesCourse() {
        when(courses.findByCodeIgnoreCase("JAVA101")).thenReturn(Optional.empty());
        when(courses.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        Course result=service.createCourse(new ApiDtos.CourseRequest(" java101 ","Lập trình Java",3));
        assertEquals("JAVA101", result.getCode());
        assertEquals(3, result.getCredits());
    }

    @Test void rejectsInvertedTermDates() {
        when(terms.findAll()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.createTerm(
                new ApiDtos.TermRequest("HK1","2026-2027",
                        LocalDate.of(2026,12,1),LocalDate.of(2026,9,1))));
        verify(terms,never()).save(any());
    }

    @Test void refusesDeleteWhenCourseHasClasses() {
        when(courses.existsById(1L)).thenReturn(true);
        when(classes.existsByCourse_Id(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.deleteCourse(1L));
        verify(courses,never()).deleteById(1L);
    }

    @Test void rejectsDuplicateTerm() {
        Term existing=new Term();
        existing.setName("Học kỳ 1");
        existing.setAcademicYear("2026-2027");
        when(terms.findAll()).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class, () -> service.createTerm(
                new ApiDtos.TermRequest("Học kỳ 1","2026-2027",null,null)));
    }
}
