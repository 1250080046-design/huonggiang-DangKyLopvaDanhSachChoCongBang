package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Getter @Setter
@Entity @Table(name="HocPhan")
public class Course {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaHocPhan") private Long id;
 @Column(name="MaHocPhanCode", nullable=false, unique=true, length=30) private String code;
 @Column(name="TenHocPhan", nullable=false, length=200) private String name;
 @Column(name="SoTinChi", nullable=false) private Integer credits;
 @Column(name="NamDaoTao") private Integer year;
 @Column(name="HocKyDuKien") private Integer semester;
 @Column(name="NganhDaoTao", length=150) private String major;
}
