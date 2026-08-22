package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.dto.PayRateRequest;
import com.kdc.tsdms.entity.PayRate;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.PayRateRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BẢNG ĐƠN GIÁ TIẾT DẠY (Flyway V37) — đưa hai con số từng nằm cứng trong {@code
 * PayrollService} ra thành dữ liệu sửa được.
 *
 * <p>Cách dùng đúng khi tăng giá: gọi {@link #create} với mức mới kèm ngày bắt đầu áp dụng.
 * Service tự ĐÓNG mức cũ ở ngày liền trước, KHÔNG sửa đè lên nó. Sửa đè là xóa lịch sử giá:
 * mọi kỳ lương cũ tính lại sẽ ra số khác với số đã trả, mà không có dấu vết nào giải thích.
 */
@Service
public class PayRateService {

    private final PayRateRepository repo;
    private final AuditService auditService;

    public PayRateService(PayRateRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    /** Toàn bộ bảng giá, mới nhất trước — cả mức đang dùng lẫn mức đã đóng. */
    @Transactional(readOnly = true)
    public List<PayRate> list() {
        return repo.findAllByOrderByEffectiveFromDescGradeFromAsc();
    }

    /**
     * Thêm một mức giá và tự đóng mức cũ cùng khoảng khối.
     *
     * <p>Mức cũ được đóng ở ngày LIỀN TRƯỚC ngày mức mới có hiệu lực, nên hai mức không bao
     * giờ cùng phủ một ngày. Không có bước này thì {@code PayrollService.resolveRate} sẽ gặp
     * hai mức khớp và lấy mức đầu tiên trong danh sách — một kết quả đúng do may mắn.
     */
    @Transactional
    public PayRate create(PayRateRequest req) {
        if (req.gradeTo() < req.gradeFrom()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Khối kết thúc phải từ khối bắt đầu trở đi.");
        }
        Integer userId = SecurityUtils.currentUserId();
        LocalDate ngayDong = req.effectiveFrom().minusDays(1);

        for (PayRate cu : repo.findAllByOrderByEffectiveFromDescGradeFromAsc()) {
            boolean trungKhoi =
                    cu.getGradeFrom().equals(req.gradeFrom()) && cu.getGradeTo().equals(req.gradeTo());
            if (!trungKhoi || cu.getEffectiveTo() != null) {
                continue;
            }
            if (!cu.getEffectiveFrom().isBefore(req.effectiveFrom())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Đã có mức giá cho khối này áp dụng từ " + cu.getEffectiveFrom()
                                + ". Ngày áp dụng của mức mới phải sau ngày đó.");
            }
            cu.setEffectiveTo(ngayDong);
            cu.setUpdatedAt(Instant.now());
            cu.setUpdatedBy(userId);
            repo.save(cu);
        }

        PayRate moi = new PayRate();
        moi.setGradeFrom(req.gradeFrom());
        moi.setGradeTo(req.gradeTo());
        moi.setAmount(req.amount());
        moi.setEffectiveFrom(req.effectiveFrom());
        moi.setNote(
                req.note() == null || req.note().isBlank() ? null : req.note().trim());
        moi.setCreatedBy(userId);
        PayRate daLuu = repo.save(moi);
        auditService.ghi(
                "THEM_DON_GIA",
                "PayRate",
                daLuu.getId(),
                "Khối " + req.gradeFrom() + "-" + req.gradeTo() + " · "
                        + req.amount().toPlainString() + "đ/tiết, áp dụng từ " + req.effectiveFrom());
        return daLuu;
    }

    /**
     * Xóa một mức giá và MỞ LẠI mức mà nó đã đóng.
     *
     * <p>CHỈ xóa được mức CHƯA có hiệu lực. Mức đã từng áp dụng là căn cứ của những phiếu
     * lương đã trả — xóa nó đi thì tính lại kỳ cũ sẽ ra số khác, và không ai giải thích được
     * chênh lệch.
     *
     * <p>PHẢI MỞ LẠI MỨC CŨ, nếu không sẽ để lại một LỖ THỦNG trong bảng giá. {@link #create}
     * đóng mức cũ ở ngày liền trước mức mới; xóa mức mới mà không đảo ngược bước đó thì từ
     * ngày ấy trở đi khoảng khối này không còn mức nào phủ.
     *
     * <p>Hậu quả không hề ồn ào: {@code PayrollService.resolveRate} trả {@code null},
     * {@code generate} ghi một dòng cảnh báo vào log rồi <b>bỏ qua tiết đó</b>. Phiếu lương
     * vẫn sinh ra bình thường, chỉ là thiếu tiền — và người duy nhất phát hiện là giáo viên bị
     * hụt, sau khi đã nhận lương.
     */
    @Transactional
    public void delete(Integer id) {
        PayRate r = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy mức giá id=" + id));
        if (!r.getEffectiveFrom().isAfter(BusinessTime.today())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Mức giá này đã có hiệu lực từ " + r.getEffectiveFrom()
                            + " nên là căn cứ của các phiếu lương đã tính. Muốn dừng áp dụng thì khai mức mới.");
        }
        moLaiMucBiDongBoi(r);
        repo.delete(r);
        auditService.ghi(
                "XOA_DON_GIA",
                "PayRate",
                id,
                "Khối " + r.getGradeFrom() + "-" + r.getGradeTo() + " · "
                        + r.getAmount().toPlainString() + "đ/tiết, lẽ ra áp dụng từ " + r.getEffectiveFrom(),
                "Đã mở lại mức giá liền trước");
    }

    /**
     * Mức nào bị {@code r} đóng lại thì mở ra: cùng khoảng khối và có {@code EffectiveTo} đúng
     * bằng ngày liền trước {@code r.EffectiveFrom}.
     *
     * <p>So khớp theo ngày chứ không lưu con trỏ "mức trước là ai": ngày là dữ liệu đã có sẵn
     * và luôn đúng, còn một cột con trỏ là thêm một thứ có thể lệch.
     */
    private void moLaiMucBiDongBoi(PayRate r) {
        LocalDate ngayDaDong = r.getEffectiveFrom().minusDays(1);
        for (PayRate cu : repo.findAllByOrderByEffectiveFromDescGradeFromAsc()) {
            boolean cungKhoi = cu.getGradeFrom().equals(r.getGradeFrom())
                    && cu.getGradeTo().equals(r.getGradeTo());
            if (cungKhoi && !cu.getId().equals(r.getId()) && ngayDaDong.equals(cu.getEffectiveTo())) {
                cu.setEffectiveTo(null);
                cu.setUpdatedAt(Instant.now());
                cu.setUpdatedBy(SecurityUtils.currentUserId());
                repo.save(cu);
                return;
            }
        }
    }
}
