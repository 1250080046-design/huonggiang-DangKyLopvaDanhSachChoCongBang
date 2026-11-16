package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.Course;
import vn.edu.registration.domain.Term;
import vn.edu.registration.repository.CourseRepository;
import vn.edu.registration.repository.TermRepository;
import vn.edu.registration.repository.ClassSectionRepository;

import java.util.List;

@Service
public class AcademicCatalogService {
    private final CourseRepository courses;
    private final TermRepository terms;
    private final ClassSectionRepository classes;

    public AcademicCatalogService(CourseRepository courses, TermRepository terms,
                                  ClassSectionRepository classes) {
        this.courses = courses;
        this.terms = terms;
        this.classes = classes;
    }

    @Transactional(readOnly=true)
    public List<Course> courses() { return courses.findAll(); }

    @Transactional(readOnly=true)
    public List<Term> terms() { return terms.findAll(); }

    @Transactional
    public Course createCourse(ApiDtos.CourseRequest req) {
        String code = req.code().trim().toUpperCase(java.util.Locale.ROOT);
        if (courses.findByCodeIgnoreCase(code).isPresent())
            throw new IllegalArgumentException("Mã học phần đã tồn tại.");
        Course course = new Course();
        course.setCode(code);
        course.setName(req.name().trim());
        course.setCredits(req.credits());
        applyProgram(course, req);
        return courses.save(course);
    }

    @Transactional
    public Course updateCourse(Long id, ApiDtos.CourseRequest req) {
        Course course = courses.findById(id).orElseThrow(
            () -> new EntityNotFoundException("Không tìm thấy học phần."));
        String code = req.code().trim().toUpperCase(java.util.Locale.ROOT);
        if (courses.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("Mã học phần đã được sử dụng.");
        course.setCode(code);
        course.setName(req.name().trim());
        course.setCredits(req.credits());
        applyProgram(course, req);
        return course;
    }

    private void applyProgram(Course course, ApiDtos.CourseRequest req) {
        if ((req.year() == null) != (req.semester() == null)) {
            throw new IllegalArgumentException("Năm đào tạo và học kỳ dự kiến phải cùng có hoặc cùng để trống.");
        }
        course.setYear(req.year());
        course.setSemester(req.semester());
        course.setMajor(req.major() == null || req.major().isBlank() ? null : req.major().trim());
    }

    @Transactional
    public void deleteCourse(Long id) {
        if (!courses.existsById(id)) throw new EntityNotFoundException("Không tìm thấy học phần.");
        if (classes.existsByCourse_Id(id))
            throw new IllegalArgumentException("Học phần đã có lớp, không thể xóa.");
        courses.deleteById(id);
    }

    @Transactional
    public Term setRegistrationWindow(Long id, ApiDtos.RegistrationWindowRequest request) {
        Term selected = terms.findById(id).orElseThrow(() -> new EntityNotFoundException("Không tìm thấy học kỳ."));
        if (request.startsAt() != null && request.endsAt() != null
                && !request.startsAt().isBefore(request.endsAt()))
            throw new IllegalArgumentException("Thời điểm đóng đăng ký phải sau thời điểm mở.");
        if (request.open() && !selected.getName().matches("(?iu)^(?:học kỳ|hk|kỳ)\\s*[12]$"))
            throw new IllegalArgumentException("Chỉ hỗ trợ mở đợt đăng ký cho học kỳ 1 hoặc 2.");
        if (request.open() && !selected.getAcademicYear().matches("\\d{4}-\\d{4}"))
            throw new IllegalArgumentException("Năm học phải có dạng YYYY-YYYY.");
        if (request.open()) {
            for (Term other : terms.findByRegistrationOpenTrue()) {
                if (!other.getId().equals(id)) other.setRegistrationOpen(false);
            }
        }
        selected.setRegistrationOpen(request.open());
        selected.setRegistrationStartsAt(request.startsAt());
        selected.setRegistrationEndsAt(request.endsAt());
        return selected;
    }

    @Transactional
    public void deleteTerm(Long id) {
        if (!terms.existsById(id)) throw new EntityNotFoundException("Không tìm thấy học kỳ.");
        if (classes.existsByTerm_Id(id))
            throw new IllegalArgumentException("Học kỳ đã có lớp, không thể xóa.");
        terms.deleteById(id);
    }

    private void validateTerm(ApiDtos.TermRequest req) {
        if (req.startDate() != null && req.endDate() != null
                && req.endDate().isBefore(req.startDate()))
            throw new IllegalArgumentException("Ngày kết thúc học kỳ phải sau ngày bắt đầu.");
        if (terms.findAll().stream().anyMatch(t ->
            t.getName().equalsIgnoreCase(req.name().trim())
            && t.getAcademicYear().equalsIgnoreCase(req.academicYear().trim())))
            throw new IllegalArgumentException("Học kỳ và năm học đã tồn tại.");
    }

    @Transactional
    public Term createTerm(ApiDtos.TermRequest req) {
        validateTerm(req);
        Term term = new Term();
        apply(term, req);
        return terms.save(term);
    }

    @Transactional
    public Term updateTerm(Long id, ApiDtos.TermRequest req) {
        Term term = terms.findById(id).orElseThrow(
            () -> new EntityNotFoundException("Không tìm thấy học kỳ."));
        if (req.startDate() != null && req.endDate() != null
                && req.endDate().isBefore(req.startDate()))
            throw new IllegalArgumentException("Ngày kết thúc học kỳ phải sau ngày bắt đầu.");
        if (terms.findAll().stream().anyMatch(t ->
            !t.getId().equals(id)
            && t.getName().equalsIgnoreCase(req.name().trim())
            && t.getAcademicYear().equalsIgnoreCase(req.academicYear().trim())))
            throw new IllegalArgumentException("Học kỳ và năm học đã tồn tại.");
        apply(term, req);
        return term;
    }

    private void apply(Term term, ApiDtos.TermRequest req) {
        term.setName(req.name().trim());
        term.setAcademicYear(req.academicYear().trim());
        term.setStartDate(req.startDate());
        term.setEndDate(req.endDate());
    }
}
