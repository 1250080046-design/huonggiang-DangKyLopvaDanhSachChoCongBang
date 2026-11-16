package vn.edu.registration.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RegistrationService {
    private final ClassSectionRepository classes; private final RegistrationRepository registrations; private final WaitlistRepository waitlist;
    private final int maxCredits;
    private final RoundRobinRunRepository roundRobinRuns;
    private final RoundRobinCandidateLogRepository roundRobinCandidates;
    private final StudentRepository students; private final DecisionLogRepository logs; private final CurrentAccountService current; private final RegistrationTermPolicy termPolicy; private final CurriculumPolicy curriculumPolicy;
    public RegistrationService(ClassSectionRepository classes, RegistrationRepository registrations, WaitlistRepository waitlist,
            StudentRepository students, DecisionLogRepository logs, CurrentAccountService current, RegistrationTermPolicy termPolicy, CurriculumPolicy curriculumPolicy,
            RoundRobinRunRepository roundRobinRuns, RoundRobinCandidateLogRepository roundRobinCandidates,
            @Value("${app.registration.max-credits:24}") int maxCredits) {
        this.classes=classes; this.registrations=registrations; this.waitlist=waitlist; this.students=students; this.logs=logs; this.current=current; this.termPolicy=termPolicy; this.curriculumPolicy=curriculumPolicy; this.roundRobinRuns=roundRobinRuns; this.roundRobinCandidates=roundRobinCandidates; this.maxCredits=maxCredits;
    }
    @Transactional
    public ApiDtos.ActionResult register(Long classId) {
        Student student=current.student();
        ClassSection c=classes.findByIdForUpdate(classId).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lớp học."));
        lockStudentsInOrder(c, student.getId());
        student = lockStudent(student.getId());
        if (!termPolicy.canRegister(c.getTerm())) throw new IllegalArgumentException("Chỉ được đăng ký " + termPolicy.openPeriod() + ". Học kỳ 1 đã đóng và sẽ mở ở đợt năm sau.");
        if (c.getStatus()!=ClassStatus.OPEN) throw new IllegalArgumentException("Lớp học hiện không mở đăng ký.");
        if (!curriculumPolicy.canRegister(student,c.getCourse()))
            throw new IllegalArgumentException(curriculumPolicy.rejectionReason(student,c.getCourse()));
        Registration existing=registrations.findByStudent_IdAndClassSection_Id(student.getId(), classId).orElse(null);
        if (existing!=null && existing.getStatus()==RegistrationStatus.ACTIVE) throw new IllegalArgumentException("Bạn đã đăng ký lớp này rồi.");
        // Một sinh viên không được giữ hai lớp của cùng học phần trong cùng học kỳ.
        // Kiểm tra cả đăng ký chính thức lẫn lượt chờ của các lớp khác.
        ensureNoDuplicateCourseTerm(student, c);
        ensureAcademicEligibility(student, c);
        refreshCount(c);
        WaitlistEntry prior=waitlist.findByStudent_IdAndClassSection_Id(student.getId(), classId).orElse(null);
        if (prior!=null && prior.getStatus()==WaitlistStatus.WAITING) {
            return new ApiDtos.ActionResult("WAITLISTED", "Bạn đã có trong danh sách chờ.", classId, prior.getId(), position(prior));
        }
        // Neu da co nguoi cho, khong duoc vuot hang; day cac vi tri cho len truoc khi chap nhan yeu cau moi.
        if (waitlist.countByClassSection_IdAndStatus(classId, WaitlistStatus.WAITING)>0 && c.getEnrolled()<c.getCapacity()) promoteWaiting(c,"Đăng ký trực tiếp khi còn người chờ");
        refreshCount(c);
        if (c.getEnrolled()<c.getCapacity() && waitlist.countByClassSection_IdAndStatus(classId, WaitlistStatus.WAITING)==0) {
            Registration r=existing==null?new Registration():existing; r.setStudent(student); r.setClassSection(c); r.setRegisteredAt(LocalDateTime.now()); r.setStatus(RegistrationStatus.ACTIVE);
            registrations.save(r); refreshCount(c); log(c, student, null, r, "REGISTERED", "Đăng ký trực tiếp còn chỗ.");
            return new ApiDtos.ActionResult("REGISTERED", "Đăng ký lớp học thành công.", classId, r.getId(), null);
        }
        WaitlistEntry entry=prior==null?new WaitlistEntry():prior; entry.setStudent(student); entry.setClassSection(c); entry.setJoinedAt(LocalDateTime.now()); entry.setStatus(WaitlistStatus.WAITING); entry.setIneligibleReason(null); entry.setUpdatedAt(LocalDateTime.now()); waitlist.save(entry);
        log(c, student, entry, null, "WAITLISTED", "Lớp đã đầy hoặc còn người trong hàng chờ; xét Round Robin theo từng sinh viên.");
        return new ApiDtos.ActionResult("WAITLISTED", "Lớp đã đầy hoặc còn người trong hàng chờ. Bạn đã được thêm vào danh sách chờ.", classId, entry.getId(), position(entry));
    }
    private boolean hasOtherActiveSection(Student student, ClassSection target) {
        return registrations.findByStudent_IdOrderByRegisteredAtDesc(student.getId()).stream()
                .anyMatch(r -> r.getStatus()==RegistrationStatus.ACTIVE
                        && !r.getClassSection().getId().equals(target.getId())
                        && sameCourseAndTerm(r.getClassSection(),target));
    }
    private void ensureNoDuplicateCourseTerm(Student student, ClassSection target) {
        for (Registration registration : registrations.findByStudent_IdOrderByRegisteredAtDesc(student.getId())) {
            ClassSection other=registration.getClassSection();
            if (registration.getStatus()==RegistrationStatus.ACTIVE
                    && !other.getId().equals(target.getId())
                    && sameCourseAndTerm(other,target)) {
                throw new IllegalArgumentException("Bạn đã đăng ký lớp khác thuộc cùng học phần và học kỳ.");
            }
        }
        for (WaitlistEntry entry : waitlist.findByStudent_IdOrderByJoinedAtDesc(student.getId())) {
            ClassSection other=entry.getClassSection();
            if (entry.getStatus()==WaitlistStatus.WAITING
                    && !other.getId().equals(target.getId())
                    && sameCourseAndTerm(other,target)) {
                throw new IllegalArgumentException("Bạn đã ở danh sách chờ của lớp khác thuộc cùng học phần và học kỳ.");
            }
        }
    }
    /** Check only officially enrolled classes in the same term; scheduled waitlist requests
     * are checked again when promoted so the student can resolve conflicts beforehand. */
    private String academicRejection(Student student, ClassSection target) {
        int credits = target.getCourse().getCredits();
        for (Registration registration : registrations.findByStudent_IdOrderByRegisteredAtDesc(student.getId())) {
            if (registration.getStatus()!=RegistrationStatus.ACTIVE) continue;
            ClassSection enrolled = registration.getClassSection();
            if (!enrolled.getTerm().getId().equals(target.getTerm().getId())) continue;
            if (enrolled.getId().equals(target.getId())) continue;
            if (ScheduleConflictPolicy.conflicts(enrolled.getSchedule(),target.getSchedule()))
                return "Trùng lịch học với lớp " + enrolled.getName() + ".";
            credits += enrolled.getCourse().getCredits();
        }
        if (credits>maxCredits) return "Vượt giới hạn " + maxCredits + " tín chỉ trong học kỳ (tổng dự kiến " + credits + ").";
        return null;
    }
    private void ensureAcademicEligibility(Student student, ClassSection target) {
        String rejection=academicRejection(student,target);
        if (rejection!=null) throw new IllegalArgumentException(rejection);
    }
    private boolean sameCourseAndTerm(ClassSection a, ClassSection b) {
        return a.getCourse().getId().equals(b.getCourse().getId())
                && a.getTerm().getId().equals(b.getTerm().getId());
    }

    @Transactional
    public ApiDtos.ActionResult cancelRegistration(Long registrationId) {
        Registration r=registrations.findById(registrationId).orElseThrow(()->new EntityNotFoundException("Không tìm thấy đăng ký."));
        Student student=current.student();
        if (!r.getStudent().getId().equals(student.getId())) throw new IllegalArgumentException("Bạn không có quyền hủy đăng ký này.");
        ClassSection c=classes.findByIdForUpdate(r.getClassSection().getId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lớp học."));
        lockStudentsInOrder(c, student.getId());
        student = lockStudent(student.getId());
        r=registrations.findById(registrationId).orElseThrow();
        if (r.getStatus()!=RegistrationStatus.ACTIVE) throw new IllegalArgumentException("Đăng ký này đã được hủy trước đó.");
        r.setStatus(RegistrationStatus.CANCELLED); log(c, student, null, r, "CANCELLED", "Sinh viên chủ động hủy đăng ký.");
        refreshCount(c); promoteWaiting(c,"Sinh viên hủy đăng ký"); refreshCount(c);
        return new ApiDtos.ActionResult("CANCELLED", "Đã hủy đăng ký. Hệ thống đã xét hàng chờ Round Robin.", c.getId(), r.getId(), null);
    }
    @Transactional
    public ApiDtos.ActionResult withdrawWaitlist(Long entryId) {
        WaitlistEntry e=waitlist.findById(entryId).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lượt chờ."));
        Student student=current.student();
        if (!e.getStudent().getId().equals(student.getId())) throw new IllegalArgumentException("Bạn không có quyền rút lượt chờ này.");
        ClassSection c=classes.findByIdForUpdate(e.getClassSection().getId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lớp học."));
        lockStudentsInOrder(c, student.getId());
        student = lockStudent(student.getId());
        if (e.getStatus()!=WaitlistStatus.WAITING) throw new IllegalArgumentException("Lượt chờ này không còn ở trạng thái đang chờ.");
        e.setStatus(WaitlistStatus.WITHDRAWN); e.setUpdatedAt(LocalDateTime.now()); log(c, student, e, null, "WAITLIST_WITHDRAWN", "Sinh viên rút khỏi danh sách chờ.");
        promoteWaiting(c,"Sinh viên rút khỏi danh sách chờ");
        return new ApiDtos.ActionResult("WITHDRAWN", "Đã rút khỏi danh sách chờ.", c.getId(), e.getId(), null);
    }
    /** Rejoin after a previous ineligible decision; never skip the Round Robin queue. */
    @Transactional
    public ApiDtos.ActionResult rejoinWaitlist(Long entryId) {
        WaitlistEntry e=waitlist.findById(entryId).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lượt chờ."));
        Student currentStudent=current.student();
        if (!e.getStudent().getId().equals(currentStudent.getId())) throw new IllegalArgumentException("Bạn không có quyền tham gia lại lượt chờ này.");
        ClassSection c=classes.findByIdForUpdate(e.getClassSection().getId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy lớp học."));
        lockStudentsInOrder(c, currentStudent.getId());
        Student student=lockStudent(currentStudent.getId());
        if (e.getStatus()!=WaitlistStatus.INELIGIBLE) throw new IllegalArgumentException("Chỉ lượt chờ không đủ điều kiện mới có thể yêu cầu tham gia lại.");
        if (c.getStatus()!=ClassStatus.OPEN || !termPolicy.canRegister(c.getTerm())) throw new IllegalArgumentException("Lớp hoặc đợt đăng ký hiện không mở.");
        if (!student.getAccount().isActivated() || student.getAccount().getStatus()!=AccountStatus.ACTIVE) throw new IllegalArgumentException("Tài khoản sinh viên chưa hợp lệ.");
        if (!curriculumPolicy.canRegister(student,c.getCourse())) throw new IllegalArgumentException(curriculumPolicy.rejectionReason(student,c.getCourse()));
        if (hasOtherActiveSection(student,c)) throw new IllegalArgumentException("Bạn đã đăng ký lớp khác cùng học phần.");
        ensureNoDuplicateCourseTerm(student,c);
        ensureAcademicEligibility(student,c);
        if (registrations.existsByStudent_IdAndClassSection_IdAndStatus(student.getId(),c.getId(),RegistrationStatus.ACTIVE)) throw new IllegalArgumentException("Bạn đã đăng ký chính thức lớp này.");
        // Reset join timestamp for a new request. Ordering remains based on stable student ID and cursor.
        e.setStatus(WaitlistStatus.WAITING); e.setIneligibleReason(null); e.setJoinedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now());
        waitlist.save(e);
        log(c,student,e,null,"WAITLIST_REJOINED","Sinh viên đã khắc phục điều kiện và yêu cầu tham gia lại theo vòng Round Robin.");
        promoteWaiting(c,"Yêu cầu tham gia lại");
        return new ApiDtos.ActionResult(e.getStatus()==WaitlistStatus.PROMOTED?"REGISTERED":"WAITLISTED",
                e.getStatus()==WaitlistStatus.PROMOTED?"Đã được cấp chỗ theo Round Robin.":"Đã tham gia lại danh sách chờ; không có ưu tiên đặc biệt.", c.getId(),e.getId(),e.getStatus()==WaitlistStatus.WAITING?position(e):null);
    }
    private Student lockStudent(Long id) {
        return students.findByIdForUpdate(id).orElseThrow(()->new EntityNotFoundException("Không tìm thấy sinh viên."));
    }
    /** With the class row already locked, collect all affected students and acquire their
     * write locks in globally ascending ID order, independent of each class's RR cursor.
     * Every registration, cancellation, rejoin and promotion uses this order. */
    private void lockStudentsInOrder(ClassSection c, Long... alsoLock) {
        java.util.TreeSet<Long> ids=new java.util.TreeSet<>();
        for (WaitlistEntry e : waitlist.findByClassSection_IdAndStatus(c.getId(),WaitlistStatus.WAITING)) ids.add(e.getStudent().getId());
        for (Long id:alsoLock) if(id!=null) ids.add(id);
        for (Long id:ids) lockStudent(id);
    }

    @Transactional(readOnly=true)
    public List<ApiDtos.RegistrationView> myRegistrations() {
        Student s=current.student();
        return registrations.findByStudent_IdOrderByRegisteredAtDesc(s.getId()).stream().map(r -> new ApiDtos.RegistrationView(
                r.getId(), r.getClassSection().getId(), r.getClassSection().getName(), r.getClassSection().getCourse().getCode(),
                r.getClassSection().getCourse().getName(), r.getRegisteredAt(), r.getStatus())).toList();
    }
    /** SQL-backed timetable; enrolled classes remain visible when the registration period closes. */
    @Transactional(readOnly=true)
    public ApiDtos.StudySchedule mySchedule() {
        Student student = current.student();
        List<ApiDtos.StudyScheduleItem> items = registrations
                .findByStudent_IdOrderByRegisteredAtDesc(student.getId()).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.ACTIVE)
                .map(r -> {
                    ClassSection c = r.getClassSection();
                    return new ApiDtos.StudyScheduleItem(r.getId(), c.getId(), c.getName(),
                            c.getCourse().getCode(), c.getCourse().getName(), c.getCourse().getCredits(),
                            c.getTerm().getName() + " " + c.getTerm().getAcademicYear(),
                            c.getTeacher().getFullName(), c.getRoom(), c.getSchedule());
                }).toList();
        return new ApiDtos.StudySchedule(termPolicy.openPeriod(), items);
    }

    @Transactional(readOnly=true)
    public List<ApiDtos.WaitlistView> myWaitlist() {
        Student s=current.student();
        return waitlist.findByStudent_IdOrderByJoinedAtDesc(s.getId()).stream().map(this::waitlistView).toList();
    }
    @Transactional(readOnly=true)
    public List<ApiDtos.StudentInClass> studentsInClass(Long classId) {
        return registrations.findClassRegistrations(classId, RegistrationStatus.ACTIVE).stream().map(r -> new ApiDtos.StudentInClass(
                r.getStudent().getId(), r.getStudent().getFullName(), r.getStudent().getAccount().getEmail(), r.getStudent().getFaculty(), r.getRegisteredAt())).toList();
    }
    public long activeCount(Long classId) { return registrations.countByClassSection_IdAndStatus(classId, RegistrationStatus.ACTIVE); }
/**
 * The caller owns the pessimistic lock on LopHoc. Rotate between individual
 * students using a persistent per-class cursor (LopHoc.SinhVienRRGanNhat).
 * Join timestamp never determines priority.
 */
@Transactional
public void promoteWaiting(ClassSection c) { promoteWaiting(c,"Phát sinh chỗ hoặc yêu cầu đăng ký"); }

/** Caller must hold class lock. Lock order for all writers: CLASS -> STUDENT.
 * Audit rows and registration changes share one transaction and roll back together. */
@Transactional
public void promoteWaiting(ClassSection c, String trigger) {
    refreshCount(c);
    if (c.getStatus()!=ClassStatus.OPEN || !termPolicy.canRegister(c.getTerm())) return;
    List<WaitlistEntry> queue=new java.util.ArrayList<>(waitlist.findByClassSection_IdAndStatus(c.getId(), WaitlistStatus.WAITING));
    if (queue.isEmpty() || c.getEnrolled()>=c.getCapacity()) return;
    lockStudentsInOrder(c);
    RoundRobinRun run=new RoundRobinRun();
    run.setClassId(c.getId()); run.setTrigger(trigger); run.setEnrolledBefore(c.getEnrolled());
    run.setCapacity(c.getCapacity()); run.setCursorBefore(c.getLastServedStudentId());
    run.setOutcome("IN_PROGRESS");
    run=roundRobinRuns.save(run);
    int sequence=0; int promoted=0;
    while (c.getEnrolled()<c.getCapacity() && !queue.isEmpty()) {
        WaitlistEntry e=RoundRobinScheduler.next(queue, item->item.getStudent().getId(), c.getLastServedStudentId());
        queue.remove(e);
        // A second class may be granting a seat simultaneously. This row lock forces
        // eligibility checks to observe committed changes made by that transaction.
        Student student=lockStudent(e.getStudent().getId());
        String rejection=placementRejection(student,c);
        RoundRobinCandidateLog candidate=new RoundRobinCandidateLog(); candidate.setRun(run);
        candidate.setSequence(++sequence); candidate.setStudentId(student.getId()); candidate.setWaitlistId(e.getId());
        if (rejection!=null) {
            e.setStatus(WaitlistStatus.INELIGIBLE); e.setIneligibleReason(rejection);
            e.setUpdatedAt(LocalDateTime.now());
            candidate.setResult("SKIPPED_INELIGIBLE"); candidate.setReason(rejection);
            roundRobinCandidates.save(candidate);
            log(c,student,e,null,"SKIPPED_INELIGIBLE","Không đủ điều kiện tại thời điểm xét: "+rejection);
            continue;
        }
        Registration r=registrations.findByStudent_IdAndClassSection_Id(student.getId(),c.getId()).orElseGet(Registration::new);
        r.setStudent(student); r.setClassSection(c); r.setRegisteredAt(LocalDateTime.now());
        r.setStatus(RegistrationStatus.ACTIVE); registrations.save(r);
        e.setStatus(WaitlistStatus.PROMOTED); e.setIneligibleReason(null); e.setUpdatedAt(LocalDateTime.now());
        c.setLastServedStudentId(student.getId());
        refreshCount(c); promoted++;
        candidate.setResult("PROMOTED_ROUND_ROBIN"); candidate.setReason("Đủ điều kiện; cấp chỗ trực tiếp.");
        roundRobinCandidates.save(candidate);
        log(c,student,e,r,"PROMOTED_ROUND_ROBIN","Được cấp chỗ theo Round Robin; con trỏ mới: "+c.getLastServedStudentId());
    }
    run.setEnrolledAfter(c.getEnrolled()); run.setCursorAfter(c.getLastServedStudentId());
    run.setPromotedCount(promoted); run.setOutcome(promoted>0?"PROMOTED":"NO_ELIGIBLE_CANDIDATE");
    roundRobinRuns.save(run);
}
private String placementRejection(Student student, ClassSection c) {
    if (!curriculumPolicy.canRegister(student,c.getCourse())) return curriculumPolicy.rejectionReason(student,c.getCourse());
    if (!student.getAccount().isActivated() || student.getAccount().getStatus()!=AccountStatus.ACTIVE) return "Tài khoản sinh viên không hoạt động hoặc chưa kích hoạt.";
    if (registrations.existsByStudent_IdAndClassSection_IdAndStatus(student.getId(),c.getId(),RegistrationStatus.ACTIVE)) return "Đã đăng ký lớp này.";
    if (hasOtherActiveSection(student,c)) return "Đã có lớp khác thuộc cùng học phần trong học kỳ.";
    return academicRejection(student,c);
}
    private void refreshCount(ClassSection c) { c.setEnrolled((int)registrations.countByClassSection_IdAndStatus(c.getId(), RegistrationStatus.ACTIVE)); }
private long position(WaitlistEntry e) {
    ClassSection c=e.getClassSection();
    List<WaitlistEntry> queue=waitlist.findByClassSection_IdAndStatus(c.getId(), WaitlistStatus.WAITING);
    List<WaitlistEntry> ordered=RoundRobinScheduler.order(queue, item->item.getStudent().getId(), c.getLastServedStudentId());
    for (int i=0;i<ordered.size();i++) if (ordered.get(i).getId().equals(e.getId())) return i+1L;
    return 0;
}
    private ApiDtos.WaitlistView waitlistView(WaitlistEntry e) {
        long pos=e.getStatus()==WaitlistStatus.WAITING?position(e):0;
        ClassSection c=e.getClassSection();
        return new ApiDtos.WaitlistView(e.getId(), c.getId(), c.getName(), c.getCourse().getCode(), c.getCourse().getName(), e.getJoinedAt(), e.getStatus(), pos, e.getIneligibleReason(), e.getUpdatedAt());
    }
    private void log(ClassSection c, Student s, WaitlistEntry w, Registration r, String result, String reason) {
        DecisionLog d=new DecisionLog(); d.setClassId(c.getId()); d.setStudentId(s==null?null:s.getId()); d.setWaitlistId(w==null?null:w.getId());
        d.setRegistrationId(r==null?null:r.getId()); d.setActorAccountId(current.account().getId()); d.setOccurredAt(LocalDateTime.now()); d.setResult(result); d.setReason(reason); logs.save(d);
    }
}
