package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.common.Paging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TRẦN SỐ DÒNG MỖI REQUEST.
 *
 * <p>{@code page} và {@code size} đến thẳng từ query string. Trước bản vá này, sáu endpoint
 * danh sách chỉ có {@code Math.max(size, 1)} — tức là chặn số âm mà bỏ ngỏ số lớn. Bất kỳ ai
 * mở DevTools và sửa URL thành {@code ?size=1000000} đều kéo trọn cả bảng về: server dựng từng
 * ấy entity trong bộ nhớ rồi tuần tự hóa ra JSON, không cần sửa một dòng code nào.
 *
 * <p>Với dữ liệu demo hiện tại (hàng trăm nghìn buổi dạy) đó là cách làm nghẽn hệ thống nhanh
 * nhất, nên nó được khóa ở MỘT chỗ dùng chung thay vì mỗi controller tự nhớ.
 */
class PagingGuardTest {

    @Test
    @DisplayName("size khổng lồ bị kẹp về trần, không kéo được cả bảng")
    void size_khong_lo_bi_kep() {
        assertThat(Paging.of(0, 1_000_000).getPageSize()).isEqualTo(Paging.MAX_SIZE);
    }

    @Test
    @DisplayName("size 0 hoặc âm về 1 — Spring ném IllegalArgumentException nếu để nguyên")
    void size_khong_hop_le_ve_mot() {
        assertThat(Paging.of(0, 0).getPageSize()).isEqualTo(1);
        assertThat(Paging.of(0, -5).getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("size hợp lệ đi qua nguyên vẹn")
    void size_hop_le_giu_nguyen() {
        assertThat(Paging.of(2, 10).getPageSize()).isEqualTo(10);
        assertThat(Paging.of(2, 10).getPageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("trang âm về 0")
    void trang_am_ve_khong() {
        assertThat(Paging.of(-3, 10).getPageNumber()).isZero();
    }
}
