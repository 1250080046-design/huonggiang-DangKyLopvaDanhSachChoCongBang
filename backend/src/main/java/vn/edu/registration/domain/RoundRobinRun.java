package vn.edu.registration.domain;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter @Entity @Table(name="LuotXetRoundRobin")
public class RoundRobinRun {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaLuotXet") private Long id;
 @Column(name="MaLop",nullable=false) private Long classId;
 @Column(name="ThoiDiem",nullable=false) private LocalDateTime occurredAt = LocalDateTime.now();
 @Column(name="NguyenNhan",nullable=false,length=100) private String trigger;
 @Column(name="SiSoTruoc",nullable=false) private int enrolledBefore;
 @Column(name="SiSoSau",nullable=false) private int enrolledAfter;
 @Column(name="SucChua",nullable=false) private int capacity;
 @Column(name="ConTroTruoc") private Long cursorBefore;
 @Column(name="ConTroSau") private Long cursorAfter;
 @Column(name="SoNguoiDuocCap",nullable=false) private int promotedCount;
 @Column(name="KetQua",nullable=false,length=60) private String outcome;
}
