package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.AuditLogResponse;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.AuditLog;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AuditLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ĐỌC nhật ký hệ thống.
 *
 * <p>Tách khỏi {@link AuditService} (chỉ ghi) vì hai việc có vòng đời khác hẳn nhau: ghi thì
 * gọi từ hàng chục chỗ trong giao dịch riêng, còn đọc thì chỉ một màn hình gọi và cần tra tên
 * người dùng, dịch nhãn, phân trang. Nhét chung một class là buộc mọi service đang chỉ muốn ghi
 * phải kéo theo cả bộ phụ thuộc của phần đọc.
 */
@Service
public class AuditQueryService {

    /**
     * Nhãn tiếng Việt cho từng loại thao tác.
     *
     * <p>Mã để ASCII và ổn định (dữ liệu đã ghi không đổi được), nhãn nằm ở đây để sửa chữ
     * không phải chạy migration.
     */
    private static final Map<String, String> NHAN = Map.ofEntries(
            Map.entry("XOA_GIAO_VIEN", "Xóa giáo viên"),
            Map.entry("KHOI_PHUC_GIAO_VIEN", "Khôi phục giáo viên"),
            Map.entry("XOA_TRUONG", "Xóa trường"),
            Map.entry("XOA_LOP", "Xóa lớp học"),
            Map.entry("XOA_MON_HOC", "Xóa môn học"),
            Map.entry("XOA_KY_NGHI", "Xóa kỳ nghỉ"),
            Map.entry("HUY_PHAN_CONG", "Hủy phân công"),
            Map.entry("KHOI_PHUC_PHAN_CONG", "Khôi phục phân công"),
            Map.entry("EP_DUYET_PHAN_CONG", "Ép duyệt thay giáo viên"),
            Map.entry("CHOT_LUONG", "Chốt phiếu lương"),
            Map.entry("MO_LAI_LUONG", "Mở lại phiếu lương"),
            Map.entry("TRA_LUONG", "Đánh dấu đã trả lương"),
            Map.entry("THEM_DON_GIA", "Thêm mức đơn giá"),
            Map.entry("XOA_DON_GIA", "Xóa mức đơn giá"),
            Map.entry("THEM_LOP_HANG_LOAT", "Thêm lớp hàng loạt"),
            Map.entry("SUA_CHAM_CONG", "Sửa chấm công"));

    private final AuditLogRepository repo;
    private final AppUserRepository userRepo;

    public AuditQueryService(AuditLogRepository repo, AppUserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    /**
     * @param tuNgay/denNgay lọc theo NGÀY (bao gồm cả hai đầu). Quy về mốc UTC vì cột
     *     {@code CreatedAt} lưu giờ UTC theo quy ước của schema.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(
            String action, String entity, Integer actorId, LocalDate tuNgay, LocalDate denNgay, Pageable pageable) {
        Instant tu = tuNgay == null ? null : tuNgay.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant den = denNgay == null ? null : denNgay.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);

        Page<AuditLog> page = repo.search(rong(action), rong(entity), actorId, tu, den, pageable);

        // Tên người thực hiện: nạp gộp một lần cho cả trang thay vì hỏi từng dòng.
        Map<Integer, String> ten = new HashMap<>();
        List<Integer> ids = page.getContent().stream()
                .map(AuditLog::getActorUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!ids.isEmpty()) {
            for (AppUser u : userRepo.findAllById(ids)) {
                ten.put(u.getId(), u.getUsername());
            }
        }
        return page.map(a -> AuditLogResponse.from(
                a,
                a.getActorUserId() == null
                        ? "(hệ thống)"
                        : ten.getOrDefault(a.getActorUserId(), "(tài khoản #" + a.getActorUserId() + ")"),
                NHAN.getOrDefault(a.getAction(), a.getAction())));
    }

    /** Danh sách loại thao tác + bảng đã từng ghi — đổ vào hai ô lọc của màn hình. */
    @Transactional(readOnly = true)
    public Map<String, Object> filterOptions() {
        List<Map<String, String>> actions = repo.findDistinctActions().stream()
                .map(a -> Map.of("code", a, "label", NHAN.getOrDefault(a, a)))
                .toList();
        return Map.of("actions", actions, "entities", repo.findDistinctEntities());
    }

    private static String rong(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
