package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter
@Entity @Table(name="NhatKyQuyetDinh", indexes=@Index(name="IX_NhatKyQuyetDinh_Lop_ThoiDiem", columnList="MaLop,ThoiDiem"))
public class DecisionLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaNhatKy") private Long id;
 @Column(name="MaLop", nullable=false) private Long classId;
 @Column(name="MaSinhVien") private Long studentId;
 @Column(name="MaDSC") private Long waitlistId;
 @Column(name="MaDangKy") private Long registrationId;
 @Column(name="MaTaiKhoanThucHien") private Long actorAccountId;
 @Column(name="ThoiDiem", nullable=false) private LocalDateTime occurredAt = LocalDateTime.now();
 @Column(name="KetQua", nullable=false, length=50) private String result;
 @Column(name="LyDo", length=500) private String reason;
}
