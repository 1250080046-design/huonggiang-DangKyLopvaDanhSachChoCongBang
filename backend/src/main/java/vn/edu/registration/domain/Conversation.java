package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter
@Entity @Table(name="CuocTroChuyen", indexes=@Index(name="IX_CuocTroChuyen_User_Status", columnList="MaTaiKhoanNguoiDung,TrangThai"))
public class Conversation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaCuocTroChuyen") private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaTaiKhoanNguoiDung", nullable=false) private Account userAccount;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="MaTaiKhoanQuanTri") private Account adminAccount;
 @Column(name="ThoiDiemTao", nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
 @Column(name="ThoiDiemCapNhat", nullable=false) private LocalDateTime updatedAt = LocalDateTime.now();
 @Enumerated(EnumType.STRING) @Column(name="TrangThai", nullable=false, length=20) private ConversationStatus status = ConversationStatus.OPEN;
}
