package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.School;
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
 * CẢNH BÁO chạy giữa hai trường quá gấp.
 *
 * <p>Khác hai luật chặn cứng bên cạnh: đây là điều CÓ THỂ xảy ra thật, chỉ là rủi ro — hai
 * trường có thể cách nhau 200m hoặc 15km, dữ liệu hệ thống không biết. Nên luật này chỉ nêu
 * số liệu để người xếp lịch quyết, và tuyệt đối không được đụng tới lịch trong CÙNG một
 * trường (nơi các tiết liền nhau vốn cách nhau 0 phút).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelGapWarningTest {

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
    private Optional<String> canhBao() {
        return checker.travelWarning(
                GV, TRUONG_B, "MON", tiet(2, "07:35", "08:10"), LocalDate.of(2026, 8, 10), null, null);
    }

    /** Cảnh báo nêu ĐIỀU GIÁO VIÊN ĐANG VƯỚNG: tiết mấy, trường nào, thứ mấy. */
    @Test
    void chayNgaySangTruongKhacThiCanhBao() {
        daCoBuoi(TRUONG_A, tiet(1, "07:00", "07:35")); // tan 07:35, vao 07:35 → 0 phut
        assertThat(canhBao())
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("tiết 1")
                .contains("TH Dư Hàng")
                .contains("thứ 2");
    }

    /** Ranh giới: đúng 30 phút là đủ, không cảnh báo. */
    @Test
    void dungBaMuoiPhutThiKhongCanhBao() {
        daCoBuoi(TRUONG_A, tiet(1, "06:30", "07:05")); // tan 07:05 → vao 07:35 = 30 phut
        assertThat(canhBao()).isEmpty();
    }

    @Test
    void haiMuoiChinPhutThiVanCanhBao() {
        daCoBuoi(TRUONG_A, tiet(1, "06:31", "07:06")); // 29 phut
        assertThat(canhBao()).isPresent();
    }

    /** CÙNG trường thì dạy liền tiết là bình thường — không được cảnh báo. */
    @Test
    void cungTruongThiKhongCanhBao() {
        daCoBuoi(TRUONG_B, tiet(1, "07:00", "07:35"));
        assertThat(canhBao()).isEmpty();
    }

    /** Giờ đè hẳn lên nhau là việc của check() chặn cứng, không phải của cảnh báo này. */
    @Test
    void gioDeNhauThiKhongPhaiViecCuaCanhBao() {
        daCoBuoi(TRUONG_A, tiet(1, "07:20", "07:55"));
        assertThat(canhBao()).isEmpty();
    }

    @Test
    void cachXaThiKhongCanhBao() {
        daCoBuoi(TRUONG_A, tiet(1, "05:00", "05:35")); // cach 120 phut
        assertThat(canhBao()).isEmpty();
    }

    /** Cảnh báo cả chiều ngược: buổi đã có diễn ra SAU buổi đang xét. */
    @Test
    void buoiDaCoNamSauThiVanCanhBao() {
        daCoBuoi(TRUONG_A, tiet(3, "08:20", "08:55")); // buoi moi tan 08:10 → cach 10 phut
        assertThat(canhBao())
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("tiết 3")
                .contains("TH Dư Hàng");
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
        assertThat(canhBao()).isEmpty();
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

        assertThat(canhBao()).isEmpty();
    }

    @Test
    void boQuaChinhPhieuDangSua() {
        daCoBuoi(TRUONG_A, tiet(1, "07:00", "07:35"));
        assertThat(checker.travelWarning(
                        GV, TRUONG_B, "MON", tiet(2, "07:35", "08:10"), LocalDate.of(2026, 8, 10), null, 480))
                .isEmpty();
    }
}
