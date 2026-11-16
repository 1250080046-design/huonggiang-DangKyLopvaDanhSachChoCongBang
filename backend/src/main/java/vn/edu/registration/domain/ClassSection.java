package vn.edu.registration.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Getter @Setter
@Entity @Table(name="LopHoc", indexes=@Index(name="IX_LopHoc_GiangVien", columnList="MaGiangVien"))
public class ClassSection {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaLop") private Long id;
 @Column(name="TenLop", nullable=false, length=100) private String name;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaHocKy", nullable=false) private Term term;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaHocPhan", nullable=false) private Course course;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="MaGiangVien", nullable=false) private Teacher teacher;
 @Column(name="PhongHoc", length=100) private String room;
 @Column(name="LichHoc", length=200) private String schedule;
 @Column(name="SucChua", nullable=false) private Integer capacity;
 @Column(name="SoLuongDaDangKy", nullable=false) private Integer enrolled = 0;
 /** Persistent student-level Round Robin cursor (per class). */
 @Column(name="SinhVienRRGanNhat") private Long lastServedStudentId;
 @Enumerated(EnumType.STRING) @Column(name="TrangThai", nullable=false, length=20) private ClassStatus status = ClassStatus.OPEN;
}
