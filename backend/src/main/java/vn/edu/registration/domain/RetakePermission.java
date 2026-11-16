package vn.edu.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name="QuyenHocLai", uniqueConstraints=@UniqueConstraint(columnNames={"MaSinhVien","MaHocPhan"}))
public class RetakePermission {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="MaQuyen") private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="MaSinhVien") private Student student;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="MaHocPhan") private Course course;
}
