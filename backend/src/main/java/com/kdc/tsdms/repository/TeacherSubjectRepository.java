package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.TeacherSubject;
import com.kdc.tsdms.entity.TeacherSubjectId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng nối TeacherSubject (khóa kép TeacherId + SubjectId). */
public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, TeacherSubjectId> {

    /**
     * Xóa vĩnh viễn mọi liên kết GV ⇄ môn của 1 môn học — dùng khi hard-delete
     * Subject. TeacherSubject chỉ là cờ "dạy được môn nào" (không phải dữ liệu
     * lịch sử như Assignment) nên xóa cùng môn học là an toàn.
     */
    void deleteBySubjectId(Integer subjectId);
}
