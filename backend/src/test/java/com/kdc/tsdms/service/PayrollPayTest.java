package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.entity.PayrollChangeLog;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.PayRateRepository;
import com.kdc.tsdms.repository.PayrollChangeLogRepository;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * XÁC NHẬN ĐÃ TRẢ LƯƠNG (PayrollService.pay, Flyway V37).
 *
 * <p>Trước V37 trạng thái {@code PAID} là trạng thái CHẾT: nó có trong ràng buộc của bảng, có
 * trong danh sách trạng thái giáo viên được xem, và {@code assertReopenable} từ chối mở lại
 * phiếu PAID — nhưng không có đường code nào ĐẶT được nó. Kế toán chi tiền xong không có nút
 * nào để ghi nhận, nên trên hệ thống "đã chốt" và "đã trả" là một.
 *
 * <p>Hệ quả không chỉ là thiếu một nút: rào chắn "phiếu đã trả thì không mở lại được" là rào
 * duy nhất bảo vệ sổ sách sau khi tiền ra khỏi quỹ, mà rào đó không bao giờ chạy vì không
 * phiếu nào tới được trạng thái PAID.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollPayTest {

    private static final int PAYROLL_ID = 5;
    private static final int TEACHER_ID = 3;
    private static final short YEAR = 2026;
    private static final short MONTH = 8;

    @Mock
    private PayrollRepository payrollRepo;

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private PayRateRepository payRateRepo;

    @Mock
    private ContractRepository contractRepo;

    @Mock
    private PayrollChangeLogRepository changeLogRepo;

    @Mock
    private AppUserRepository userRepo;

    @Mock
    private DisplayNameResolver displayNameResolver;

    @Mock
    private HolidayService holidayService;

    @Mock
    private EntityManager em;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PayrollService service;

    @Test
    void phieu_da_chot_thi_danh_dau_da_tra_va_ghi_nhat_ky() {
        Payroll p = payroll("FINALIZED");
        stub(p);

        service.pay(PAYROLL_ID);

        assertThat(p.getStatus()).isEqualTo("PAID");

        ArgumentCaptor<PayrollChangeLog> log = ArgumentCaptor.forClass(PayrollChangeLog.class);
        verify(changeLogRepo).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("PAY");
        assertThat(log.getValue().getStatusBefore()).isEqualTo("FINALIZED");
        assertThat(log.getValue().getStatusAfter()).isEqualTo("PAID");
    }

    @Test
    void phieu_dang_nhap_thi_khong_nhay_thang_sang_da_tra() {
        // Bỏ qua bước chốt là bỏ qua cảnh báo ngày nghỉ và bước khóa chấm công — hai thứ duy
        // nhất chặn việc trả tiền theo một con số sai.
        Payroll p = payroll("DRAFT");
        stub(p);

        assertThatThrownBy(() -> service.pay(PAYROLL_ID))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chốt phiếu trước")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(changeLogRepo, never()).save(any());
    }

    @Test
    void bam_hai_lan_thi_bao_da_tra_roi_chu_khong_ghi_them_nhat_ky() {
        Payroll p = payroll("PAID");
        stub(p);

        assertThatThrownBy(() -> service.pay(PAYROLL_ID))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã được đánh dấu");
        verify(changeLogRepo, never()).save(any());
    }

    @Test
    void tra_ca_ky_chi_dung_toi_phieu_da_chot() {
        Payroll daChot = payroll("FINALIZED");
        Payroll conNhap = payroll("DRAFT");
        conNhap.setId(6);
        when(payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(YEAR, MONTH))
                .thenReturn(List.of(daChot, conNhap));
        stub(daChot);

        int paid = service.payPeriod(YEAR, MONTH);

        assertThat(paid).isEqualTo(1);
        assertThat(conNhap.getStatus()).isEqualTo("DRAFT"); // không bị đụng tới
    }

    @Test
    void ky_khong_co_phieu_nao_da_chot_thi_bao_ro_chu_khong_im_lang_tra_ve_0() {
        when(payrollRepo.findByPeriodYearAndPeriodMonthOrderByTeacherId(YEAR, MONTH))
                .thenReturn(List.of(payroll("DRAFT")));

        assertThatThrownBy(() -> service.payPeriod(YEAR, MONTH))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không có phiếu nào ở trạng thái đã chốt");
    }

    /* ─────────────────── helpers ─────────────────── */

    private Payroll payroll(String status) {
        Payroll p = new Payroll();
        p.setId(PAYROLL_ID);
        p.setTeacherId(TEACHER_ID);
        p.setPeriodYear(YEAR);
        p.setPeriodMonth(MONTH);
        p.setStatus(status);
        p.setNetAmount(new BigDecimal("3250000"));
        return p;
    }

    private void stub(Payroll p) {
        when(payrollRepo.findById(p.getId())).thenReturn(Optional.of(p));
        Teacher t = new Teacher();
        t.setId(TEACHER_ID);
        t.setFirstName("An");
        t.setLastName("Nguyễn Văn");
        when(teacherRepo.findById(TEACHER_ID)).thenReturn(Optional.of(t));
    }
}
