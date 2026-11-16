package vn.edu.registration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.api.ApiDtos;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;

import java.time.LocalDate;
import java.util.Locale;

/** Mỗi hàng có transaction riêng. Không thay đổi tài khoản đã tồn tại. */
@Service
public class PeopleImportRowService {
    private final ActivationService activation;
    private final AccountRepository accounts;
    private final StudentRepository students;
    private final TeacherRepository teachers;

    public PeopleImportRowService(ActivationService activation, AccountRepository accounts,
                                  StudentRepository students, TeacherRepository teachers) {
        this.activation = activation;
        this.accounts = accounts;
        this.students = students;
        this.teachers = teachers;
    }

    public record ImportPerson(String username, String email, String fullName, String faculty,
                               String major, String cohort, String dateOfBirth, String role) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean importRow(ImportPerson data, Role targetRole) {
        if (targetRole != Role.STUDENT && targetRole != Role.TEACHER)
            throw new IllegalArgumentException("Vai trò nhập không hợp lệ.");
        String email = required(data.email(), "Email", 255).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Email không đúng định dạng.");
        String fullName = required(data.fullName(), "Họ tên", 150);
        String username = optional(data.username(), 40, "Mã người dùng");
        if (username != null && !username.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Mã người dùng chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        String faculty = optional(data.faculty(), 150, "Khoa");
        String major = optional(data.major(), 150, "Ngành/Chuyên môn");
        String cohort = optional(data.cohort(), 30, "Khóa");
        if (targetRole == Role.TEACHER && cohort != null)
            throw new IllegalArgumentException("Giảng viên không sử dụng cột Khóa.");
        String roleText = data.role() == null ? "" : data.role().trim().toUpperCase(Locale.ROOT);
        if (!roleText.isEmpty() && !roleText.equals(targetRole.name()) &&
                !(targetRole == Role.STUDENT && roleText.equals("SINH VIÊN")) &&
                !(targetRole == Role.TEACHER && roleText.equals("GIẢNG VIÊN")))
            throw new IllegalArgumentException("Vai trò trong tệp không khớp trang nhập.");
        LocalDate dateOfBirth = null;
        if (data.dateOfBirth() != null && !data.dateOfBirth().isBlank()) {
            try { dateOfBirth = LocalDate.parse(data.dateOfBirth().trim()); }
            catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalArgumentException("Ngày sinh cần có định dạng yyyy-MM-dd.");
            }
            if (dateOfBirth.isAfter(LocalDate.now()) || dateOfBirth.isBefore(LocalDate.of(1900, 1, 1)))
                throw new IllegalArgumentException("Ngày sinh không hợp lệ.");
        }
        var byEmail = accounts.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            if (byEmail.get().getRole() != targetRole)
                throw new IllegalArgumentException("Email đã thuộc tài khoản có vai trò khác.");
            if (username != null && byEmail.get().getUsername() != null &&
                    !byEmail.get().getUsername().equalsIgnoreCase(username))
                throw new IllegalArgumentException("Email đã tồn tại với mã người dùng khác.");
            return false;
        }
        if (username != null && accounts.findByUsernameIgnoreCase(username).isPresent())
            throw new IllegalArgumentException("Mã người dùng đã tồn tại ở tài khoản khác.");

        // Tạo PENDING giống nút Thêm tài khoản; không lấy mật khẩu chưa mã hóa từ Excel/CSV.
        activation.createAccount(new ApiDtos.CreateAccountRequest(email, fullName, targetRole, faculty, major));
        var account = accounts.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Không thể tìm lại tài khoản vừa tạo."));
        if (username != null) account.setUsername(username);
        if (targetRole == Role.STUDENT) {
            var person = students.findByAccount_EmailIgnoreCase(email)
                    .orElseThrow(() -> new IllegalStateException("Không thể tìm hồ sơ sinh viên."));
            person.setCohort(cohort);
            person.setDateOfBirth(dateOfBirth);
        } else {
            var person = teachers.findByAccount_EmailIgnoreCase(email)
                    .orElseThrow(() -> new IllegalStateException("Không thể tìm hồ sơ giảng viên."));
            person.setDateOfBirth(dateOfBirth);
        }
        return true;
    }

    private String required(String value, String name, int limit) {
        String result = optional(value, limit, name);
        if (result == null) throw new IllegalArgumentException("Thiếu " + name + ".");
        return result;
    }
    private String optional(String value, int max, String name) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(name + " dài quá " + max + " ký tự.");
        return result;
    }
}
