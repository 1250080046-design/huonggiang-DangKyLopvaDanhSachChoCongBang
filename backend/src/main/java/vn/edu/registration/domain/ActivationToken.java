package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter
@Entity @Table(name="TokenKichHoat")
public class ActivationToken {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaToken") private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaTaiKhoan", nullable=false) private Account account;
 @Column(name="TokenHash", nullable=false, unique=true, length=64) private String tokenHash;
 @Column(name="ThoiDiemTao", nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
 @Column(name="ThoiDiemHetHan", nullable=false) private LocalDateTime expiresAt;
 @Column(name="ThoiDiemSuDung") private LocalDateTime usedAt;
}
