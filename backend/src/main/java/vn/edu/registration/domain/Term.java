package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDate;
@Getter @Setter
@Entity @Table(name="HocKy", uniqueConstraints=@UniqueConstraint(name="UQ_HocKy_Ten_Nam", columnNames={"TenHocKy","NamHoc"}))
public class Term {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaHocKy") private Long id;
 @Column(name="TenHocKy", nullable=false, length=30) private String name;
 @Column(name="NamHoc", nullable=false, length=20) private String academicYear;
 @Column(name="NgayBatDau") private LocalDate startDate;
 @Column(name="NgayKetThuc") private LocalDate endDate;
 @Column(name="DangMoDangKy", nullable=false) private boolean registrationOpen = false;
 @Column(name="MoDangKyTu") private java.time.LocalDateTime registrationStartsAt;
 @Column(name="DongDangKyLuc") private java.time.LocalDateTime registrationEndsAt;
}
