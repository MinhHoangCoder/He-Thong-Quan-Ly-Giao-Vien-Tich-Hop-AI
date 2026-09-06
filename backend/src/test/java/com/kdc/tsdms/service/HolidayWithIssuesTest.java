package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Holiday;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.AttendanceRepository;
import com.kdc.tsdms.repository.HolidayRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * NÚT "BUỔI DẠY" HIỆN Ở DÒNG NÀO — {@code holidaysWithIssues}.
 *
 * <p>Luật của màn hình là: <b>có nút ⇔ mở ra là có nội dung</b>. Nút thừa thì người dùng bấm
 * vào và nhận một câu "không vướng gì" — khó chịu nhưng không mất gì. Nút THIẾU mới nguy: đó
 * là đường duy nhất vào chỗ dọn buổi dạy vướng kỳ nghỉ, ẩn nhầm là kỳ nghỉ đó không ai đụng
 * tới nữa, và job nền cứ thế ghi vắng cho giáo viên vào ngày trường đóng cửa.
 *
 * <p>Vì phép kiểm tra được viết lại bằng hai câu quét phẳng rồi đối chiếu bên Java (chứ không
 * JOIN trong SQL — xem lý do ở {@code HolidayRepository.sessionDaysInRange}), nó KHÔNG dùng
 * chung mã với {@code impact()}/{@code absences()} là hai hàm quyết định nội dung hộp thoại.
 * Hai bên lệch nhau lúc nào không biết, nên khóa lại ở đây.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HolidayWithIssuesTest {

    private static final int TRUONG_A = 5;
    private static final int TRUONG_B = 9;

    @Mock
    private HolidayRepository holidayRepo;

    @Mock
    private SchoolRepository schoolRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @Mock
    private AssignmentSlotRepository slotRepo;

    @Mock
    private AttendanceRepository attendanceRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private HolidayService service;

    @Test
    void khong_hoi_gi_thi_khong_cham_vao_db() {
        assertThat(service.holidaysWithIssues(List.of())).isEmpty();
        assertThat(service.holidaysWithIssues(null)).isEmpty();
    }

    @Test
    void chi_tra_ve_ky_nghi_that_su_con_viec() {
        Holiday coBuoiDay = holiday(1, "2026-04-30", "2026-05-01", null);
        Holiday saiTruong = holiday(2, "2026-09-02", "2026-09-02", TRUONG_A);
        Holiday chiConDongVang = holiday(3, "2026-06-01", "2026-06-01", null);
        Holiday sach = holiday(4, "2026-07-15", "2026-07-15", null);

        when(holidayRepo.findAllById(anyList())).thenReturn(List.of(coBuoiDay, saiTruong, chiConDongVang, sach));
        when(holidayRepo.sessionDaysInRange(any(), any()))
                .thenReturn(List.<Object[]>of(
                        ngay("2026-04-30", TRUONG_A),
                        // Kỳ nghỉ #2 chỉ của trường A, buổi này của trường B — không phải việc của nó.
                        ngay("2026-09-02", TRUONG_B)));
        when(holidayRepo.systemAbsenceDaysInRange(any(), any()))
                .thenReturn(List.<Object[]>of(ngay("2026-06-01", TRUONG_A)));

        List<Integer> ids = service.holidaysWithIssues(List.of(1, 2, 3, 4));

        // #3 là ca tinh tế nhất: buổi dạy đã bị hủy sạch nên câu đếm buổi không thấy gì, nhưng
        // hủy buổi KHÔNG xóa dòng vắng đã ghi — kỳ nghỉ đó vẫn còn việc phải dọn.
        assertThat(ids).containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void ky_nghi_toan_he_thong_nhan_ngay_cua_moi_truong() {
        Holiday toanHeThong = holiday(1, "2026-01-01", "2026-01-03", null);
        when(holidayRepo.findAllById(anyList())).thenReturn(List.of(toanHeThong));
        // Buổi không gắn ô thời khóa biểu nào (schoolId null) cũng phải được tính.
        when(holidayRepo.sessionDaysInRange(any(), any())).thenReturn(List.<Object[]>of(ngay("2026-01-02", null)));
        when(holidayRepo.systemAbsenceDaysInRange(any(), any())).thenReturn(List.of());

        assertThat(service.holidaysWithIssues(List.of(1))).containsExactly(1);
    }

    @Test
    void ngay_sat_ngoai_khoang_khong_tinh_la_con_viec() {
        Holiday hai_ngay = holiday(1, "2026-05-10", "2026-05-11", null);
        when(holidayRepo.findAllById(anyList())).thenReturn(List.of(hai_ngay));
        // Hai đầu của kỳ nghỉ là ĐÓNG: 09/05 và 12/05 đều nằm ngoài.
        when(holidayRepo.sessionDaysInRange(any(), any()))
                .thenReturn(List.<Object[]>of(ngay("2026-05-09", null), ngay("2026-05-12", null)));
        when(holidayRepo.systemAbsenceDaysInRange(any(), any())).thenReturn(List.of());

        assertThat(service.holidaysWithIssues(List.of(1))).isEmpty();
    }

    private static Holiday holiday(int id, String from, String to, Integer schoolId) {
        Holiday h = new Holiday();
        h.setId(id);
        h.setName("Kỳ nghỉ #" + id);
        h.setFromDate(LocalDate.parse(from));
        h.setToDate(LocalDate.parse(to));
        h.setKind("BREAK");
        h.setSchoolId(schoolId);
        return h;
    }

    /** Một dòng của truy vấn native: cột ngày về dưới dạng {@link Date} như driver JDBC trả. */
    private static Object[] ngay(String iso, Integer schoolId) {
        return new Object[] {Date.valueOf(iso), schoolId};
    }
}
