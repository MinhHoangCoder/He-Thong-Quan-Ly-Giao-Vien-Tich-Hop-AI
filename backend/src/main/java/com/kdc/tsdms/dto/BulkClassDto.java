package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO cho màn THÊM LỚP HÀNG LOẠT.
 *
 * <p>Ba cách nhập (sinh theo mẫu / dán từ Excel / tải file) chỉ khác nhau ở BƯỚC ĐỌC. Sau khi
 * đọc xong, cả ba đều chảy vào cùng một danh sách {@link Dong} rồi qua cùng một bộ kiểm và
 * cùng một đường ghi. Ba lối vào, một đường code — nếu tách ba đường thì ba nơi có thể trôi ra
 * khác nhau và người dùng gặp ba kiểu báo lỗi cho cùng một sai sót.
 */
public final class BulkClassDto {

    private BulkClassDto() {}

    /** Trạng thái của một dòng sau khi kiểm. */
    public enum TrangThai {
        /** Hợp lệ, sẽ được tạo. */
        HOP_LE,
        /** Lớp đã tồn tại ở trường này — bỏ qua, không ghi đè. */
        DA_TON_TAI,
        /** Dữ liệu sai, kèm lý do ở {@link Dong#message}. */
        LOI
    }

    /**
     * Một dòng lớp học đang chờ tạo.
     *
     * @param dong số thứ tự dòng trong nguồn nhập — để người dùng dò ngược về đúng dòng trong
     *     file Excel của họ, thay vì phải tự đếm
     */
    public record Dong(
            int dong, String name, String gradeLevel, String schoolYear, TrangThai trangThai, String message) {

        public static Dong loi(int dong, String name, String gradeLevel, String schoolYear, String message) {
            return new Dong(dong, name, gradeLevel, schoolYear, TrangThai.LOI, message);
        }
    }

    /**
     * Yêu cầu XEM TRƯỚC.
     *
     * @param mode {@code GENERATE} = sinh theo mẫu (dùng {@code grades} + {@code soLopMoiKhoi});
     *     {@code TEXT} = đọc từ {@code duLieu} do người dùng dán vào
     * @param grades các khối cần sinh, ví dụ {@code [1,2,3]}
     * @param soLopMoiKhoi mỗi khối bao nhiêu lớp — tên lớp sinh ra là {@code 1A1, 1A2, ...}
     * @param duLieu văn bản dán từ Excel: mỗi dòng một lớp, các cột cách nhau bằng Tab hoặc dấu
     *     phẩy, theo thứ tự {@code Tên lớp, Khối, Năm học}
     */
    public record XemTruocRequest(
            @NotNull(message = "Vui lòng chọn trường") Integer schoolId,
            String mode,
            String schoolYear,
            List<Integer> grades,
            Integer soLopMoiKhoi,
            String duLieu) {}

    /** Kết quả xem trước — người dùng nhìn rồi mới quyết định có lưu hay không. */
    public record XemTruocResponse(
            Integer schoolId, String schoolName, List<Dong> rows, int soHopLe, int soDaTonTai, int soLoi) {}

    /** Yêu cầu GHI: chỉ những dòng hợp lệ mà người dùng đã xem và đồng ý. */
    public record TaoRequest(
            @NotNull(message = "Vui lòng chọn trường") Integer schoolId, List<Dong> rows) {}

    /** Kết quả ghi. */
    public record TaoResponse(int daTao, int boQua) {}
}
