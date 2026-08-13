package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Chặn cứng việc chạy giữa hai trường quá gấp.
 *
 * <p>{@code check()} chỉ bắt được giờ đè hẳn lên nhau; tan trường này rồi vào ngay trường kia
 * thì lịch nhìn vẫn "hợp lệ" mà thực tế không ai làm được. Luật này lấp đúng khe đó, và tuyệt
 * đối không được đụng tới lịch trong CÙNG một trường (nơi các tiết liền nhau vốn cách nhau 0 phút).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelGapBlockTest {

    private static final int GV = 11;
    private static final int TRUONG_A = 4;
    private static final int TRUONG_B = 5;

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private AssignmentSlotRepository slotRepo;

    @Mock
    private PeriodRepository periodRepo;

    @Mock
    private SchoolRepository schoolRepo;

    @Mock
    private SchoolClassRepository classRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @InjectMocks
    private TeacherTimeConflictChecker checker;

    private static Period tiet(int id, String tu, String den) {
        Period p = new Period();
        p.setId(id);
        p.setPeriodNumber((short) id);
        p.setStartTime(LocalTime.parse(tu));
        p.setEndTime(LocalTime.parse(den));
        return p;
    }

    /** Buổi đã có sẵn của giáo viên trong ngày, ở trường {@code schoolId}. */
    private void daCoBuoi(int schoolId, Period period) {
        AssignmentSlot slot = new AssignmentSlot();
        slot.setId(500);
        slot.setAssignmentId(480);
        slot.setTeacherId(GV);
        slot.setDayOfWeek("MON");
        slot.setPeriodId(period.getId());

        Assignment a = new Assignment();
        a.setId(480);
        a.setTeacherId(GV);
        a.setSchoolId(schoolId);
        a.setStatus("ACTIVE");
        a.setStartDate(LocalDate.of(2026, 8, 10));

        when(slotRepo.findByTeacherIdAndDayOfWeekAndDeletedFalse(GV, "MON")).thenReturn(List.of(slot));
        when(assignmentRepo.findByIdAndDeletedFalse(480)).thenReturn(Optional.of(a));
        when(periodRepo.findById(period.getId())).thenReturn(Optional.of(period));
    }

    @BeforeEach
    void setUp() {
        School a = new School();
        a.setId(TRUONG_A);
        a.setName("TH Dư Hàng");
        School b = new School();
        b.setId(TRUONG_B);
        b.setName("TH Lê Văn Tám");
        when(schoolRepo.findById(TRUONG_A)).thenReturn(Optional.of(a));
        when(schoolRepo.findById(TRUONG_B)).thenReturn(Optional.of(b));
        when(slotRepo.findByTeacherIdAndDayOfWeekAndDeletedFalse(anyInt(), anyString()))
                .thenReturn(List.of());
    }

    /** Tiết mới đang xét: 07:35–08:10 tại TRƯỜNG B. */
    private void soat() {
        checker.checkTravelGap(GV, TRUONG_B, "MON", tiet(2, "07:35", "08:10"), LocalDate.of(2026, 8, 10), null, null);
    }

    /** Lời từ chối nêu điều giáo viên đang vướng (tiết mấy, trường nào, thứ mấy) rồi mới tới luật. */
    @Test
    void chayNgaySangTruongKhacThiChan() {
        daCoBuoi(TRUONG_A, tiet(1, "07:00", "07:35")); // tan 07:35, vao 07:35 → 0 phut
        assertThatThrownBy(this::soat)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tiết 1")
                .hasMessageContaining("TH Dư Hàng")
                .hasMessageContaining("thứ 2")
                .hasMessageContaining("60 phút");
    }

    /** Ranh giới: đúng 60 phút là đủ, cho qua. */
    @Test
    void dungSauMuoiPhutThiChoQua() {
        daCoBuoi(TRUONG_A, tiet(1, "06:00", "06:35")); // tan 06:35 → vao 07:35 = 60 phut
        assertThatCode(this::soat).doesNotThrowAnyException();
    }

    @Test
    void namMuoiChinPhutThiVanChan() {
        daCoBuoi(TRUONG_A, tiet(1, "06:01", "06:36")); // 59 phut
        assertThatThrownBy(this::soat).isInstanceOf(ApiException.class);
    }

    /** CÙNG trường thì dạy liền tiết là bình thường — không được chặn. */
    @Test
    void cungTruongThiChoQua() {
        daCoBuoi(TRUONG_B, tiet(1, "07:00", "07:35"));
        assertThatCode(this::soat).doesNotThrowAnyException();
    }

    /** Giờ đè hẳn lên nhau là việc của check(), không phải của luật này. */
    @Test
    void gioDeNhauThiKhongPhaiViecCuaLuatNay() {
        daCoBuoi(TRUONG_A, tiet(1, "07:20", "07:55"));
        assertThatCode(this::soat).doesNotThrowAnyException();
    }

    @Test
    void cachXaThiChoQua() {
        daCoBuoi(TRUONG_A, tiet(1, "05:00", "05:35")); // cach 120 phut
        assertThatCode(this::soat).doesNotThrowAnyException();
    }

    /** Chặn cả chiều ngược: buổi đã có diễn ra SAU buổi đang xét. */
    @Test
    void buoiDaCoNamSauThiVanChan() {
        daCoBuoi(TRUONG_A, tiet(3, "08:20", "08:55")); // buoi moi tan 08:10 → cach 10 phut
        assertThatThrownBy(this::soat)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tiết 3")
                .hasMessageContaining("TH Dư Hàng");
    }

    @Test
    void phieuKhongGiuChoThiBoQua() {
        daCoBuoi(TRUONG_A, tiet(1, "07:00", "07:35"));
        when(assignmentRepo.findByIdAndDeletedFalse(480)).thenAnswer(inv -> {
            Assignment a = new Assignment();
            a.setId(480);
            a.setSchoolId(TRUONG_A);
            a.setStatus("REJECTED");
            a.setStartDate(LocalDate.of(2026, 8, 10));
            return Optional.of(a);
        });
        assertThatCode(this::soat).doesNotThrowAnyException();
    }

    @Test
    void giaiDoanKhongChongNhauThiBoQua() {
        AssignmentSlot slot = new AssignmentSlot();
        slot.setId(500);
        slot.setAssignmentId(480);
        slot.setTeacherId(GV);
        slot.setDayOfWeek("MON");
        slot.setPeriodId(1);
        Assignment a = new Assignment();
        a.setId(480);
        a.setSchoolId(TRUONG_A);
        a.setStatus("ACTIVE");
        a.setStartDate(LocalDate.of(2026, 1, 1));
        a.setEndDate(LocalDate.of(2026, 6, 30));
        when(slotRepo.findByTeacherIdAndDayOfWeekAndDeletedFalse(GV, "MON")).thenReturn(List.of(slot));
        when(assignmentRepo.findByIdAndDeletedFalse(480)).thenReturn(Optional.of(a));
        when(periodRepo.findById(1)).thenReturn(Optional.of(tiet(1, "07:00", "07:35")));

        assertThatCode(this::soat).doesNotThrowAnyException();
    }

    @Test
    void boQuaChinhPhieuDangSua() {
        daCoBuoi(TRUONG_A, tiet(1, "07:00", "07:35"));
        assertThatCode(() -> checker.checkTravelGap(
                        GV, TRUONG_B, "MON", tiet(2, "07:35", "08:10"), LocalDate.of(2026, 8, 10), null, 480))
                .doesNotThrowAnyException();
    }
}
