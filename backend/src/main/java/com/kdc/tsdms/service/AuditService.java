package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.AuditLog;
import com.kdc.tsdms.repository.AuditLogRepository;
import com.kdc.tsdms.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * NHẬT KÝ HỆ THỐNG — ghi lại AI đã làm GÌ với dữ liệu nào.
 *
 * <p>Trả lời một câu hỏi mà trước đây hệ thống không trả lời được: <b>"cái này ai xóa, lúc
 * nào?"</b>. Các bảng đều có {@code DeletedBy}/{@code DeletedAt}, nhưng chúng chỉ giữ được LẦN
 * CUỐI và chỉ cho thao tác xóa — khôi phục rồi xóa lại thì dấu vết cũ mất, còn những việc
 * không-xóa-nhưng-nguy-hiểm (mở lại kỳ lương đã chốt, ép duyệt thay giáo viên) thì không để lại
 * gì cả.
 *
 * <p>CHỈ GHI THAO TÁC NGUY HIỂM, không ghi thao tác xem. Nhật ký ngập dòng "đã xem danh sách"
 * là nhật ký không ai đọc, và nó làm bảng phình lên nhanh hơn bảng nghiệp vụ.
 *
 * <p><b>Ghi trong giao dịch RIÊNG</b> ({@link Propagation#REQUIRES_NEW}). Nếu dùng chung giao
 * dịch với việc chính thì một thao tác bị rollback sẽ xóa luôn dòng nhật ký của nó — mất đúng
 * bằng chứng cần nhất. Đổi lại, nhật ký có thể ghi cả những lần thất bại; đó là chủ ý.
 *
 * <p>Lỗi khi ghi nhật ký KHÔNG được làm hỏng việc chính: không ai chấp nhận "không xóa được
 * giáo viên vì bảng log đầy". Nuốt lỗi và ghi ra log ứng dụng.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Cắt giá trị dài — cột NVARCHAR có giới hạn, và không ai đọc hết một chuỗi 4.000 ký tự. */
    private static final int DAI_TOI_DA = 900;

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /**
     * @param action việc đã làm, dạng {@code XOA_GIAO_VIEN}, {@code MO_LAI_LUONG}
     * @param entity tên bảng nghiệp vụ, dạng {@code Teacher}
     * @param entityId khóa của bản ghi, để chuỗi vì có bảng dùng khóa ghép
     * @param truoc trạng thái trước khi đổi (null nếu không có gì để so)
     * @param sau trạng thái sau khi đổi, hoặc lý do người dùng nhập
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ghi(String action, String entity, Object entityId, String truoc, String sau) {
        try {
            AuditLog l = new AuditLog();
            l.setActorUserId(SecurityUtils.currentUserId());
            l.setAction(action);
            l.setEntity(entity);
            l.setEntityId(entityId == null ? null : String.valueOf(entityId));
            l.setOldValue(cat(truoc));
            l.setNewValue(cat(sau));
            l.setIpAddress(diaChiIp());
            repo.save(l);
        } catch (RuntimeException e) {
            log.warn("Không ghi được nhật ký cho thao tác {} trên {} #{}", action, entity, entityId, e);
        }
    }

    /** Dạng rút gọn cho thao tác không cần so sánh trước/sau. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ghi(String action, String entity, Object entityId, String moTa) {
        ghi(action, entity, entityId, null, moTa);
    }

    private static String cat(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= DAI_TOI_DA ? s : s.substring(0, DAI_TOI_DA) + "…";
    }

    /**
     * Địa chỉ IP của người thực hiện.
     *
     * <p>Ưu tiên {@code X-Forwarded-For} vì khi chạy sau reverse proxy thì {@code
     * getRemoteAddr()} chỉ trả về IP của proxy — mọi dòng nhật ký sẽ mang cùng một địa chỉ và
     * cột này thành vô dụng. Header đó GIẢ MẠO ĐƯỢC nếu không đứng sau proxy tin cậy, nên nó
     * là manh mối điều tra chứ không phải bằng chứng.
     *
     * <p>Gọi ngoài ngữ cảnh HTTP (job nền) thì trả null, không ném lỗi.
     */
    private static String diaChiIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For là danh sách; IP gốc của client là phần tử ĐẦU.
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
