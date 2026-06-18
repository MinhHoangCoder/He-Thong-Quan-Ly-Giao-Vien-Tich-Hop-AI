package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Bảng nối lớp ⇄ học sinh (nhiều-nhiều). Khóa kép (ClassId, StudentId). */
@Entity
@Table(name = "ClassEnrollment")
@IdClass(ClassEnrollmentId.class)
@Getter
@Setter
public class ClassEnrollment {

    @Id
    @Column(name = "ClassId")
    private Integer classId;

    @Id
    @Column(name = "StudentId")
    private Integer studentId;

    /** DB tự điền SYSUTCDATETIME() lúc ghi danh → chỉ đọc. */
    @Column(name = "EnrolledAt", insertable = false, updatable = false)
    private Instant enrolledAt;

    public ClassEnrollment() {}

    public ClassEnrollment(Integer classId, Integer studentId) {
        this.classId = classId;
        this.studentId = studentId;
    }
}
