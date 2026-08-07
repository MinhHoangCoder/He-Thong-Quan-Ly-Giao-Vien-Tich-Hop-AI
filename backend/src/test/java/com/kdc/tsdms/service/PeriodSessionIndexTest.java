package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.repository.PeriodRepository;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test cho PeriodSessionIndex — quy đổi số tiết trong NGÀY sang số tiết trong BUỔI.
 *
 * <p>Điểm cần khóa: số tiết sáng KHÔNG phải hằng số 5. Tiểu học có 5 sáng + 5 chiều (tiết 10
 * = "Chiều · Tiết 5"), THCS có 5 sáng + 4 chiều, và trường nào lệch khung thì vẫn phải ra
 * đúng — trước đây frontend trừ cứng 5 nên trường lệch là hiển thị sai.
 */
@ExtendWith(MockitoExtension.class)
class PeriodSessionIndexTest {

    @Mock
    private PeriodRepository periodRepo;

    private static Period period(int schoolId, int number, String session) {
        Period p = new Period();
        p.setSchoolId(schoolId);
        p.setPeriodNumber((short) number);
        p.setSessionType(session);
        p.setStartTime(LocalTime.of(7, 0));
        p.setEndTime(LocalTime.of(7, 35));
        return p;
    }

    @Test
    void tietSangGiuNguyenSoTiet() {
        PeriodSessionIndex index = new PeriodSessionIndex(periodRepo);

        assertThat(index.of(period(4, 1, "MORNING"))).isEqualTo((short) 1);
        assertThat(index.of(period(4, 5, "MORNING"))).isEqualTo((short) 5);
    }

    @Test
    void tietChieuDanhLaiTu1TheoSoTietSangCuaTruong() {
        when(periodRepo.countBySchoolIdAndSessionTypeAndDeletedFalse(4, "MORNING"))
                .thenReturn(5);
        PeriodSessionIndex index = new PeriodSessionIndex(periodRepo);

        // Tiểu học: tiết 6 -> "Chiều · Tiết 1", tiết 10 -> "Chiều · Tiết 5" (tiết mới thêm).
        assertThat(index.of(period(4, 6, "AFTERNOON"))).isEqualTo((short) 1);
        assertThat(index.of(period(4, 10, "AFTERNOON"))).isEqualTo((short) 5);
    }

    @Test
    void truongLechKhungVanRaDungSoTiet() {
        // Trường chỉ có 4 tiết sáng -> tiết 5 phải là "Chiều · Tiết 1", KHÔNG phải trừ cứng 5.
        when(periodRepo.countBySchoolIdAndSessionTypeAndDeletedFalse(9, "MORNING"))
                .thenReturn(4);
        PeriodSessionIndex index = new PeriodSessionIndex(periodRepo);

        assertThat(index.of(period(9, 5, "AFTERNOON"))).isEqualTo((short) 1);
        assertThat(index.of(period(9, 8, "AFTERNOON"))).isEqualTo((short) 4);
    }

    @Test
    void demSoTietSangChiHoiDbMotLanChoMoiTruong() {
        when(periodRepo.countBySchoolIdAndSessionTypeAndDeletedFalse(4, "MORNING"))
                .thenReturn(5);
        PeriodSessionIndex index = new PeriodSessionIndex(periodRepo);

        index.of(period(4, 6, "AFTERNOON"));
        index.of(period(4, 7, "AFTERNOON"));
        index.of(period(4, 10, "AFTERNOON"));

        verify(periodRepo, times(1)).countBySchoolIdAndSessionTypeAndDeletedFalse(4, "MORNING");
    }

    @Test
    void thieuDuLieuThiTraNull() {
        PeriodSessionIndex index = new PeriodSessionIndex(periodRepo);

        assertThat(index.of(null)).isNull();
        assertThat(index.of(new Period())).isNull();
    }
}
