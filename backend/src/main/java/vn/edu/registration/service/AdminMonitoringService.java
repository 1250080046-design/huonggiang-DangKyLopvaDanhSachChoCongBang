package vn.edu.registration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.WaitlistEntry;
import vn.edu.registration.domain.WaitlistStatus;
import vn.edu.registration.repository.DecisionLogRepository;
import vn.edu.registration.repository.ClassSectionRepository;
import vn.edu.registration.repository.StudentRepository;
import vn.edu.registration.domain.ClassSection;
import vn.edu.registration.domain.Student;
import java.util.function.Function;
import java.util.stream.Collectors;
import vn.edu.registration.repository.WaitlistRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminMonitoringService {
    private final WaitlistRepository waitlist;
    private final DecisionLogRepository decisions;
    private final ClassSectionRepository classes;
    private final StudentRepository students;

    public AdminMonitoringService(WaitlistRepository waitlist, DecisionLogRepository decisions,
                                  ClassSectionRepository classes, StudentRepository students) {
        this.waitlist = waitlist;
        this.decisions = decisions;
        this.classes = classes;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.AdminWaitlistView> waitlist(WaitlistStatus status) {
        List<WaitlistEntry> rows = status == null
                ? waitlist.findAll().stream().filter(e -> e.getStatus() == WaitlistStatus.WAITING).toList()
                : waitlist.findByStatus(status);

        // Show the same projected order as the actual student-level Round Robin.
        Map<Long, List<WaitlistEntry>> waitingByClass = new HashMap<>();
        for (WaitlistEntry e : rows) {
            if (e.getStatus() == WaitlistStatus.WAITING) {
                waitingByClass.computeIfAbsent(e.getClassSection().getId(), unused -> new ArrayList<>()).add(e);
            }
        }
        Map<Long, Long> positions = new HashMap<>();
        for (List<WaitlistEntry> classQueue : waitingByClass.values()) {
            Long cursor = classQueue.get(0).getClassSection().getLastServedStudentId();
            List<WaitlistEntry> order = RoundRobinScheduler.order(classQueue,
                    e -> e.getStudent().getId(), cursor);
            for (int i = 0; i < order.size(); i++) positions.put(order.get(i).getId(), (long) i + 1);
        }
        List<ApiDtos.AdminWaitlistView> result = new ArrayList<>();
        for (WaitlistEntry e : rows) {
            result.add(new ApiDtos.AdminWaitlistView(
                    e.getId(), e.getClassSection().getId(), e.getClassSection().getName(),
                    e.getStudent().getId(), e.getStudent().getFullName(), e.getStudent().getAccount().getEmail(),
                    e.getJoinedAt(), e.getStatus(), positions.getOrDefault(e.getId(), 0L)));
        }
        result.sort(Comparator.comparing((ApiDtos.AdminWaitlistView v) -> v.classId())
                .thenComparing(v -> v.position() == 0 ? Long.MAX_VALUE : v.position())
                .thenComparing(ApiDtos.AdminWaitlistView::id));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.DecisionView> recentDecisions() {
        var records = decisions.findTop100ByOrderByOccurredAtDesc();
        if (records.isEmpty()) return List.of();
        // Tra cứu theo lô để tránh N+1 SQL queries khi có nhiều dòng lịch sử.
        var classIds = records.stream().map(d -> d.getClassId()).filter(java.util.Objects::nonNull).distinct().toList();
        var studentIds = records.stream().map(d -> d.getStudentId()).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, ClassSection> classById = classes.findAllById(classIds).stream()
                .collect(Collectors.toMap(ClassSection::getId, Function.identity()));
        Map<Long, Student> studentById = students.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        return records.stream().map(d -> {
            ClassSection section = classById.get(d.getClassId());
            Student student = studentById.get(d.getStudentId());
            return new ApiDtos.DecisionView(d.getId(), d.getClassId(), d.getStudentId(),
                    d.getWaitlistId(), d.getRegistrationId(), d.getOccurredAt(),
                    d.getResult(), d.getReason(),
                    section == null ? null : section.getName(),
                    section == null || section.getCourse() == null ? null : section.getCourse().getCode(),
                    section == null || section.getCourse() == null ? null : section.getCourse().getName(),
                    student == null ? null : student.getFullName(),
                    student == null || student.getAccount() == null ? null : student.getAccount().getEmail());
        }).toList();
    }
}
