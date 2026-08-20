package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Student — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface StudentRepository extends JpaRepository<Student, Integer> {

    /** Học sinh còn sống của một trường — chặn xóa trường khi còn hồ sơ học sinh. */
    long countBySchoolIdAndDeletedFalse(Integer schoolId);
}
