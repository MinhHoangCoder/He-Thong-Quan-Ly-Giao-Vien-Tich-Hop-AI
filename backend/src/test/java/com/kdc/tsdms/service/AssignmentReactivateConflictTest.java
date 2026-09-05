package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;

/**
 * Khôi phục phân công từ Thùng rác phải soát trùng CẢ HAI CHIỀU — KHÔNG cần DB.
 *
 * <p>Luật "một lớp một tiết chỉ một giáo viên" được vá ở luồng tạo/sửa và ép duyệt, nhưng
 * {@code reactivate} thì lọt: lúc phiếu nằm trong thùng rác, ô lịch của nó bị xóa mềm nên khung
 * giờ được nhả ra và lớp có thể đã giao cho người khác; khôi phục mà chỉ hỏi "giáo viên này có
 * bận không" thì xong việc lớp có hai giáo viên cùng một tiết, cả hai đều sinh buổi dạy và đều
 * được tính công. Nút Khôi phục có sẵn trên giao diện nên đây là đường đi thật, không phải chỉ
 * gọi API tay.
 *
 * <p>{@code ClassTimeConflictTest} đã phủ bản thân luật trùng lớp, nhưng phủ nó ĐỨNG RIÊNG —
 * không có gì kiểm luật đó có được NỐI vào từng luồng ghi hay không. Test này giữ đúng mối nối
 * đó cho luồng khôi phục.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentReactivateConflictTest {

    private static final int ASSIGNMENT_ID = 11;
    private static final int TEACHER_ID = 3;
    private static final int CLASS_ID = 77;
    private static final int PERIOD_ID = 5;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private AssignmentSlotRepository slotRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private SchoolRepository schoolRepo;

    @Mock
    private SchoolClassRepository classRepo;

    @Mock
    private SubjectRepository subjectRepo;

    @Mock
    private PeriodRepository periodRepo;

    /**
     * Từ V39 {@code reactivate} phải hỏi lịch nghỉ (xem test cuối file) — thiếu mock này thì
     * Mockito tiêm null và luồng khôi phục nổ NullPointerException trước khi kiểm được gì.
     */
    @Mock
    private HolidayRepository holidayRepo;

    @Mock
    private AssignmentApprovalService approvalService;

    @Mock
    private TeacherTimeConflictChecker conflictChecker;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private AssignmentService service;

    /** Phiếu ĐÃ HỦY (điều kiện để được khôi phục), có đúng một ô lịch gắn lớp. */
    private Period givenCancelledAssignmentWithOneSlot() {
        Assignment a = new Assignment();
        a.setId(ASSIGNMENT_ID);
        a.setTeacherId(TEACHER_ID);
        a.setStatus(AssignmentStatus.CANCELLED);
        a.setStartDate(START);
        a.setEndDate(END);
        when(assignmentRepo.findByIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(Optional.of(a));

        AssignmentSlot slot = new AssignmentSlot();
        slot.setAssignmentId(ASSIGNMENT_ID);
        slot.setTeacherId(TEACHER_ID);
        slot.setDayOfWeek("MON");
        slot.setPeriodId(PERIOD_ID);
        slot.setClassId(CLASS_ID);
        when(slotRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID)).thenReturn(List.of(slot));

        Period p = new Period();
        p.setId(PERIOD_ID);
        p.setPeriodNumber((short) 1);
        p.setStartTime(LocalTime.of(7, 30));
        p.setEndTime(LocalTime.of(8, 15));
        when(periodRepo.findById(PERIOD_ID)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void khoiPhuc_soatCaPhiaLop_chuKhongChiPhiaGiaoVien() {
        Period p = givenCancelledAssignmentWithOneSlot();
        // Lớp đã bị giáo viên khác chiếm trong lúc phiếu nằm ở thùng rác.
        doThrow(new ApiException(HttpStatus.CONFLICT, "Lớp 6A đã có giáo viên dạy khung giờ này"))
                .when(conflictChecker)
                .checkClass(eq(CLASS_ID), anyInt(), any(), any(), any(), any(), anyInt());

        // Đỏ ở dòng này = reactivate KHÔNG gọi checkClass nên không có gì chặn.
        assertThatThrownBy(() -> service.reactivate(ASSIGNMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // Và đúng tham số: bỏ qua chính phiếu đang khôi phục, so đúng lớp/thứ/tiết/giai đoạn.
        verify(conflictChecker).checkClass(CLASS_ID, TEACHER_ID, "MON", p, START, END, ASSIGNMENT_ID);
    }

    @Test
    void khoiPhuc_vanGiuNguyenLuatTrungGioCuaChinhGiaoVien() {
        Period p = givenCancelledAssignmentWithOneSlot();

        service.reactivate(ASSIGNMENT_ID);

        // Luật cũ không được rơi mất khi thêm luật mới.
        verify(conflictChecker).check(TEACHER_ID, "MON", p, START, END, ASSIGNMENT_ID);
    }

    /**
     * BỎ HỦY KHÔNG ĐƯỢC HỒI SINH BUỔI RƠI VÀO NGÀY NGHỈ.
     *
     * <p>Buổi ngày lễ bị {@code HolidayService} đánh CANCELLED, cùng trạng thái với buổi bị hủy
     * theo phiếu — nên vòng lặp khôi phục nhìn hai loại đó y hệt nhau. Bật lại buổi ngày lễ là
     * dựng lại đúng buổi "ma" mà V29 sinh ra để chặn: {@code AttendanceSweepService} quét buổi đã
     * qua mà không ai chấm rồi tự ghi VẮNG, trừ thẳng vào lương của người không hề được gọi đi dạy.
     */
    @Test
    void boHuy_khongHoiSinhBuoiRoiVaoNgayNghi() {
        givenCancelledAssignmentWithOneSlot();
        Schedule trongKyNghi = buoiDaHuy(LocalDateTime.of(2026, 12, 25, 7, 30));
        Schedule ngayThuong = buoiDaHuy(LocalDateTime.of(2026, 12, 28, 7, 30));
        when(scheduleRepo.findByAssignmentIdAndDeletedFalse(ASSIGNMENT_ID))
                .thenReturn(List.of(trongKyNghi, ngayThuong));

        Holiday nghiTet = new Holiday();
        nghiTet.setFromDate(LocalDate.of(2026, 12, 24));
        nghiTet.setToDate(LocalDate.of(2026, 12, 26));
        when(holidayRepo.findOverlapping(any(), any())).thenReturn(List.of(nghiTet));

        service.reactivate(ASSIGNMENT_ID);

        assertThat(trongKyNghi.getStatus()).isEqualTo("CANCELLED");
        assertThat(ngayThuong.getStatus()).isEqualTo("PENDING");
    }

    /** Buổi đã bị hủy theo phiếu — thứ mà "Bỏ hủy" phải cân nhắc có bật lại hay không. */
    private static Schedule buoiDaHuy(LocalDateTime batDau) {
        Schedule s = new Schedule();
        s.setStatus("CANCELLED");
        s.setStartTime(batDau);
        s.setEndTime(batDau.plusMinutes(45));
        return s;
    }
}
