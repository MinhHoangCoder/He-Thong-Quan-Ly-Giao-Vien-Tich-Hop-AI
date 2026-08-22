package com.kdc.tsdms.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Dựng {@link Pageable} có KẸP TRẦN cho mọi endpoint danh sách.
 *
 * <p>Vì sao cần: {@code page} và {@code size} đến thẳng từ query string, tức là từ người dùng.
 * Không có cận trên thì bất kỳ ai mở DevTools cũng gọi được {@code ?size=1000000} và kéo trọn
 * một bảng trăm nghìn dòng về — server dựng từng ấy entity trong bộ nhớ, JSON trả về vài chục
 * megabyte, và không cần một dòng code nào bị sửa. Đây là cách phá dễ nhất trong cả hệ thống.
 *
 * <p>{@link #MAX_SIZE} = 100 chứ không phải một số to hơn: màn hình bày 10 dòng một trang, còn
 * các nhu cầu thật cần nhiều hơn (xuất file, in) đều có endpoint riêng của chúng. Ai cần 500
 * dòng một lượt thì đó là dấu hiệu thiếu một endpoint, không phải thiếu một tham số.
 *
 * <p>Kẹp im lặng chứ không ném lỗi: {@code size} quá lớn là lỗi của người gọi chứ không phải
 * của người dùng cuối, và trả về 100 dòng đầu vẫn là câu trả lời dùng được.
 */
public final class Paging {

    /** Số dòng tối đa một request được lấy. */
    public static final int MAX_SIZE = 100;

    private Paging() {}

    /** Trang thứ {@code page} (0-based), tối đa {@link #MAX_SIZE} dòng, không sắp xếp. */
    public static Pageable of(int page, int size) {
        return PageRequest.of(safePage(page), safeSize(size));
    }

    /** Như trên, kèm thứ tự sắp xếp. */
    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(safePage(page), safeSize(size), sort);
    }

    /** Trang âm về 0 — trang "-1" không có nghĩa gì và làm Spring ném IllegalArgumentException. */
    public static int safePage(int page) {
        return Math.max(page, 0);
    }

    /** Kẹp về [1, {@link #MAX_SIZE}]. */
    public static int safeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
