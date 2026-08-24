package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Đưa phân công vào Thùng rác KHÔNG được xóa buổi đã dạy — KHÔNG cần DB.
 *
 * <p>Trước Đợt 5 có một mâu thuẫn nội bộ nằm cạnh nhau trong cùng một file: {@code cancel} chỉ
 * hủy buổi TƯƠNG LAI và cố ý giữ buổi quá khứ để không mất chấm công/lương, rồi {@code
 * softDelete} chạy ngay sau đó gắn cờ xóa lên TẤT CẢ buổi — xóa sạch sự thận trọng vừa rồi.
 *
 * <p>Dòng {@code Attendance} thì vẫn còn (Attendance không có xóa mềm, cũng không nằm trong
 * cascade), nên kết quả là chấm công MỒ CÔI: tiền vẫn được trả nhưng buổi dạy chứng minh nó đã
 * biến khỏi mọi màn hình. {@code PayrollRepository.demChamCongMoCoi} có đếm ca này, nhưng chỉ
 * ở mức CẢNH BÁO nên kế toán vẫn chốt kỳ được — cảnh báo không phải là rào chắn.
 *
 * <p>Test dựng phiếu ở trạng thái CANCELLED/REJECTED để {@code softDelete} không gọi vòng qua
 * {@code cancel}: mục tiêu ở đây là luật của riêng softDelete, không phải luật của cancel.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentTrashKeepsPastTest {

    private static final int ASSIGNMENT_ID = 42;

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private AssignmentSlotRepository slotRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private AttendanceRepository attendanceRepo;

    @InjectMocks
    private AssignmentService service;

    /** Buổi cách "bây giờ" một khoảng đủ xa để không phụ thuộc thời điểm chạy test. */
    private Schedule buoi(long id, LocalDateTime batDau) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setAssignmentId(ASSIGNMENT_ID);
        s.setStartTime(batDau);
        s.setEndTime(batDau.plusMinutes(45));
        s.setStatus("APPROVED");
        return s;
    }

    private Assignment phieu(String trangThai, LocalDateTime xacNhanLuc) {
        Assignment a = new Assignment();
        a.setId(ASSIGNMENT_ID);
        a.setStatus(trangThai);
        a.setConfirmedAt(xacNhanLuc);
        when(assignmentRepo.findByIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(Optional.of(a));
        return a;
    }

    /**
     * Trọng tâm: phiếu ĐÃ TỪNG CHẠY thì buổi đã tới giờ là bằng chứng chấm công — ở lại, dù
     * phiếu đã nằm trong thùng rác. Buổi tương lai thì không có gì để chứng minh, xóa theo.
     */
    @Test
    void phieuDaTungChay_giuNguyenBuoiDaDay_chiXoaBuoiTuongLai() {
        LocalDateTime bayGio = BusinessTime.now();
        Assignment a = phieu(AssignmentStatus.CANCELLED, BusinessTime.now().minusMonths(2));
        Schedule daDay = buoi(1, bayGio.minusDays(7));
        Schedule sapToi = buoi(2, bayGio.plusDays(7));
        when(scheduleRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of(daDay, sapToi));
        when(slotRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of());

        service.softDelete(ASSIGNMENT_ID);

        assertThat(a.isDeleted()).isTrue();
        assertThat(daDay.isDeleted())
                .as("buổi đã dạy phải ở lại làm bằng chứng cho dòng chấm công đang tính tiền")
                .isFalse();
        assertThat(sapToi.isDeleted()).isTrue();
        verify(scheduleRepo).save(sapToi);
        verify(scheduleRepo, never()).save(daDay);
    }

    /**
     * Phiếu CHƯA TỪNG được xác nhận thì không buổi nào có hiệu lực (chưa ai xác nhận thì chưa
     * ai đi dạy, cũng chưa có chấm công) — xóa sạch, kể cả buổi quá khứ, để thùng rác không
     * còn sót ô lịch của một phiếu chưa từng tồn tại về mặt nghiệp vụ. Đây đúng là luật mà
     * {@code cancel} dùng qua cờ {@code neverConfirmed}.
     */
    @Test
    void phieuChuaTungXacNhan_thiXoaCaBuoiQuaKhu() {
        LocalDateTime bayGio = BusinessTime.now();
        phieu(AssignmentStatus.REJECTED, null);
        Schedule quaKhu = buoi(1, bayGio.minusDays(7));
        Schedule tuongLai = buoi(2, bayGio.plusDays(7));
        when(scheduleRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of(quaKhu, tuongLai));
        when(slotRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of());

        service.softDelete(ASSIGNMENT_ID);

        assertThat(quaKhu.isDeleted()).isTrue();
        assertThat(tuongLai.isDeleted()).isTrue();
    }

    /**
     * Ô thời khóa biểu là MẪU LẶP TUẦN chứ không phải bằng chứng của buổi nào, nên phiếu vào
     * thùng rác là mẫu phải biến khỏi thời khóa biểu — cascade đầy đủ, không có ngoại lệ nào.
     */
    @Test
    void oThoiKhoaBieu_luonBiXoaTheoDuPhieuDaTungChay() {
        phieu(AssignmentStatus.CANCELLED, BusinessTime.now().minusMonths(2));
        AssignmentSlot slot = new AssignmentSlot();
        slot.setId(5);
        slot.setAssignmentId(ASSIGNMENT_ID);
        when(slotRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of(slot));
        when(scheduleRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of());

        service.softDelete(ASSIGNMENT_ID);

        assertThat(slot.isDeleted()).isTrue();
        verify(slotRepo).save(slot);
    }
}
