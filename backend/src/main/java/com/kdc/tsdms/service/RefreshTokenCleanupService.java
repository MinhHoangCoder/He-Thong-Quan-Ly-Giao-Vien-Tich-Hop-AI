package com.kdc.tsdms.service;

import com.kdc.tsdms.repository.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DỌN REFRESH TOKEN ĐÃ CHẾT.
 *
 * <p>Bảng {@code RefreshToken} chỉ lớn lên chứ không bao giờ nhỏ đi: mỗi lần đăng nhập thêm
 * một dòng, mỗi lần đổi mật khẩu thu hồi hàng loạt dòng khác — nhưng không có gì XÓA chúng.
 * Đo trên máy dev sau vài tháng: 1.381 dòng, trong đó chỉ 21 dòng còn sống.
 *
 * <p>Vì sao đáng dọn chứ không mặc kệ:
 *
 * <ul>
 *   <li>Mỗi lần làm mới phiên đều quét bảng này. Bảng càng phình thì thao tác nằm trên đường
 *       đăng nhập của mọi người càng chậm.
 *   <li>Chuỗi băm của token đã thu hồi vẫn là dữ liệu nhạy cảm. Giữ vô thời hạn là mở rộng
 *       phạm vi thiệt hại nếu bản sao lưu lọt ra ngoài, mà không đổi lại được gì.
 * </ul>
 *
 * <p>GIỮ THÊM {@value #GIU_THEM_NGAY} NGÀY sau khi token chết, không xóa ngay. Token vừa hết
 * hạn hoặc vừa bị thu hồi còn là bằng chứng để trả lời "phiên này bị đá ra lúc nào, vì sao" —
 * xóa tức thì là mất luôn dấu vết ngay lúc cần nhất.
 *
 * <p>Chạy MỘT LẦN MỖI NGÀY chứ không phải mỗi vài phút: đây là việc dọn dẹp, không phải việc
 * nghiệp vụ, và dữ liệu thừa một ngày không hại gì. {@code initialDelay} để ứng dụng khởi động
 * xong hẳn rồi mới đụng vào DB.
 */
@Service
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    /** Số ngày còn giữ lại một token sau khi nó hết hạn / bị thu hồi. */
    static final int GIU_THEM_NGAY = 30;

    private static final long MOT_NGAY_MS = 24L * 60 * 60 * 1000;

    private final RefreshTokenRepository repo;

    public RefreshTokenCleanupService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Scheduled(fixedDelay = MOT_NGAY_MS, initialDelay = 300_000)
    @Transactional
    public void don() {
        Instant nguong = Instant.now().minus(GIU_THEM_NGAY, ChronoUnit.DAYS);
        int daXoa = repo.deleteChetTruoc(nguong);
        if (daXoa > 0) {
            log.info("Đã dọn {} refresh token chết trước {}.", daXoa, nguong);
        }
    }
}
