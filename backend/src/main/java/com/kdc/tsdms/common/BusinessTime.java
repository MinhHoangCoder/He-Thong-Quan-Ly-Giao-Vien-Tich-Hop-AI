package com.kdc.tsdms.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * GIỜ NGHIỆP VỤ của hệ thống — luôn là giờ tường Việt Nam.
 *
 * <p>Vì sao phải có class này: {@code TsdmsApplication.main} cố tình ghim JVM về UTC để khớp
 * quy ước "lưu UTC" của schema. Hệ quả là {@code LocalDate.now()} / {@code LocalDateTime.now()}
 * trần trả về giờ UTC, lệch 7 tiếng so với giờ ghi trong dữ liệu nghiệp vụ (giờ vào tiết, giờ
 * chấm công, ngày làm việc). Mọi chỗ hỏi "bây giờ là mấy giờ" hay "hôm nay là ngày nào" đều
 * phải đi qua đây.
 *
 * <p>Trước đây hằng số này bị chép tay ở 7 file khác nhau; quên một chỗ là cả một tính năng
 * lệch một ngày mà không ai thấy cho tới lúc chốt lương. Gom về một chỗ để không còn quên được.
 */
public final class BusinessTime {

    /** Múi giờ nghiệp vụ. Đừng đọc từ cấu hình: dữ liệu đã lỡ lưu theo giờ này rồi. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BusinessTime() {}

    /** Bây giờ, theo giờ tường Việt Nam. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** Hôm nay, theo giờ tường Việt Nam. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
