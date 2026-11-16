package vn.edu.registration.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.registration.repository.StudentRepository;
import vn.edu.registration.repository.TeacherRepository;

import java.util.List;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentCaptor;
import vn.edu.registration.domain.Teacher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeopleAdminControllerTest {
    @Mock StudentRepository students;
    @Mock TeacherRepository teachers;
    @Mock vn.edu.registration.service.PeopleExportService exportService;
    @InjectMocks PeopleAdminController controller;

    @Test
    void peopleFiltersUseDistinctDatabaseValuesAcrossAllAccounts() {
        when(students.findDistinctFaculties()).thenReturn(List.of("Công nghệ thông tin", " ", "Kinh tế"));
        when(students.findDistinctMajors()).thenReturn(List.of("Kỹ thuật phần mềm", "", "Khoa học máy tính"));
        when(students.findDistinctCohorts()).thenReturn(List.of("K22", "K23"));
        when(teachers.findDistinctFaculties()).thenReturn(List.of("Công nghệ thông tin"));
        when(teachers.findDistinctSpecialties()).thenReturn(List.of("Lập trình", "Cơ sở dữ liệu"));

        var filters = controller.peopleFilters();
        assertThat(filters.studentMajorCount()).isEqualTo(2);
        assertThat(filters.studentMajors()).containsExactly("Khoa học máy tính", "Kỹ thuật phần mềm");
        assertThat(filters.studentCohorts()).containsExactly("K22", "K23");
        assertThat(filters.studentFaculties()).doesNotContain(" ");
        assertThat(filters.teacherSpecialties()).containsExactly("Cơ sở dữ liệu", "Lập trình");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exportUsesAllFilteredStudentsNotOnlyFirstPage() {
        when(students.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());
        controller.exportStudents("An", "", "Kỹ thuật phần mềm", "K22", "ACTIVE", "xlsx");
        verify(students).findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class));
        verify(exportService).export(List.of(), true, "xlsx");
        verify(students, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teacherExportUsesSpecialtyAndAllRows() {
        when(teachers.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());
        controller.exportTeachers("", "", "", "Kỹ thuật dữ liệu", "csv");
        verify(teachers).findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class));
        verify(exportService).export(List.of(), false, "csv");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teacherFilterUsesExactSpecialtyFromBoMonBeforePagination() {
        when(teachers.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        var result = controller.teachers("", "", "", "  Kỹ thuật dữ liệu  ", 0, 20);
        assertThat(result.getTotalElements()).isZero();

        ArgumentCaptor<Specification<Teacher>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(teachers).findAll(captor.capture(), any(Pageable.class));
        Root<Teacher> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> boMon = mock(Path.class);
        Expression<String> trimmed = mock(Expression.class);
        Expression<String> lowered = mock(Expression.class);
        Predicate initial = mock(Predicate.class);
        Predicate match = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(initial);
        when(root.<String>get("major")).thenReturn(boMon);
        when(cb.trim(boMon)).thenReturn(trimmed);
        when(cb.lower(trimmed)).thenReturn(lowered);
        when(cb.equal(lowered, "kỹ thuật dữ liệu")).thenReturn(match);
        when(cb.and(initial, match)).thenReturn(match);

        captor.getValue().toPredicate(root, query, cb);
        verify(root).get("major");
        verify(cb).equal(lowered, "kỹ thuật dữ liệu");
    }
}
