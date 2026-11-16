package vn.edu.registration.domain;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
@Getter @Setter
@Entity @Table(name="GiangVien")
public class Teacher {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaGiangVien") private Long id;
 @OneToOne(optional=false) @JoinColumn(name="MaTaiKhoan", nullable=false, unique=true) private Account account;
 @Column(name="HoTen", nullable=false, length=150) private String fullName;
 @Column(name="Khoa", length=150) private String faculty;
 @Column(name="BoMon", length=150) private String major;
 @Column(name="NgaySinh") private java.time.LocalDate dateOfBirth;
}
