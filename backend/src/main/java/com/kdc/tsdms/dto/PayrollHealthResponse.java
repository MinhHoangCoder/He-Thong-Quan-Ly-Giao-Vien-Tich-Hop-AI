package com.kdc.tsdms.dto;

import java.util.List;

/**
 * KIỂM TRA SỨC KHỎE DỮ LIỆU TRƯỚC KHI CHỐT LƯƠNG (GET /api/v1/payroll/health).
 *
 * <p>Chốt lương là hành động một chiều: sau đó chấm công của kỳ bị khóa, và mở lại được nhưng
 * phải có lý do và có giới hạn thời gian. Chốt khi dữ liệu còn khuyết nghĩa là khóa luôn cái
 * khuyết đó vào trong.
 *
 * <p>Điểm chung của mọi vấn đề liệt kê ở đây: <b>không cái nào tự báo lỗi</b>. Bảng lương vẫn
 * sinh ra bình thường, con số vẫn có, chỉ là sai — và người duy nhất phát hiện là giáo viên bị
 * hụt tiền, sau khi đã nhận lương. Vì vậy phải hỏi trước khi bấm, không phải sau.
 */
public record PayrollHealthResponse(
        short year,
        short month,
        /** Không còn vấn đề mức CHẶN nào. Cảnh báo thì vẫn chốt được. */
        boolean sanSangChot,
        List<VanDe> vanDe) {

    /**
     * @param ma mã định danh để frontend nhận diện, không phải để hiển thị
     * @param mucDo {@code CHAN} = nên sửa trước khi chốt · {@code CANH_BAO} = biết để cân nhắc
     * @param soLuong số dòng dữ liệu dính vấn đề — bằng 0 thì không trả về
     * @param duongDan màn hình đi sửa. Chỉ ra vấn đề mà không chỉ đường sửa thì cảnh báo chỉ
     *     làm người ta bực.
     */
    public record VanDe(String ma, String mucDo, String tieuDe, String moTa, int soLuong, String duongDan) {

        public static final String CHAN = "CHAN";
        public static final String CANH_BAO = "CANH_BAO";
    }
}
