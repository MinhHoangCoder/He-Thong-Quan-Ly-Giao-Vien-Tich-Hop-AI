package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO cho màn THÊM LỚP HÀNG LOẠT.
 *
 * <p>Màn này chỉ còn MỘT nguồn dữ liệu: bảng nhập nhiều dòng trên giao diện. Nút nạp file Excel
 * cũng đổ vào chính bảng đó chứ không đi đường riêng, nên file và tay gõ chịu chung một bộ
 * kiểm và một đường ghi — trước đây ba lối vào (sinh theo mẫu / dán từ Excel / tải file) chia
 * ba nhánh code và người dùng gặp ba kiểu báo lỗi cho cùng một sai sót.
 *
 * <p>Không còn bước "xem trước" riêng: bảng nhập đã là bản xem trước, và người dùng sửa được
 * ngay tại chỗ thay vì phải quay lại nguồn nhập rồi bấm xem trước lần nữa.
 */
public final class BulkClassDto {

    private BulkClassDto() {}

    /**
     * Một dòng lớp học trong bảng nhập.
     *
     * @param dong số thứ tự dòng trên màn hình — để câu báo lỗi chỉ đích danh "dòng 7" thay vì
     *     bắt người dùng tự đếm xem lớp bị từ chối nằm ở đâu trong 40 dòng
     * @param gradeLevel khối; bỏ trống được vì suy được từ chữ số đầu tên lớp ("7A1" → 7)
     * @param schoolYear năm học riêng của dòng; bỏ trống thì lấy năm học chung của cả lô
     */
    public record Dong(int dong, String name, String gradeLevel, String schoolYear) {}

    /**
     * Yêu cầu GHI cả lô.
     *
     * @param schoolYear năm học chung, áp cho những dòng bỏ trống cột Năm học
     */
    public record TaoRequest(
            @NotNull(message = "Vui lòng chọn trường") Integer schoolId, String schoolYear, List<Dong> rows) {}

    /**
     * Kết quả ghi.
     *
     * <p>Chỉ còn một con số vì luồng là được ăn cả ngã về không: hoặc tạo hết, hoặc không tạo
     * dòng nào và trả lỗi. Không còn "bỏ qua bao nhiêu dòng" để người dùng phải đi dò.
     */
    public record TaoResponse(int daTao) {}
}
