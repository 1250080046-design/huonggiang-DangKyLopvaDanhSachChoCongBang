package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter
@Entity @Table(name="DangKy", uniqueConstraints=@UniqueConstraint(name="UQ_DangKy_SinhVien_Lop", columnNames={"MaSinhVien","MaLop"}), indexes=@Index(name="IX_DangKy_Lop_TrangThai", columnList="MaLop,TrangThai"))
public class Registration {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaDangKy") private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaSinhVien", nullable=false) private Student student;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaLop", nullable=false) private ClassSection classSection;
 @Column(name="ThoiGianDangKy", nullable=false) private LocalDateTime registeredAt = LocalDateTime.now();
 @Enumerated(EnumType.STRING) @Column(name="TrangThai", nullable=false, length=20) private RegistrationStatus status = RegistrationStatus.ACTIVE;
}
