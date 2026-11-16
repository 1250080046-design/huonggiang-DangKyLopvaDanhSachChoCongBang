package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.util.List;

@Service
public class ClassService {
    private final ClassSectionRepository classes; private final CourseRepository courses; private final TermRepository terms;
    private final TeacherRepository teachers; private final RegistrationRepository registrationRepository; private final WaitlistRepository waitlistRepository; private final DecisionLogRepository decisionLogs; private final RegistrationService registrationService; private final RegistrationTermPolicy termPolicy; private final CurriculumPolicy curriculumPolicy; private final CurrentAccountService current;
    public ClassService(ClassSectionRepository classes, CourseRepository courses, TermRepository terms, TeacherRepository teachers, RegistrationService registrationService, RegistrationTermPolicy termPolicy, CurriculumPolicy curriculumPolicy, CurrentAccountService current, RegistrationRepository registrationRepository, WaitlistRepository waitlistRepository, DecisionLogRepository decisionLogs) {
        this.classes=classes; this.courses=courses; this.terms=terms; this.teachers=teachers; this.registrationService=registrationService; this.termPolicy=termPolicy; this.curriculumPolicy=curriculumPolicy; this.current=current; this.registrationRepository=registrationRepository; this.waitlistRepository=waitlistRepository; this.decisionLogs=decisionLogs;
    }
    @Transactional(readOnly=true)
    public List<ApiDtos.ClassView> search(String q) {
        String term=(q==null?"":q.trim().toLowerCase());
        return classes.findAll().stream().filter(c -> term.isEmpty() || c.getName().toLowerCase().contains(term)
                || c.getCourse().getName().toLowerCase().contains(term) || c.getCourse().getCode().toLowerCase().contains(term)
                || (c.getTeacher().getFullName()!=null && c.getTeacher().getFullName().toLowerCase().contains(term)))
                .map(this::view).toList();
    }
    /** Chỉ trả về các lớp đang được phép đăng ký cho màn hình tra cứu sinh viên. */
    @Transactional(readOnly=true)
    public List<ApiDtos.ClassView> searchOpen(String q) {
        Student student=current.student();
        String keyword=(q==null?"":q.trim().toLowerCase());
        return classes.findAll().stream()
                .filter(c -> c.getStatus()==ClassStatus.OPEN && termPolicy.canRegister(c.getTerm()))
                .filter(c -> curriculumPolicy.canRegister(student,c.getCourse()))
                .filter(c -> keyword.isEmpty() || c.getName().toLowerCase().contains(keyword)
                        || c.getCourse().getName().toLowerCase().contains(keyword)
                        || c.getCourse().getCode().toLowerCase().contains(keyword)
                        || (c.getTeacher().getFullName()!=null && c.getTeacher().getFullName().toLowerCase().contains(keyword)))
                .map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public List<ApiDtos.ClassView> teacherClasses(String email) {
        Teacher teacher=teachers.findByAccount_EmailIgnoreCase(email).orElseThrow(()->new EntityNotFoundException("Không tìm thấy hồ sơ giảng viên."));
        return classes.findByTeacher_Id(teacher.getId()).stream().map(this::view).toList();
    }
    /** Permanently delete only unused sections. Preserve all registration, queue and audit history. */
    @Transactional
    public void deleteUnusedClass(Long id) {
        ClassSection c = classes.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp học phần."));
        if (registrationRepository.existsByClassSection_Id(id)
                || waitlistRepository.existsByClassSection_Id(id)
                || decisionLogs.existsByClassId(id)) {
            throw new IllegalArgumentException("Không thể xóa lớp đã có đăng ký, danh sách chờ hoặc nhật ký quyết định. Hãy đóng hoặc hủy lớp để bảo toàn lịch sử.");
        }
        classes.delete(c);
        classes.flush();
    }

    @Transactional
    public ApiDtos.ClassView create(ApiDtos.CreateClassRequest req) {
        ScheduleConflictPolicy.parse(req.schedule());
        ClassSection c=new ClassSection(); c.setName(req.name().trim());
        c.setCourse(courses.findById(req.courseId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy học phần.")));
        c.setTerm(terms.findById(req.termId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy học kỳ.")));
        c.setTeacher(teachers.findById(req.teacherId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy giảng viên.")));
        c.setRoom(req.room()); c.setSchedule(req.schedule()); c.setCapacity(req.capacity()); c.setEnrolled(0); c.setStatus(termPolicy.canRegister(c.getTerm()) ? ClassStatus.OPEN : ClassStatus.CLOSED);
        return view(classes.save(c));
    }
    /** Changes scheduled teaching information without moving existing registrations to another course or term. */
    @Transactional
    public ApiDtos.ClassView updateDetails(Long id, ApiDtos.UpdateClassRequest req) {
        ClassSection c = classes.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp học."));
        long active = registrationService.activeCount(id);
        // A class belongs to a concrete academic term independently of the course curriculum semester.
        // Changing either course or term is forbidden once any registration/audit references exist.
        boolean termChanged = req.termId() != null && !req.termId().equals(c.getTerm().getId());
        boolean courseChanged = req.courseId() != null && !req.courseId().equals(c.getCourse().getId());
        if ((termChanged || courseChanged) && (registrationRepository.existsByClassSection_Id(id)
                || waitlistRepository.existsByClassSection_Id(id)
                || decisionLogs.existsByClassId(id))) {
            throw new IllegalArgumentException("Không thể đổi học phần hoặc học kỳ của lớp đã có đăng ký, danh sách chờ hoặc lịch sử Round Robin.");
        }
        if (termChanged) {
            c.setTerm(terms.findById(req.termId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy học kỳ được chọn.")));
        }
        // A different course changes the meaning of every registration and RR decision.
        // Allow it only if this section has no historical references whatsoever.
        if (req.courseId() != null && !req.courseId().equals(c.getCourse().getId())) {
            if (registrationRepository.existsByClassSection_Id(id)
                    || waitlistRepository.existsByClassSection_Id(id)
                    || decisionLogs.existsByClassId(id)) {
                throw new IllegalArgumentException("Không thể đổi học phần của lớp đã có đăng ký, danh sách chờ hoặc lịch sử Round Robin.");
            }
            Course newCourse = courses.findById(req.courseId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy học phần được chọn."));
            c.setCourse(newCourse);
        }
        if (termChanged || courseChanged) {
            Integer courseSemester = c.getCourse().getSemester();
            String termName = java.text.Normalizer.normalize(c.getTerm().getName().toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").trim();
            Integer termSemester = termName.matches(".*(?:hoc ky|hk|ky)\\s*1$") ? 1
                    : termName.matches(".*(?:hoc ky|hk|ky)\\s*2$") ? 2 : null;
            if (courseSemester != null && termSemester != null && !courseSemester.equals(termSemester))
                throw new IllegalArgumentException("Học phần thuộc học kỳ " + courseSemester + " nhưng lớp đang chọn " + c.getTerm().getName() + ". Vui lòng chọn học kỳ phù hợp.");
        }
        if (req.capacity() < active)
            throw new IllegalArgumentException("Sức chứa mới nhỏ hơn số sinh viên đang đăng ký.");
        String newSchedule = req.schedule() == null ? "" : req.schedule().trim();
        String oldSchedule = c.getSchedule() == null ? "" : c.getSchedule().trim();
        ScheduleConflictPolicy.parse(newSchedule);
        if (active > 0 && !newSchedule.equals(oldSchedule))
            throw new IllegalArgumentException("Lớp đã có sinh viên đăng ký. Không thể đổi lịch trực tiếp vì có thể gây trùng lịch của sinh viên.");
        // Admin can reopen the section independently of the registration window.
        // Student registration remains restricted by RegistrationTermPolicy in RegistrationService.
        if (req.status() == ClassStatus.CANCELLED && active > 0)
            throw new IllegalArgumentException("Không thể hủy lớp đang có sinh viên đăng ký.");
        Teacher teacher = teachers.findById(req.teacherId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy giảng viên."));
        // Only revalidate existing assignments when schedule/teacher/status is changed.
        // Old imported records may already contain overlaps; a capacity-only edit must not be blocked.
        boolean timetableChanged = !teacher.getId().equals(c.getTeacher().getId())
                || !newSchedule.equals(oldSchedule)
                || (req.status() == ClassStatus.OPEN && c.getStatus() != ClassStatus.OPEN);
        if (timetableChanged) for (ClassSection other : classes.findByTeacher_Id(teacher.getId())) {
            if (other.getId().equals(id) || other.getStatus() == ClassStatus.CANCELLED
                    || req.status() == ClassStatus.CANCELLED
                    || !other.getTerm().getId().equals(c.getTerm().getId())) continue;
            if (ScheduleConflictPolicy.conflicts(newSchedule, other.getSchedule()))
                throw new IllegalArgumentException("Giảng viên đã được phân công một lớp trùng lịch: " + other.getName());
        }
        c.setName(req.name().trim());
        c.setTeacher(teacher);
        c.setRoom(req.room() == null ? null : req.room().trim());
        c.setSchedule(newSchedule);
        c.setCapacity(req.capacity());
        c.setEnrolled((int) active);
        c.setStatus(req.status());
        // Do not promote anyone from the waitlist while the academic registration window is closed.
        if (c.getStatus() == ClassStatus.OPEN && termPolicy.canRegister(c.getTerm()))
            registrationService.promoteWaiting(c,"Admin sửa lớp hoặc mở lại lớp");
        return view(c);
    }

    @Transactional
    public ApiDtos.ClassView changeCapacity(Long id, int capacity) {
        ClassSection c=classes.findByIdForUpdate(id).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lớp học."));
        long active=registrationService.activeCount(id);
        if (capacity < active) throw new IllegalArgumentException("Sức chứa mới không thể nhỏ hơn sĩ số đã đăng ký ("+active+").");
        c.setCapacity(capacity); c.setEnrolled((int)active); registrationService.promoteWaiting(c,"Admin thay đổi sức chứa");
        return view(c);
    }
    public record BulkCloseResult(int closed, int skipped) {}

    /** Atomically close multiple sections; existing successful registrations remain intact. */
    @Transactional
    public BulkCloseResult closeClasses(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty() || classIds.size() > 200 || classIds.stream().anyMatch(id -> id == null || id < 1))
            throw new IllegalArgumentException("Vui lòng chọn từ 1 đến 200 lớp hợp lệ.");
        // A stable lock order reduces deadlock risk when two administrators act concurrently.
        List<Long> distinctIds = classIds.stream().distinct().sorted().toList();
        List<ClassSection> selected = new java.util.ArrayList<>(distinctIds.size());
        for (Long id : distinctIds) {
            selected.add(classes.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp học mã " + id + ".")));
        }
        int closed = 0;
        for (ClassSection c : selected) {
            if (c.getStatus() != ClassStatus.OPEN) continue;
            c.setStatus(ClassStatus.CLOSED);
            closed++;
        }
        return new BulkCloseResult(closed, selected.size() - closed);
    }

    @Transactional
    public void changeStatus(Long id, ClassStatus status) {
        ClassSection c=classes.findByIdForUpdate(id).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lớp học."));
        // Class lifecycle is independent from the semester's registration window.
        if (status==ClassStatus.CANCELLED && c.getEnrolled()>0) throw new IllegalArgumentException("Không thể hủy lớp còn sinh viên đã đăng ký. Hãy xử lý đăng ký trước.");
        c.setStatus(status);
        if (status==ClassStatus.OPEN && termPolicy.canRegister(c.getTerm()))
            registrationService.promoteWaiting(c,"Admin mở lại lớp");
    }
    public ApiDtos.ClassView view(ClassSection c) {
        String termName=c.getTerm().getName()+" "+c.getTerm().getAcademicYear();
        return new ApiDtos.ClassView(c.getId(), c.getName(), c.getCourse().getCode(), c.getCourse().getName(), termName,
                c.getTeacher().getFullName(), c.getTeacher().getId(), c.getRoom(), c.getSchedule(), c.getCapacity(), c.getEnrolled(),
                Math.max(0,c.getCapacity()-c.getEnrolled()), c.getStatus());
    }
}
