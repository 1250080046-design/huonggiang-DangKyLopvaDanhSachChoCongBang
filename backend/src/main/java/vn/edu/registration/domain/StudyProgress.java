package vn.edu.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Academic progression is separate from the immutable admission cohort. */
@Getter @Setter
@Entity @Table(name="TienDoHocTap")
public class StudyProgress {
    public enum Status { ON_TRACK, DELAYED, SUSPENDED, RETURNED }
    @Id @Column(name="MaSinhVien") private Long studentId;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @MapsId
    @JoinColumn(name="MaSinhVien") private Student student;
    @Enumerated(EnumType.STRING) @Column(name="TrangThai", nullable=false, length=20)
    private Status status = Status.ON_TRACK;
    @Column(name="NamDaoTaoThucTe") private Integer effectiveYear;
    @Column(name="GhiChu", length=500) private String note;
    @Column(name="CapNhatLuc", nullable=false) private java.time.LocalDateTime updatedAt;
}
