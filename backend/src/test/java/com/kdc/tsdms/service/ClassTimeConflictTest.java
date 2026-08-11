package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.entity.Teacher;
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
import org.springframework.http.HttpStatus;

/**
 * MỘT LỚP TRONG MỘT TIẾT CHỈ CÓ MỘT GIÁO VIÊN.
 *
 * <p>Luật cũ chỉ dò phía giáo viên ("người này có bận không") nên không ai chặn chiều ngược
 * lại: xếp ba giáo viên vào cùng một lớp cùng một tiết là hợp lệ, cả ba đều sinh buổi dạy và
 * đều được tính công — cùng một tiết học trả tiền ba lần. Đã xảy ra thật trên dữ liệu (lớp
 * 1A1 sáng thứ 2 có 3 giáo viên).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassTimeConflictTest {

    private static final int LOP = 27;
    private static final int GV_MOI = 11;
    private static final int GV_CU = 99;

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

    private static Period tiet(int so, String tu, String den) {
        Period p = new Period();
        p.setId(so);
        p.setPeriodNumber((short) so);
        p.setStartTime(LocalTime.parse(tu));
        p.setEndTime(LocalTime.parse(den));
        return p;
    }

    private final Period tietMoi = tiet(1, "07:00", "07:35");

    /** Ô lịch đã tồn tại của lớp: GV khác, cùng thứ, tiết trùng giờ, phiếu ACTIVE vô thời hạn. */
    private Assignment datChoSan(int teacherId, Period period, String status, LocalDate tu, LocalDate den) {
        AssignmentSlot slot = new AssignmentSlot();
        slot.setId(500);
        slot.setAssignmentId(484);
        slot.setTeacherId(teacherId);
        slot.setDayOfWeek("MON");
        slot.setPeriodId(period.getId());
        slot.setClassId(LOP);

        Assignment a = new Assignment();
        a.setId(484);
        a.setTeacherId(teacherId);
        a.setStatus(status);
        a.setStartDate(tu);
        a.setEndDate(den);

        when(slotRepo.findByClassIdAndDayOfWeekAndDeletedFalse(LOP, "MON")).thenReturn(List.of(slot));
        when(assignmentRepo.findByIdAndDeletedFalse(484)).thenReturn(Optional.of(a));
        when(periodRepo.findById(period.getId())).thenReturn(Optional.of(period));
        return a;
    }

    @BeforeEach
    void setUp() {
        SchoolClass c = new SchoolClass();
        c.setId(LOP);
        c.setName("1A1");
        when(classRepo.findById(LOP)).thenReturn(Optional.of(c));
        Teacher t = new Teacher();
        t.setId(GV_CU);
        t.setLastName("Nguyễn Văn");
        t.setFirstName("An");
        when(teacherRepo.findById(anyInt())).thenReturn(Optional.of(t));
        when(slotRepo.findByClassIdAndDayOfWeekAndDeletedFalse(any(), anyString()))
                .thenReturn(List.of());
    }

    private void kiemTra() {
        checker.checkClass(LOP, GV_MOI, "MON", tietMoi, LocalDate.of(2026, 8, 10), null, null);
    }

    @Test
    void lopDaCoGiaoVienKhacDayThiChan() {
        datChoSan(GV_CU, tiet(1, "07:00", "07:35"), "ACTIVE", LocalDate.of(2026, 8, 10), null);
        assertThatThrownBy(this::kiemTra)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("lớp 1A1")
                .hasMessageContaining("Nguyễn Văn An");
    }

    /** Tiết khác nhau nhưng giờ đè nhau (khung tiết do người nhập, vẫn phải bắt). */
    @Test
    void gioDeNhauDuKhacTietThiVanChan() {
        datChoSan(GV_CU, tiet(2, "07:20", "07:55"), "ACTIVE", LocalDate.of(2026, 8, 10), null);
        assertThatThrownBy(this::kiemTra).isInstanceOf(ApiException.class);
    }

    /** Phiếu CHỜ XÁC NHẬN vẫn giữ chỗ — đúng như luật phía giáo viên. */
    @Test
    void phieuChoXacNhanVanGiuCho() {
        datChoSan(GV_CU, tiet(1, "07:00", "07:35"), "PENDING", LocalDate.of(2026, 8, 10), null);
        assertThatThrownBy(this::kiemTra)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chờ giáo viên xác nhận");
    }

    /** Chính giáo viên đó dạy liền tiết cho cùng lớp là bình thường. */
    @Test
    void cungGiaoVienThiKhongChan() {
        datChoSan(GV_MOI, tiet(1, "07:00", "07:35"), "ACTIVE", LocalDate.of(2026, 8, 10), null);
        assertThatCode(this::kiemTra).doesNotThrowAnyException();
    }

    @Test
    void gioKhongDeNhauThiKhongChan() {
        datChoSan(GV_CU, tiet(2, "07:40", "08:15"), "ACTIVE", LocalDate.of(2026, 8, 10), null);
        assertThatCode(this::kiemTra).doesNotThrowAnyException();
    }

    @Test
    void giaiDoanKhongChongNhauThiKhongChan() {
        datChoSan(GV_CU, tiet(1, "07:00", "07:35"), "ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertThatCode(this::kiemTra).doesNotThrowAnyException();
    }

    /** Phiếu bị từ chối đã nhả chỗ. */
    @Test
    void phieuKhongGiuChoThiBoQua() {
        datChoSan(GV_CU, tiet(1, "07:00", "07:35"), "REJECTED", LocalDate.of(2026, 8, 10), null);
        assertThatCode(this::kiemTra).doesNotThrowAnyException();
    }

    /** Sửa lại chính phiếu đó thì không tự chặn mình. */
    @Test
    void boQuaChinhPhieuDangSua() {
        datChoSan(GV_CU, tiet(1, "07:00", "07:35"), "ACTIVE", LocalDate.of(2026, 8, 10), null);
        assertThatCode(() -> checker.checkClass(LOP, GV_MOI, "MON", tietMoi, LocalDate.of(2026, 8, 10), null, 484))
                .doesNotThrowAnyException();
    }

    /** Dữ liệu cũ chưa gắn lớp thì không có gì để so. */
    @Test
    void oLichKhongGanLopThiBoQua() {
        assertThatCode(() -> checker.checkClass(null, GV_MOI, "MON", tietMoi, LocalDate.of(2026, 8, 10), null, null))
                .doesNotThrowAnyException();
    }
}
