package com.kdc.tsdms.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHIA LÔ TRƯỚC KHI NÉM VÀO {@code WHERE Id IN (...)}.
 *
 * <p>SQL Server chỉ nhận 2.100 tham số cho một câu lệnh, mà {@code findAllById} dựng đúng một
 * dấu hỏi cho mỗi khóa. Lỗi này ĐÃ XẢY RA THẬT trên dữ liệu demo: màn Lịch dạy gom ô lịch của
 * mọi buổi trong khoảng đang xem, chọn khoảng vài tháng là chạm 4.749 ô và request trả 500 với
 * "The incoming request has too many parameters".
 *
 * <p>Điểm đáng chú ý: nó KHÔNG lộ ra ở dữ liệu nhỏ. Với 1.000 dòng mọi thứ vẫn xanh; phải tới
 * khoảng 3.000 dòng mới hỏng — tức là hỏng đúng lúc hệ thống bắt đầu được dùng thật.
 */
class BatchInTest {

    /** Ghi lại kích thước từng lô để kiểm tra việc chia, rồi trả về chính các khóa của lô. */
    private static List<Integer> ghiNhan(List<Integer> lo, List<Integer> kichThuocLo) {
        kichThuocLo.add(lo.size());
        return lo;
    }

    @Test
    @DisplayName("Danh sách dài hơn trần thì bị chia lô, không lô nào vượt 1.000 khóa")
    void chia_lo_khi_vuot_tran() {
        List<Integer> keys = IntStream.rangeClosed(1, 4749).boxed().toList();
        List<Integer> kichThuocLo = new ArrayList<>();

        List<Integer> ketQua = BatchIn.theoLo(keys, lo -> ghiNhan(lo, kichThuocLo));

        assertThat(kichThuocLo).containsExactly(1000, 1000, 1000, 1000, 749);
        assertThat(kichThuocLo).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(BatchIn.LO_TOI_DA));
        assertThat(ketQua).hasSize(4749);
    }

    @Test
    @DisplayName("Danh sách ngắn thì chỉ một lô, không gọi thừa")
    void danh_sach_ngan_thi_mot_lo() {
        List<Integer> kichThuocLo = new ArrayList<>();
        BatchIn.theoLo(List.of(1, 2, 3), lo -> ghiNhan(lo, kichThuocLo));
        assertThat(kichThuocLo).containsExactly(3);
    }

    @Test
    @DisplayName("Đúng bằng trần thì vẫn một lô")
    void dung_bang_tran_thi_mot_lo() {
        List<Integer> keys = IntStream.rangeClosed(1, BatchIn.LO_TOI_DA).boxed().toList();
        List<Integer> kichThuocLo = new ArrayList<>();
        BatchIn.theoLo(keys, lo -> ghiNhan(lo, kichThuocLo));
        assertThat(kichThuocLo).containsExactly(BatchIn.LO_TOI_DA);
    }

    @Test
    @DisplayName("Danh sách rỗng hoặc null thì KHÔNG gọi truy vấn lần nào")
    void rong_thi_khong_goi_truy_van() {
        List<Integer> kichThuocLo = new ArrayList<>();
        assertThat(BatchIn.theoLo(List.<Integer>of(), lo -> ghiNhan(lo, kichThuocLo)))
                .isEmpty();
        assertThat(BatchIn.theoLo((List<Integer>) null, lo -> ghiNhan(lo, kichThuocLo)))
                .isEmpty();
        assertThat(kichThuocLo).isEmpty();
    }

    @Test
    @DisplayName("Gộp đủ kết quả của mọi lô, giữ nguyên thứ tự")
    void gop_du_ket_qua() {
        List<Integer> keys = IntStream.rangeClosed(1, 2500).boxed().toList();
        List<Integer> ketQua = BatchIn.theoLo(keys, lo -> lo);
        assertThat(ketQua).hasSize(2500);
        assertThat(ketQua.get(0)).isEqualTo(1);
        assertThat(ketQua.get(2499)).isEqualTo(2500);
    }
}
