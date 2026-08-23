package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.PayRateRequest;
import com.kdc.tsdms.entity.PayRate;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.PayRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * BẢNG ĐƠN GIÁ KHÔNG ĐƯỢC CÓ LỖ THỦNG.
 *
 * <p>Tăng giá là {@code create} một mức mới, và service tự ĐÓNG mức cũ ở ngày liền trước. Nếu
 * sau đó xóa mức mới (gõ nhầm ngày, gõ nhầm số) mà không mở lại mức cũ, thì từ ngày ấy trở đi
 * khoảng khối đó <b>không còn mức giá nào phủ</b>.
 *
 * <p>Hậu quả không hề ồn ào — và đó mới là phần nguy hiểm. {@code PayrollService.resolveRate}
 * trả {@code null}, {@code generate} ghi một dòng cảnh báo vào log rồi <b>bỏ qua tiết đó</b>.
 * Phiếu lương vẫn sinh ra bình thường, chỉ là thiếu tiền. Người duy nhất phát hiện là giáo
 * viên bị hụt, sau khi đã nhận lương.
 *
 * <p>Lỗ hổng này nằm im trong backend từ V37 vì chưa có màn hình nào gọi được {@code delete}.
 * Nó lộ ra ngay lần đầu thêm nút Xóa vào giao diện.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayRateHoleTest {

    @Mock
    private PayRateRepository repo;

    @InjectMocks
    private PayRateService service;

    private static PayRate muc(int id, int tu, int den, String tien, LocalDate from, LocalDate to) {
        PayRate r = new PayRate();
        r.setId(id);
        r.setGradeFrom((short) tu);
        r.setGradeTo((short) den);
        r.setAmount(new BigDecimal(tien));
        r.setEffectiveFrom(from);
        r.setEffectiveTo(to);
        return r;
    }

    @Test
    @DisplayName("Xóa mức chưa áp dụng thì MỞ LẠI mức cũ mà nó đã đóng")
    void xoa_muc_tuong_lai_thi_mo_lai_muc_cu() {
        LocalDate apDung = LocalDate.now().plusMonths(2);
        PayRate cu = muc(1, 1, 5, "115000", LocalDate.of(2020, 1, 1), apDung.minusDays(1));
        PayRate moi = muc(3, 1, 5, "130000", apDung, null);
        when(repo.findById(3)).thenReturn(Optional.of(moi));
        when(repo.findAllByOrderByEffectiveFromDescGradeFromAsc()).thenReturn(List.of(moi, cu));

        service.delete(3);

        assertThat(cu.getEffectiveTo())
                .as("mức cũ phải được mở lại, nếu không bảng giá thủng từ %s trở đi", apDung)
                .isNull();
        verify(repo).delete(moi);
    }

    @Test
    @DisplayName("Không có mức cũ nào bị đóng thì chỉ xóa, không đụng ai")
    void khong_co_muc_cu_thi_chi_xoa() {
        LocalDate apDung = LocalDate.now().plusMonths(2);
        PayRate moi = muc(3, 1, 5, "130000", apDung, null);
        PayRate khacKhoi = muc(2, 6, 9, "125000", LocalDate.of(2020, 1, 1), null);
        when(repo.findById(3)).thenReturn(Optional.of(moi));
        when(repo.findAllByOrderByEffectiveFromDescGradeFromAsc()).thenReturn(List.of(moi, khacKhoi));

        service.delete(3);

        assertThat(khacKhoi.getEffectiveTo())
                .as("khối khác thì không được đụng tới")
                .isNull();
        verify(repo).delete(moi);
    }

    @Test
    @DisplayName("Mức ĐÃ áp dụng thì không xóa được — nó là căn cứ của lương đã trả")
    void muc_da_ap_dung_thi_khong_xoa_duoc() {
        PayRate dangDung = muc(1, 1, 5, "115000", LocalDate.of(2020, 1, 1), null);
        when(repo.findById(1)).thenReturn(Optional.of(dangDung));

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã có hiệu lực");
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("Tăng giá thì mức cũ bị đóng ở ngày LIỀN TRƯỚC, hai mức không cùng phủ một ngày")
    void tang_gia_thi_dong_muc_cu_o_ngay_lien_truoc() {
        LocalDate apDung = LocalDate.of(2026, 10, 1);
        PayRate cu = muc(1, 1, 5, "115000", LocalDate.of(2020, 1, 1), null);
        when(repo.findAllByOrderByEffectiveFromDescGradeFromAsc()).thenReturn(new ArrayList<>(List.of(cu)));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.create(new PayRateRequest((short) 1, (short) 5, new BigDecimal("130000"), apDung, null));

        assertThat(cu.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
