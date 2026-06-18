package com.kdc.tsdms.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa kép (ClassId, StudentId) cho bảng nối ClassEnrollment. */
public class ClassEnrollmentId implements Serializable {

    private Integer classId;
    private Integer studentId;

    public ClassEnrollmentId() {}

    public ClassEnrollmentId(Integer classId, Integer studentId) {
        this.classId = classId;
        this.studentId = studentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassEnrollmentId that)) return false;
        return Objects.equals(classId, that.classId) && Objects.equals(studentId, that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classId, studentId);
    }
}
