package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Certificate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Certificate — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface CertificateRepository extends JpaRepository<Certificate, Integer> {
    /** Danh sách chứng chỉ của GV — hiển thị chi tiết. */
    List<Certificate> findByTeacherIdAndDeletedFalse(Integer teacherId);

    /** Lấy 1 chứng chỉ thuộc đúng GV — tránh sửa/xóa nhầm chứng chỉ của người khác. */
    Optional<Certificate> findByIdAndTeacherIdAndDeletedFalse(Integer id, Integer teacherId);

    List<Certificate> findByTeacherId(Integer teacherId);
    
}
