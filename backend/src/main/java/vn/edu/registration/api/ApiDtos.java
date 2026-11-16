package vn.edu.registration.api;

import jakarta.validation.constraints.*;
import vn.edu.registration.domain.*;
import java.time.LocalDateTime;

public final class ApiDtos {
    private ApiDtos() {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min=12, max=100) String newPassword) {}
    public record CourseRequest(@NotBlank @Size(max=30) String code,
                                @NotBlank @Size(max=200) String name,
                                @NotNull @Min(1) @Max(10) Integer credits,
                                @Min(1) @Max(4) Integer year,
                                @Min(1) @Max(2) Integer semester,
                                @Size(max=150) String major) {
        public CourseRequest(String code, String name, Integer credits) {
            this(code, name, credits, null, null, null);
        }
    }
    public record RegistrationWindowRequest(boolean open,
                                            LocalDateTime startsAt, LocalDateTime endsAt) {}
    public record ProfileUpdateRequest(@NotBlank @Size(max=150) String fullName,
                                       java.time.LocalDate dateOfBirth) {}
    public record TermRequest(@NotBlank @Size(max=30) String name,
                              @NotBlank @Size(max=20) String academicYear,
                              java.time.LocalDate startDate, java.time.LocalDate endDate) {}

    public record LoginResponse(String token, String email, Role role, String displayName, boolean mustChangePassword) {}
    public record ProfileView(Long id, String username, String email, String fullName, Role role,
                              String faculty, String major, String cohort, String accountStatus, String learningStatus, java.time.LocalDate dateOfBirth) {}
    public record ActivateRequest(@NotBlank String token, @Size(min=8, max=100) String newPassword) {}
    public record CreateAccountRequest(@Email @NotBlank String email, @NotBlank @Size(max=150) String fullName,
                                       @NotNull Role role, @Size(max=150) String faculty, @Size(max=150) String major) {}
    public record CreateClassRequest(@NotBlank @Size(max=100) String name, @NotNull Long courseId, @NotNull Long termId,
                                     @NotNull Long teacherId, @Size(max=100) String room,
                                     @Size(max=200) String schedule, @Min(1) @Max(1000) int capacity) {}
    public record UpdateCapacityRequest(@Min(1) @Max(1000) int capacity) {}
    public record UpdateClassRequest(@NotBlank @Size(max=100) String name,
                                     @NotNull Long teacherId,
                                     @Size(max=100) String room,
                                     @Size(max=200) String schedule,
                                     @Min(1) @Max(1000) int capacity,
                                     @NotNull ClassStatus status,
                                     Long courseId,
                                     Long termId) {
        // Backwards-compatible constructor for existing service tests.
        public UpdateClassRequest(String name, Long teacherId, String room, String schedule,
                                  int capacity, ClassStatus status, Long courseId) {
            this(name, teacherId, room, schedule, capacity, status, courseId, null);
        }
    }
    public record ClassView(Long id, String name, String courseCode, String courseName, String term,
                            String teacherName, Long teacherId, String room, String schedule, int capacity, int enrolled,
                            int remaining, ClassStatus status) {}
    public record RegistrationView(Long id, Long classId, String className, String courseCode, String courseName,
                                   LocalDateTime registeredAt, RegistrationStatus status) {}
    public record StudyScheduleItem(Long registrationId, Long classId, String className,
                                    String courseCode, String courseName, int credits,
                                    String term, String teacherName, String room, String schedule) {}
    public record StudySchedule(String currentTerm, java.util.List<StudyScheduleItem> classes) {}
    public record WaitlistView(Long id, Long classId, String className, String courseCode, String courseName,
                               LocalDateTime joinedAt, WaitlistStatus status, long position, String ineligibleReason, LocalDateTime updatedAt) {}
    public record ActionResult(String result, String message, Long classId, Long recordId, Long queuePosition) {}
    public record AdminSummary(long accounts, long classes, long activeRegistrations, long waitingStudents) {}
    public record AccountCreated(String message, String email, Role role) {}
    public record StudentInClass(Long studentId, String fullName, String email, String faculty, LocalDateTime registeredAt) {}
    public record ConversationView(Long id, String userEmail, String userName, String status, LocalDateTime updatedAt) {}
    public record CreateMessageRequest(@NotBlank @Size(max=5000) String content) {}
    public record MessageView(Long id, Long conversationId, String senderEmail, String senderRole, String content, LocalDateTime sentAt) {}
    public record DecisionView(Long id, Long classId, Long studentId, Long waitlistId, Long registrationId, LocalDateTime occurredAt, String result, String reason, String className, String courseCode, String courseName, String studentName, String studentEmail) {}
    public record AdminWaitlistView(Long id, Long classId, String className, Long studentId, String studentName, String studentEmail, LocalDateTime joinedAt, WaitlistStatus status, long position) {}
}
