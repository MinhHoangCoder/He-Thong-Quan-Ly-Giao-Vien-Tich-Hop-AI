package com.kdc.tsdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Bảng nối giáo viên ⇄ môn dạy được (nhiều-nhiều). Khóa kép (TeacherId, SubjectId). */
@Entity
@Table(name = "TeacherSubject")
@IdClass(TeacherSubjectId.class)
@Getter
@Setter
public class TeacherSubject {

    @Id
    @Column(name = "TeacherId")
    private Integer teacherId;

    @Id
    @Column(name = "SubjectId")
    private Integer subjectId;

    /** Mức thành thạo 1..5 (TINYINT) — dữ liệu đầu vào cho AI gợi ý ghép GV. */
    @Column(name = "ProficiencyLevel")
    private Short proficiencyLevel;

    public TeacherSubject() {}

    public TeacherSubject(Integer teacherId, Integer subjectId) {
        this.teacherId = teacherId;
        this.subjectId = subjectId;
    }
}
