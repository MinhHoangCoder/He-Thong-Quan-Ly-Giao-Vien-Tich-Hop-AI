package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.TeacherSubject;
import com.kdc.tsdms.entity.TeacherSubjectId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng nối TeacherSubject (khóa kép TeacherId + SubjectId). */
public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, TeacherSubjectId> {

    /** Số giáo viên đang được gắn dạy một môn — dùng làm rào chắn khi xóa môn học. */
    long countBySubjectId(Integer subjectId);
}
