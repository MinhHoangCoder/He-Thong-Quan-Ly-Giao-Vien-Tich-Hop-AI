package com.kdc.tsdms.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa kép (TeacherId, SubjectId) cho bảng nối TeacherSubject. */
public class TeacherSubjectId implements Serializable {

    private Integer teacherId;
    private Integer subjectId;

    public TeacherSubjectId() {}

    public TeacherSubjectId(Integer teacherId, Integer subjectId) {
        this.teacherId = teacherId;
        this.subjectId = subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeacherSubjectId that)) return false;
        return Objects.equals(teacherId, that.teacherId) && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teacherId, subjectId);
    }
}
