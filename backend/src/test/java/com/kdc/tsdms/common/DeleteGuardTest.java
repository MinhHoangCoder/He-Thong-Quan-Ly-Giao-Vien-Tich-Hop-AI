package com.kdc.tsdms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit test cho {@link DeleteGuard} — chốt RESTRICT dùng chung cho mọi luồng xóa.
 *
 * <p>Thứ đáng test ở đây không phải là "có ném lỗi không" mà là NỘI DUNG câu báo lỗi: người
 * dùng chỉ biết phải làm gì tiếp theo qua đúng câu đó. Kể thiếu một rào là họ sửa xong bấm lại
 * và ăn lỗi lần nữa.
 */
class DeleteGuardTest {

    @Test
    void khongConDuLieuCon_thiChoXoa() {
        assertThatCode(() -> DeleteGuard.of("trường A")
                        .blockIf(0, "lớp học")
                        .blockIf(0, "phân công")
                        .check())
                .doesNotThrowAnyException();
    }

    @Test
    void motRao_thiBaoDungRaoDo_va409() {
        assertThatThrownBy(
                        () -> DeleteGuard.of("trường A").blockIf(3, "lớp học").check())
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Không thể xóa trường A")
                .hasMessageContaining("3 lớp học")
                .hasMessageContaining("mục này");
    }

    @Test
    void nhieuRao_thiKeHET_trongMotCauChuKhongDungOCaiDauTien() {
        // Đây là lý do lớp này tồn tại: kiểu cũ ném ngay ở rào đầu nên người dùng phải sửa —
        // bấm lại — gặp rào mới, lặp mãi mà không biết còn bao nhiêu thứ phía sau.
        assertThatThrownBy(() -> DeleteGuard.of("giáo viên Nguyễn Văn An")
                        .blockIf(2, "phân công đang chạy")
                        .blockIf(5, "buổi dạy sắp tới")
                        .check())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2 phân công đang chạy")
                .hasMessageContaining("5 buổi dạy sắp tới")
                .hasMessageContaining("các mục trên");
    }

    @Test
    void batDauTuBa_thiNoiBangPhayVaChuVaOCuoi() {
        assertThatThrownBy(() -> DeleteGuard.of("trường A")
                        .blockIf(1, "lớp học")
                        .blockIf(2, "phân công")
                        .blockIf(3, "hợp đồng")
                        .check())
                .hasMessageContaining("1 lớp học, 2 phân công và 3 hợp đồng");
    }

    @Test
    void raoKhongDemDuoc_thiGhiNguyenVanLyDo() {
        assertThatThrownBy(() -> DeleteGuard.of("phân công #7")
                        .blockWhen(true, "kỳ lương liên quan đã chốt")
                        .check())
                .hasMessageContaining("kỳ lương liên quan đã chốt");
    }

    @Test
    void soLuongAm_hoacBang0_thiKhongTinhLaRao() {
        DeleteGuard g = DeleteGuard.of("x").blockIf(0, "a").blockIf(-1, "b");

        assertThat(g.blocked()).isFalse();
    }

    @Test
    void blocked_choBietCoRaoMaKhongCanNemLoi() {
        assertThat(DeleteGuard.of("x").blockIf(1, "a").blocked()).isTrue();
    }
}
