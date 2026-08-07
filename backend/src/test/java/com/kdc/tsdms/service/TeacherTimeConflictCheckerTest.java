package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.Period;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho luật "hai khung tiết có đè giờ nhau không".
 *
 * <p>Vì sao cần khóa: từ V22 khung tiết bỏ thời gian chuyển tiết — mỗi buổi chỉ ra chơi 1 lần
 * sau tiết 2, các tiết còn lại NỐI LIỀN nhau (tiểu học 16:00–16:35 rồi 16:35–17:10). Nếu luật
 * so giờ dùng {@code <=} thay vì so CHẶT thì hai tiết liền kề sẽ bị coi là trùng và mọi phân
 * công đều bị chặn nhầm 409. Test này giữ đúng ranh giới đó.
 */
class TeacherTimeConflictCheckerTest {

    private static Period period(String start, String end) {
        Period p = new Period();
        p.setStartTime(LocalTime.parse(start));
        p.setEndTime(LocalTime.parse(end));
        return p;
    }

    @Test
    void haiTietLienKeKhongTinhLaTrungGio() {
        Period truoc = period("16:00", "16:35");
        Period sau = period("16:35", "17:10");

        assertThat(TeacherTimeConflictChecker.timeOverlaps(truoc, sau)).isFalse();
        assertThat(TeacherTimeConflictChecker.timeOverlaps(sau, truoc)).isFalse();
    }

    @Test
    void giaoNhauDuMotPhutVanLaTrungGio() {
        Period a = period("16:00", "16:35");
        Period b = period("16:34", "17:10");

        assertThat(TeacherTimeConflictChecker.timeOverlaps(a, b)).isTrue();
        assertThat(TeacherTimeConflictChecker.timeOverlaps(b, a)).isTrue();
    }

    @Test
    void tietDaiOmTronTietNganLaTrungGio() {
        // Trùng CHÉO TRƯỜNG: tiết 45' của THCS ôm trọn tiết 35' của tiểu học.
        Period tieuHoc = period("14:00", "14:35");
        Period thcs = period("14:00", "14:45");

        assertThat(TeacherTimeConflictChecker.timeOverlaps(tieuHoc, thcs)).isTrue();
    }

    @Test
    void cachNhauQuaGioRaChoiThiKhongTrung() {
        Period truocRaChoi = period("14:45", "15:30");
        Period sauRaChoi = period("15:40", "16:25");

        assertThat(TeacherTimeConflictChecker.timeOverlaps(truocRaChoi, sauRaChoi))
                .isFalse();
    }

    @Test
    void thieuDuLieuThiBoQua() {
        assertThat(TeacherTimeConflictChecker.timeOverlaps(null, period("07:00", "07:35")))
                .isFalse();
        assertThat(TeacherTimeConflictChecker.timeOverlaps(period("07:00", "07:35"), new Period()))
                .isFalse();
    }
}
