package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter
@Entity @Table(name="TinNhan", indexes=@Index(name="IX_TinNhan_Conversation_Time", columnList="MaCuocTroChuyen,ThoiDiemGui"))
public class ChatMessage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaTinNhan") private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaCuocTroChuyen", nullable=false) private Conversation conversation;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaTaiKhoanGui", nullable=false) private Account sender;
 @Column(name="NoiDung", nullable=false, columnDefinition="nvarchar(max)") private String content;
 @Column(name="ThoiDiemGui", nullable=false) private LocalDateTime sentAt = LocalDateTime.now();
 @Column(name="DaDoc", nullable=false) private boolean read = false;
 @Column(name="ThoiDiemDoc") private LocalDateTime readAt;
}
