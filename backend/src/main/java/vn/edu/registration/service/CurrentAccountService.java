package vn.edu.registration.service;
import vn.edu.registration.domain.*; import vn.edu.registration.repository.*;
import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Service; import jakarta.persistence.EntityNotFoundException;
@Service
public class CurrentAccountService {
 private final AccountRepository accounts; private final StudentRepository students; private final TeacherRepository teachers;
 public CurrentAccountService(AccountRepository accounts, StudentRepository students, TeacherRepository teachers) { this.accounts=accounts; this.students=students; this.teachers=teachers; }
 public String email() { var auth=SecurityContextHolder.getContext().getAuthentication(); if(auth==null || auth.getName()==null) throw new IllegalArgumentException("Vui lòng đăng nhập lại."); return auth.getName(); }
 public Account account() { return accounts.findByEmailIgnoreCase(email()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy tài khoản.")); }
 public Student student() { return students.findByAccount_EmailIgnoreCase(email()).orElseThrow(()->new IllegalArgumentException("Tài khoản chưa có hồ sơ sinh viên.")); }
 public Teacher teacher() { return teachers.findByAccount_EmailIgnoreCase(email()).orElseThrow(()->new IllegalArgumentException("Tài khoản chưa có hồ sơ giảng viên.")); }
}
