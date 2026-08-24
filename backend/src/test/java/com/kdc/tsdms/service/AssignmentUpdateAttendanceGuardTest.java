package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * Sửa phân công XÓA CỨNG toàn bộ buổi rồi sinh lại — phải chốt bằng DỮ LIỆU, không chỉ bằng
 * TRẠNG THÁI. KHÔNG cần DB.
 *
 * <p>{@code Attendance.ScheduleId} là khóa ngoại và schema không khai {@code ON DELETE} ở đâu
 * cả, nên còn một dòng chấm công là câu {@code DELETE FROM Schedule} đâm thẳng vào ràng buộc và
 * bung ra lỗi 500 SQL thô — không phải một câu tiếng Việt người dùng đọc được.
 *
 * <p>Hôm nay chuyện đó chưa xảy ra được: chỉ phiếu chưa xác nhận mới sửa được, buổi của phiếu
 * như vậy chưa APPROVED, mà cả ba đường sinh chấm công (tự check-in, job quét, kỳ nghỉ) đều chỉ
 * chạm buổi APPROVED. Nhưng đó là một suy luận bắc cầu qua ba file khác nhau, không có gì giữ
 * nó lại: thêm một trạng thái vào {@code AssignmentStatus.isEditable}, hoặc thêm một đường ghi
 * Attendance mới, là nó gãy trong im lặng. Test này giữ hàng rào ở dạng KHÔNG phụ thuộc ai nhớ
 * gì — hỏi thẳng số dòng chấm công.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentUpdateAttendanceGuardTest {

    private static final int ASSIGNMENT_ID = 88;

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

    private void phieuSuaDuoc() {
        Assignment a = new Assignment();
        a.setId(ASSIGNMENT_ID);
        a.setStatus(AssignmentStatus.PENDING);
        when(assignmentRepo.findByIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(Optional.of(a));
    }

    @Test
    void conChamCongBamVao_thiChanVoiLoi409_khongDeCauDeleteDamVaoKhoaNgoai() {
        phieuSuaDuoc();
        when(attendanceRepo.demChamCongTheoPhanCong(ASSIGNMENT_ID)).thenReturn(4L);

        assertThatThrownBy(() -> service.update(ASSIGNMENT_ID, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("4 bản ghi chấm công")
                .hasMessageContaining("hủy phiếu này rồi tạo phiếu mới");

        // Điểm chính: dừng TRƯỚC mọi câu xóa cứng, không phải sau khi đã dọn nửa vời.
        verify(scheduleRepo, never()).deleteByAssignmentId(ASSIGNMENT_ID);
        verify(scheduleRepo, never()).deleteStatusLogsByAssignmentId(ASSIGNMENT_ID);
        verify(slotRepo, never()).deleteByAssignmentId(ASSIGNMENT_ID);
    }

    /**
     * Rào chắn phải đứng NGAY SAU cửa trạng thái, trước mọi bước kiểm tra khác: phiếu đã có
     * hiệu lực thì lý do từ chối là "phiếu đã chạy", không phải "có chấm công" — hai câu khác
     * nhau dẫn người dùng đi hai hướng khác nhau.
     */
    @Test
    void phieuDaCoHieuLuc_thiBaoLyDoTrangThai_chuKhongPhaiLyDoChamCong() {
        Assignment a = new Assignment();
        a.setId(ASSIGNMENT_ID);
        a.setStatus(AssignmentStatus.ACTIVE);
        when(assignmentRepo.findByIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.update(ASSIGNMENT_ID, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cần được hủy và tạo lại");

        verify(attendanceRepo, never()).demChamCongTheoPhanCong(ASSIGNMENT_ID);
    }
}
