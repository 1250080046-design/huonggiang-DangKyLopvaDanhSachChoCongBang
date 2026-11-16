package vn.edu.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
@Entity @Table(name = "TaiKhoan", uniqueConstraints = @UniqueConstraint(name = "UQ_TaiKhoan_Email", columnNames = "Email"))
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "MaTaiKhoan")
    private Long id;
    @Column(name = "Email", nullable = false, length = 255)
    private String email;
    @Column(name = "TenDangNhap", length = 40, unique = true)
    private String username;
    @Column(name = "MatKhauHash", length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(name = "VaiTro", nullable = false, length = 20)
    private Role role;
    @Enumerated(EnumType.STRING) @Column(name = "TrangThai", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.PENDING;
    @Column(name = "DaKichHoat", nullable = false)
    private boolean activated = false;
    @Column(name = "BuocDoiMatKhau", nullable = false)
    private boolean mustChangePassword = true;
    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "NgayKichHoat")
    private LocalDateTime activatedAt;
    @Column(name = "LanDangNhapCuoi")
    private LocalDateTime lastLoginAt;
}
