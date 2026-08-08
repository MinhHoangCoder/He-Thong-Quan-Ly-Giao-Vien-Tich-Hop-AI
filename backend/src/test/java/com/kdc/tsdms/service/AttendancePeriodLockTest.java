package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.repository.PayrollRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Khóa kỳ chấm công theo trạng thái bảng lương.
 *
 * <p>Chấm công là đầu vào tính tiền: sửa ngược một dòng sau khi lương đã chốt/đã trả làm
 * lệch sổ mà không ai hay. Test này giữ đúng ranh giới DRAFT (còn sửa được) với
 * FINALIZED/PAID (khóa).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendancePeriodLockTest {

    @Mock
    private PayrollRepository payrollRepo;

    @InjectMocks
    private AttendanceService service;

    private static final LocalDate THANG_5 = LocalDate.of(2026, 5, 20);

    private void payrollStatus(String status) {
        Payroll p = new Payroll();
        p.setStatus(status);
        when(payrollRepo.findByTeacherIdAndPeriodYearAndPeriodMonth(any(), any(), any()))
                .thenReturn(Optional.of(p));
    }

    @Test
    void luongDaChotThiKhoa() {
        payrollStatus("FINALIZED");
        assertThat(service.isPeriodLocked(7, THANG_5)).isTrue();
    }

    @Test
    void luongDaTraThiKhoa() {
        payrollStatus("PAID");
        assertThat(service.isPeriodLocked(7, THANG_5)).isTrue();
    }

    @Test
    void luongConNhapThiVanSuaDuoc() {
        payrollStatus("DRAFT");
        assertThat(service.isPeriodLocked(7, THANG_5)).isFalse();
    }

    @Test
    void chuaCoBangLuongThiVanSuaDuoc() {
        when(payrollRepo.findByTeacherIdAndPeriodYearAndPeriodMonth(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThat(service.isPeriodLocked(7, THANG_5)).isFalse();
    }

    @Test
    void thieuDuLieuThiKhongKhoa() {
        assertThat(service.isPeriodLocked(null, THANG_5)).isFalse();
        assertThat(service.isPeriodLocked(7, null)).isFalse();
    }
}
