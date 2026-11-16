package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter
@Entity @Table(name="DanhSachCho", uniqueConstraints=@UniqueConstraint(name="UQ_DanhSachCho_SinhVien_Lop", columnNames={"MaSinhVien","MaLop"}), indexes=@Index(name="IX_DanhSachCho_RoundRobin", columnList="MaLop,TrangThai,MaSinhVien"))
public class WaitlistEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaDSC") private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaSinhVien", nullable=false) private Student student;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaLop", nullable=false) private ClassSection classSection;
 @Column(name="ThoiDiemThamGia", nullable=false) private LocalDateTime joinedAt = LocalDateTime.now();
 @Enumerated(EnumType.STRING) @Column(name="TrangThai", nullable=false, length=20) private WaitlistStatus status = WaitlistStatus.WAITING;
 @Column(name="LyDoKhongDuDieuKien", length=500) private String ineligibleReason;
 @Column(name="ThoiDiemCapNhat") private LocalDateTime updatedAt = LocalDateTime.now();
}
