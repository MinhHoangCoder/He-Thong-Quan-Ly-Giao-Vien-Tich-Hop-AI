package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.entity.Employee;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.EmployeeRepository;
import com.kdc.tsdms.repository.EmployeeScheduleRepository;
import com.kdc.tsdms.repository.PartTimeShiftRequestRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ hồ sơ Nhân viên trung tâm. Chưa có API xóa — service này chốt sẵn luật cho ngày
 * có: ai làm feature xóa nhân viên thì gọi {@link #delete}, đừng tự viết.
 *
 * <p>Điểm khác Teacher: phần lớn bảng con của Employee là DẤU VẾT AI-LÀM-GÌ
 * ({@code Assignment.AssignedByEmployeeId}, {@code Feedback.HandledByEmployeeId},
 * {@code PartTimeShiftRequest.ReviewedByEmployeeId}) — cố tình KHÔNG chặn theo chúng: xóa mềm
 * giữ nguyên dòng nên tên người phân công/người duyệt vẫn tra ra được. Chỉ chặn thứ còn là
 * NGHĨA VỤ TƯƠNG LAI: ca làm đã xếp chưa tới ngày, và đơn xin ca đang treo chờ duyệt.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeScheduleRepository scheduleRepo;
    private final PartTimeShiftRequestRepository shiftRequestRepo;

    public EmployeeService(
            EmployeeRepository employeeRepo,
            EmployeeScheduleRepository scheduleRepo,
            PartTimeShiftRequestRepository shiftRequestRepo) {
        this.employeeRepo = employeeRepo;
        this.scheduleRepo = scheduleRepo;
        this.shiftRequestRepo = shiftRequestRepo;
    }

    /** Xóa mềm hồ sơ nhân viên — chặn khi còn ca phải đứng hoặc đơn xin ca chưa ai xử. */
    @Transactional
    public void delete(Integer id) {
        Employee e = employeeRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên id=" + id));
        DeleteGuard.of("nhân viên " + fullName(e))
                .blockIf(
                        scheduleRepo.countByEmployeeIdAndWorkDateGreaterThanEqualAndStatusAndDeletedFalse(
                                id, BusinessTime.today(), "SCHEDULED"),
                        "ca làm sắp tới")
                .blockIf(
                        shiftRequestRepo.countByEmployeeIdAndStatusAndDeletedFalse(id, "PENDING"),
                        "đơn xin ca chờ duyệt")
                .check();
        Integer nguoiXoa = SecurityUtils.currentUserId();
        e.setDeleted(true);
        e.setDeletedAt(Instant.now());
        e.setDeletedBy(nguoiXoa);
        e.setUpdatedAt(Instant.now());
        e.setUpdatedBy(nguoiXoa);
        employeeRepo.save(e);
    }

    /** "Họ và tên đệm" + "Tên" — đúng thứ tự đọc tiếng Việt. */
    private static String fullName(Employee e) {
        return ((e.getLastName() == null ? "" : e.getLastName()) + " "
                        + (e.getFirstName() == null ? "" : e.getFirstName()))
                .trim();
    }
}
