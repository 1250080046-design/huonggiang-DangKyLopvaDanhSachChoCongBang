package vn.edu.registration.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.registration.domain.*;
import vn.edu.registration.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;


/** One transaction per CSV row: an invalid row cannot roll back previously imported users. */
@Service
public class CsvImportRowService {
    private final AccountRepository accounts;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final PasswordEncoder passwords;
    public CsvImportRowService(AccountRepository accounts, StudentRepository students, TeacherRepository teachers, PasswordEncoder passwords) {
        this.accounts=accounts; this.students=students; this.teachers=teachers; this.passwords=passwords;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean importRow(String[] c) {
        String username=c[0].trim(), password=c[1], roleName=c[2].trim().toUpperCase(),
               fullName=c[3].trim(), dob=c[4].trim(), email=c[5].trim(), faculty=c[6].trim(), major=c[7].trim(), cohort=c[8].trim();
        boolean isStudentCode = username.matches("[0-9]{10}") || username.matches("SV[0-9]{6}");
        boolean isTeacherCode = username.matches("GV[0-9]{3}");
        if ((!isStudentCode && !isTeacherCode) || password.length()<12 || fullName.isBlank()
                || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw new IllegalArgumentException("Thiếu hoặc sai định dạng dữ liệu");
        Role role = Role.valueOf(roleName);
        if ((role==Role.STUDENT && !isStudentCode) || (role==Role.TEACHER && !isTeacherCode) || role==Role.ADMIN)
            throw new IllegalArgumentException("Role không khớp mã người dùng");
        if (role==Role.STUDENT && username.matches("[0-9]{10}")) {
            String expectedCohort="K"+username.substring(0,2);
            int expectedYear=2000+Integer.parseInt(username.substring(0,2))-18;
            if(!username.substring(3,6).equals("001")
                    || !username.substring(2,3).equals(Integer.toString(expectedYear%10))
                    || !cohort.equalsIgnoreCase(expectedCohort)
                    || !email.equalsIgnoreCase(username+"@sv.hcm.edu.vn")
                    || !dob.startsWith(expectedYear+"-"))
                throw new IllegalArgumentException("Mã sinh viên, khóa, năm sinh hoặc email không khớp");
        }
        LocalDate birth = dob.isBlank()?null:LocalDate.parse(dob);
        var existing = accounts.findByUsernameIgnoreCase(username);
        if(existing.isPresent()) {
            if (existing.get().getRole()!=role) throw new IllegalArgumentException("Vai trò tài khoản đã tồn tại không khớp");
            return false; // Do not overwrite a changed password or personal data.
        }
        if (accounts.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("Email đã tồn tại");
        Account a=new Account(); a.setUsername(username); a.setEmail(email); a.setRole(role);
        a.setPasswordHash(passwords.encode(password)); a.setActivated(true); a.setStatus(AccountStatus.ACTIVE);
        a.setActivatedAt(LocalDateTime.now()); a.setMustChangePassword(false);
        accounts.save(a);
        if(role==Role.STUDENT) {
            Student s=new Student(); s.setAccount(a); s.setFullName(fullName); s.setFaculty(faculty);
            s.setMajor(major); s.setCohort(cohort); s.setDateOfBirth(birth); students.save(s);
        } else {
            Teacher t=new Teacher(); t.setAccount(a); t.setFullName(fullName); t.setFaculty(faculty);
            t.setMajor(major); t.setDateOfBirth(birth); teachers.save(t);
        }
        return true;
    }
}
