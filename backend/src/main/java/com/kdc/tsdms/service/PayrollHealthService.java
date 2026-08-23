package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.PayrollHealthResponse;
import com.kdc.tsdms.dto.PayrollHealthResponse.VanDe;
import com.kdc.tsdms.repository.PayrollRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KIỂM TRA SỨC KHỎE DỮ LIỆU TRƯỚC KHI CHỐT LƯƠNG.
 *
 * <p>Chốt lương là hành động một chiều: sau đó chấm công của kỳ bị khóa, mở lại được nhưng
 * phải có lý do và chỉ trong một cửa sổ thời gian. Chốt khi dữ liệu còn khuyết là khóa luôn
 * cái khuyết đó vào trong.
 *
 * <p>ĐIỂM CHUNG CỦA SÁU VẤN ĐỀ Ở ĐÂY: <b>không cái nào tự báo lỗi.</b> Bảng lương vẫn sinh ra
 * bình thường, con số vẫn có, chỉ là sai. Người duy nhất phát hiện là giáo viên bị hụt tiền —
 * sau khi đã nhận lương. Kiểu lỗi này không có cách nào tìm ra bằng cách "nhìn màn hình xem có
 * gì đỏ không"; phải chủ động đi đếm.
 *
 * <p>Phân hai mức:
 *
 * <ul>
 *   <li><b>CHẶN</b> — sẽ làm SAI TIỀN nếu chốt luôn. Màn hình can, nhưng không khóa cứng: có
 *       những kỳ mà kế toán biết rõ lý do và vẫn cần chốt đúng hạn.
 *   <li><b>CẢNH BÁO</b> — đáng biết nhưng không nhất thiết sai.
 * </ul>
 *
 * <p>Mỗi vấn đề kèm ĐƯỜNG DẪN tới màn hình đi sửa. Chỉ ra vấn đề mà không chỉ đường sửa thì
 * cảnh báo chỉ làm người ta bực rồi bấm bỏ qua.
 */
@Service
public class PayrollHealthService {

    private final PayrollRepository repo;
    private final HolidayService holidayService;

    public PayrollHealthService(PayrollRepository repo, HolidayService holidayService) {
        this.repo = repo;
        this.holidayService = holidayService;
    }

    @Transactional(readOnly = true)
    public PayrollHealthResponse check(short year, short month) {
        List<VanDe> ds = new ArrayList<>();

        them(
                ds,
                "BUOI_CHUA_CHAM_CONG",
                VanDe.CHAN,
                "Buổi dạy đã qua nhưng chưa chấm công",
                "Giáo viên đã tới trường dạy, nhưng không có dòng chấm công nên tiết đó không vào bảng lương. "
                        + "Chốt kỳ bây giờ là chốt luôn phần tiền bị thiếu.",
                repo.demBuoiChuaChamCong(year, month),
                "/attendance");

        them(
                ds,
                "TIET_KHONG_TRA_DUOC_DON_GIA",
                VanDe.CHAN,
                "Tiết có công nhưng không tra được đơn giá",
                "Lớp không suy được khối, hoặc bảng đơn giá thủng ở ngày dạy đó. Hệ thống lặng lẽ BỎ QUA "
                        + "những tiết này khi tính lương — phiếu vẫn ra, chỉ là thiếu tiền.",
                repo.demTietKhongTraDuocDonGia(year, month),
                "/payroll");

        them(
                ds,
                "PHIEU_LECH_SO_TIET",
                VanDe.CHAN,
                "Phiếu lương nháp lệch số tiết so với chấm công",
                "Đã bấm Tính lương rồi nhưng sau đó chấm công thay đổi. Con số trên phiếu là ảnh chụp của "
                        + "quá khứ — bấm Tính lương lại trước khi chốt.",
                repo.demPhieuLechSoTiet(year, month),
                "/payroll");

        them(
                ds,
                "GIAO_VIEN_CHUA_CO_PHIEU",
                VanDe.CHAN,
                "Giáo viên có dạy nhưng chưa có phiếu lương",
                "Có tiết được tính công trong kỳ mà chưa có phiếu nào. Bấm Tính lương để sinh đủ.",
                repo.demGiaoVienChuaCoPhieu(year, month),
                "/payroll");

        them(
                ds,
                "BUOI_DANG_DO",
                VanDe.CANH_BAO,
                "Buổi dạy chưa điểm danh ra",
                "Đã điểm danh vào mà chưa điểm danh ra nên số giờ công chưa khép. Khóa kỳ lúc này thì giáo "
                        + "viên hết đường bấm điểm danh ra.",
                repo.demBuoiDangDo(year, month),
                "/attendance");

        them(
                ds,
                "CHAM_CONG_MO_COI",
                VanDe.CANH_BAO,
                "Chấm công của buổi dạy đã bị xóa",
                "Dòng chấm công vẫn được đếm vào lương nhưng buổi dạy sinh ra nó đã bị xóa mềm — không còn "
                        + "gì chứng minh buổi đó có thật.",
                repo.demChamCongMoCoi(year, month),
                "/attendance");

        // Kỳ nghỉ: dùng lại đúng bộ đếm đã có ở màn Lịch nghỉ thay vì viết bản thứ hai.
        var nghi = holidayService.holidayIssues(year, month);
        them(
                ds,
                "VANG_ROI_VAO_NGAY_NGHI",
                VanDe.CHAN,
                "Dòng Vắng rơi vào ngày nghỉ",
                "Buổi dạy \"ma\" sinh vào ngày trường đóng cửa; job khép sổ ghi VẮNG và trừ tiền giáo viên "
                        + "cho một buổi chưa từng tồn tại. Vào Lịch nghỉ bấm Hủy buổi dạy để dọn.",
                nghi.absenceCount(),
                "/admin/holidays");

        boolean sanSang = ds.stream().noneMatch(v -> VanDe.CHAN.equals(v.mucDo()));
        return new PayrollHealthResponse(year, month, sanSang, ds);
    }

    /** Chỉ đưa vào danh sách khi thật sự có dòng dính — báo "0 vấn đề" là nhiễu. */
    private static void them(
            List<VanDe> ds, String ma, String mucDo, String tieuDe, String moTa, int soLuong, String duongDan) {
        if (soLuong > 0) {
            ds.add(new VanDe(ma, mucDo, tieuDe, moTa, soLuong, duongDan));
        }
    }
}
