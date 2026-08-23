package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Payroll;
import java.math.BigDecimal;

/** DTO 1 dòng bảng lương tháng — kèm tên GV; NetAmount do DB tính (chỉ đọc). */
public class PayrollResponse {

    public Integer id;
    public Integer teacherId;
    public String teacherName;
    public Short periodMonth;
    public Short periodYear;
    public BigDecimal baseSalary;
    public BigDecimal taughtHours;
    public BigDecimal ratePerHour;
    public BigDecimal allowance;
    public BigDecimal bonus;
    public BigDecimal deduction;
    public BigDecimal netAmount;

    /** DRAFT | FINALIZED | PAID */
    public String status;

    /**
     * Số buổi giáo viên đi muộn trong kỳ — TÍNH LẠI mỗi lần đọc từ bảng chấm công, không lưu.
     *
     * <p>Đi muộn vẫn được trả đủ tiền tiết (tiết đó vẫn dạy hết nội dung), nên con số này
     * không tham gia công thức tính tiền. Nó có mặt để kế toán NHÌN THẤY rồi tự quyết định
     * khấu trừ — giấu đi thì trên hệ thống người đi muộn 40 phút và người đúng giờ giống hệt
     * nhau.
     *
     * <p>Không thêm cột vào bảng Payroll: cột lưu sẵn sẽ cũ đi mỗi khi chấm công được sửa,
     * mà số này lấy từ đúng đám dữ liệu đang duyệt nên không tốn thêm câu SQL nào.
     */
    public long lateCount;

    public static PayrollResponse fromEntity(Payroll p, String teacherName) {
        PayrollResponse r = new PayrollResponse();
        r.id = p.getId();
        r.teacherId = p.getTeacherId();
        r.teacherName = teacherName;
        r.periodMonth = p.getPeriodMonth();
        r.periodYear = p.getPeriodYear();
        r.baseSalary = p.getBaseSalary();
        r.taughtHours = p.getTaughtHours();
        r.ratePerHour = p.getRatePerHour();
        r.allowance = p.getAllowance();
        r.bonus = p.getBonus();
        r.deduction = p.getDeduction();
        r.netAmount = p.getNetAmount();
        r.status = p.getStatus();
        return r;
    }
}
