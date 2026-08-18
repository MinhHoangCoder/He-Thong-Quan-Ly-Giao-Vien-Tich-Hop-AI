package com.kdc.tsdms.service;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.entity.Student;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.ClassEnrollmentRepository;
import com.kdc.tsdms.repository.StudentRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ hồ sơ Học sinh (của trường khách hàng). Chưa có API xóa — service này chốt sẵn
 * luật: ai làm feature xóa học sinh thì gọi {@link #delete}, đừng tự viết.
 *
 * <p>{@code ClassEnrollment} không có cờ xóa mềm — còn dòng nghĩa là CÒN ĐANG HỌC lớp đó
 * (đúng cách {@code SchoolClassService} đếm sĩ số). Vậy luật là: rút khỏi lớp trước, xóa hồ
 * sơ sau — không có chuyện học sinh biến mất mà sĩ số lớp vẫn đếm đủ.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepo;
    private final ClassEnrollmentRepository enrollmentRepo;

    public StudentService(StudentRepository studentRepo, ClassEnrollmentRepository enrollmentRepo) {
        this.studentRepo = studentRepo;
        this.enrollmentRepo = enrollmentRepo;
    }

    /** Xóa mềm hồ sơ học sinh — chặn khi còn ghi danh ở bất kỳ lớp nào. */
    @Transactional
    public void delete(Integer id) {
        Student st = studentRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh id=" + id));
        DeleteGuard.of("học sinh " + fullName(st))
                .blockIf(enrollmentRepo.countByStudentId(id), "lượt ghi danh lớp đang học")
                .check();

        Integer nguoiXoa = SecurityUtils.currentUserId();
        st.setDeleted(true);
        st.setDeletedAt(Instant.now());
        st.setDeletedBy(nguoiXoa);
        st.setUpdatedAt(Instant.now());
        st.setUpdatedBy(nguoiXoa);
        studentRepo.save(st);
    }

    /** "Họ và tên đệm" + "Tên" — đúng thứ tự đọc tiếng Việt. */
    private static String fullName(Student st) {
        return ((st.getLastName() == null ? "" : st.getLastName()) + " "
                        + (st.getFirstName() == null ? "" : st.getFirstName()))
                .trim();
    }
}
