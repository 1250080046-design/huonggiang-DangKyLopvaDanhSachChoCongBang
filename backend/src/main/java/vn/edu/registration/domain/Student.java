package vn.edu.registration.domain;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
@Getter @Setter
@Entity @Table(name="SinhVien")
public class Student {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaSinhVien") private Long id;
 @OneToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="MaTaiKhoan", nullable=false, unique=true) private Account account;
 @Column(name="HoTen", nullable=false, length=150) private String fullName;
 @Column(name="Khoa", length=150) private String faculty;
 @Column(name="Nganh", length=150) private String major;
 @Column(name="NgaySinh") private java.time.LocalDate dateOfBirth;
 @Column(name="KhoaHoc", length=30) private String cohort;
}
