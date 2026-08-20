package com.kdc.tsdms.service;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.EmployeeRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Chi nhánh. Hiện API mới có GET (BranchController) — service này ra đời TRƯỚC khi
 * có nút xóa, để chốt sẵn luật toàn vẹn: chi nhánh là gốc của gần nửa hệ thống (trường, giáo
 * viên, nhân viên, bài giảng, hợp đồng dịch vụ đều treo vào BranchId). Ai làm feature xóa chi
 * nhánh thì gọi {@link #delete} thay vì tự viết — luật RESTRICT đã nằm sẵn ở đây.
 */
@Service
public class BranchService {

    private final BranchRepository branchRepo;
    private final SchoolRepository schoolRepo;
    private final TeacherRepository teacherRepo;
    private final EmployeeRepository employeeRepo;
    private final LessonRepository lessonRepo;
    private final ServiceContractRepository serviceContractRepo;

    public BranchService(
            BranchRepository branchRepo,
            SchoolRepository schoolRepo,
            TeacherRepository teacherRepo,
            EmployeeRepository employeeRepo,
            LessonRepository lessonRepo,
            ServiceContractRepository serviceContractRepo) {
        this.branchRepo = branchRepo;
        this.schoolRepo = schoolRepo;
        this.teacherRepo = teacherRepo;
        this.employeeRepo = employeeRepo;
        this.lessonRepo = lessonRepo;
        this.serviceContractRepo = serviceContractRepo;
    }

    /**
     * Xóa mềm chi nhánh — luật RESTRICT trên CẢ 5 bảng con còn sống. Chi nhánh chỉ xóa được
     * khi đã rỗng thật sự; còn bất cứ gì thì kể đủ tên trong một lần báo.
     */
    @Transactional
    public void delete(Integer id) {
        Branch b = branchRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy chi nhánh id=" + id));
        DeleteGuard.of("chi nhánh " + b.getName())
                .blockIf(schoolRepo.countByBranchIdAndDeletedFalse(id), "trường đang hợp tác")
                .blockIf(teacherRepo.countByBranchIdAndDeletedFalse(id), "giáo viên")
                .blockIf(employeeRepo.countByBranchIdAndDeletedFalse(id), "nhân viên")
                .blockIf(lessonRepo.countByBranchIdAndDeletedFalse(id), "bài giảng")
                .blockIf(serviceContractRepo.countByBranchIdAndDeletedFalse(id), "hợp đồng dịch vụ")
                .check();
        Integer nguoiXoa = SecurityUtils.currentUserId();
        b.setDeleted(true);
        b.setDeletedAt(Instant.now());
        b.setDeletedBy(nguoiXoa);
        b.setUpdatedAt(Instant.now());
        b.setUpdatedBy(nguoiXoa);
        branchRepo.save(b);
    }
}
