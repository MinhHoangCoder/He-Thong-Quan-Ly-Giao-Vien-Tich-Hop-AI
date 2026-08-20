package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.exception.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * KHÔNG XẾP GIÁO VIÊN VÀO LỚP CỦA NĂM HỌC ĐÃ QUA.
 *
 * <p>Mỗi lớp có một bản ghi RIÊNG cho từng năm học, nên "1A1" của một trường tồn tại nhiều lần
 * với id khác nhau. Trên dữ liệu thật: 198 lớp năm 2025-2026 và 198 lớp năm 2026-2027, trùng
 * tên từng cặp — nhìn trong dropdown là hai dòng y hệt nhau.
 *
 * <p>Vì sao phải CHẶN chứ không chỉ cảnh báo: {@code TeacherTimeConflictChecker.checkClass} dò
 * trùng theo {@code classId}. Hai bản ghi của CÙNG một phòng học thật mang hai id khác nhau —
 * xếp hai giáo viên vào cùng phòng, cùng giờ, hệ thống KHÔNG kêu gì cả. Luật chống trùng lớp
 * bị vô hiệu đúng lúc cần nhất, mà phiếu vẫn lưu thành công và lịch vẫn sinh đầy đủ nên không
 * ai nhận ra.
 *
 * <p>Mốc so sánh là NĂM BẮT ĐẦU của năm học, với quy ước "từ tháng 8 tính là năm học mới" — để
 * giai đoạn tựu trường cuối tháng 8 (Hải Phòng 2026: tựu trường 24/8, khai giảng 5/9) không bị
 * chặn oan.
 */
class AssignmentStaleClassTest {

    private static void check(String schoolYear, LocalDate startDate) {
        SchoolClass c = new SchoolClass();
        c.setId(1);
        c.setName("1A1");
        c.setSchoolYear(schoolYear);
        AssignmentService.assertClassNotStale(c, startDate);
    }

    @Test
    void chan_lop_cua_nam_hoc_truoc() {
        assertThatThrownBy(() -> check("2025-2026", LocalDate.of(2026, 9, 7)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cho_qua_lop_dung_nam_hoc() {
        assertThatCode(() -> check("2026-2027", LocalDate.of(2026, 9, 7))).doesNotThrowAnyException();
    }

    @Test
    void thang_8_da_tinh_la_nam_hoc_moi() {
        // Tựu trường 24/8/2026 → phiếu bắt đầu trong tháng 8 vẫn thuộc năm học 2026-2027.
        assertThatCode(() -> check("2026-2027", LocalDate.of(2026, 8, 24))).doesNotThrowAnyException();
        assertThatThrownBy(() -> check("2025-2026", LocalDate.of(2026, 8, 24))).isInstanceOf(ApiException.class);
    }

    @Test
    void thang_5_van_thuoc_nam_hoc_cu_nen_khong_bi_chan() {
        // Học kỳ 2 chạy tới tháng 5/2027 → lớp 2026-2027 vẫn hợp lệ.
        assertThatCode(() -> check("2026-2027", LocalDate.of(2027, 5, 10))).doesNotThrowAnyException();
    }

    @Test
    void lop_cua_nam_hoc_TUONG_LAI_khong_bi_chan() {
        // Xếp trước cho năm sau là việc hợp lệ — luật chỉ chặn lớp ĐÃ QUA.
        assertThatCode(() -> check("2027-2028", LocalDate.of(2026, 9, 7))).doesNotThrowAnyException();
    }

    @Test
    void du_lieu_thieu_hoac_hong_thi_bo_qua_khong_chan_nguoi_dung() {
        // Đây là luật phòng nhầm, không phải ràng buộc toàn vẹn — dữ liệu cũ không nên chặn việc.
        assertThatCode(() -> check(null, LocalDate.of(2026, 9, 7))).doesNotThrowAnyException();
        assertThatCode(() -> check("", LocalDate.of(2026, 9, 7))).doesNotThrowAnyException();
        assertThatCode(() -> check("abcd-efgh", LocalDate.of(2026, 9, 7))).doesNotThrowAnyException();
        assertThatCode(() -> check("2026-2027", null)).doesNotThrowAnyException();
    }

    @Test
    void thong_bao_loi_phai_noi_ro_lop_nao_nam_nao_va_phai_chon_gi() {
        // Xếp 100 giáo viên mà chỉ báo "lớp không hợp lệ" thì người dùng không biết sửa ở đâu.
        assertThatThrownBy(() -> check("2024-2025", LocalDate.of(2026, 9, 7)))
                .hasMessageContaining("1A1")
                .hasMessageContaining("2024-2025")
                .hasMessageContaining("2026-2027");
    }
}
