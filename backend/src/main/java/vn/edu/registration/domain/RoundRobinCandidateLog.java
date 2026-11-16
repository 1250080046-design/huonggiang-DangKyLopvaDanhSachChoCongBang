package vn.edu.registration.domain;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
@Getter @Setter @Entity @Table(name="ChiTietXetRoundRobin")
public class RoundRobinCandidateLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaChiTiet") private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="MaLuotXet",nullable=false) private RoundRobinRun run;
 @Column(name="ThuTuXet",nullable=false) private int sequence;
 @Column(name="MaSinhVien",nullable=false) private Long studentId;
 @Column(name="MaDSC") private Long waitlistId;
 @Column(name="KetQua",nullable=false,length=60) private String result;
 @Column(name="LyDo",length=500) private String reason;
}
