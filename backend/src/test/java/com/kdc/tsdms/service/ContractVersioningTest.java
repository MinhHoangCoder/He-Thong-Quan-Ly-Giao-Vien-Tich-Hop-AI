package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.TeacherResponse;
import com.kdc.tsdms.entity.Contract;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.CertificateRepository;
import com.kdc.tsdms.repository.ContractRepository;
import com.kdc.tsdms.repository.RefreshTokenRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hợp đồng giáo viên phải ĐÓNG BẢN CŨ — MỞ BẢN MỚI, không ghi đè.
 *
 * <p>Bản cũ set thẳng các trường lên chính dòng đang có: sửa mức lương xong là con số cũ biến
 * mất, không thùng rác, không nhật ký. Điều đáng sợ ở lỗi này là nó KHÔNG có triệu chứng —
 * màn hình vẫn hiện một hợp đồng trông hoàn toàn bình thường, chỉ là nội dung đã khác, và
 * không ai biết cho tới lúc có tranh chấp lương mới phát hiện không tra lại được gì.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractVersioningTest {

    private static final int GV = 7;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private CertificateRepository ceRepo;

    @Mock
    private ContractRepository contractRepo;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    @Mock
    private AssignmentRepository assignmentRepo;

    @Mock
    private ScheduleRepository scheduleRepo;

    @InjectMocks
    private TeacherService service;

    private Contract hopDongDangCo(String so, String luong) {
        Teacher t = new Teacher();
        t.setId(GV);
        when(teacherRepo.findByIdAndDeletedFalse(GV)).thenReturn(Optional.of(t));

        Contract c = new Contract();
        c.setId(1);
        c.setTeacherId(GV);
        c.setContractNo(so);
        c.setStartDate(LocalDate.of(2026, 1, 1));
        c.setBaseSalary(new BigDecimal(luong));
        c.setStatus("ACTIVE");
        when(contractRepo.findByTeacherIdAndDeletedFalse(GV)).thenReturn(Optional.of(c));
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contractRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        return c;
    }

    private TeacherResponse.ContractRequest yeuCau(String so, String luong) {
        TeacherResponse.ContractRequest r = new TeacherResponse.ContractRequest();
        r.setContractNo(so);
        r.setStartDate(LocalDate.of(2026, 1, 1));
        r.setBaseSalary(new BigDecimal(luong));
        return r;
    }

    @Test
    void suaLuong_thiDONGbanCu_chuKhongGhiDeLenNo() {
        Contract cu = hopDongDangCo("HD-001", "8000000");

        service.upsertContract(GV, yeuCau("HD-001", "9500000"));

        // Bản cũ phải còn nguyên số tiền của nó — đây chính là thứ bản cũ làm mất.
        assertThat(cu.getBaseSalary()).isEqualByComparingTo("8000000");
        assertThat(cu.getStatus()).isEqualTo("TERMINATED");
        assertThat(cu.isDeleted()).as("đã bị thay thế").isTrue();
        assertThat(cu.getDeletedAt()).isNotNull();
    }

    @Test
    void suaLuong_thiTAObanMoi_giuNguyenSoHopDong() {
        hopDongDangCo("HD-001", "8000000");

        service.upsertContract(GV, yeuCau("HD-001", "9500000"));

        ArgumentCaptor<Contract> bat = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepo).save(bat.capture());
        Contract moi = bat.getValue();
        assertThat(moi.getId()).as("phải là dòng MỚI, không phải dòng cũ").isNull();
        assertThat(moi.getContractNo()).isEqualTo("HD-001");
        assertThat(moi.getBaseSalary()).isEqualByComparingTo("9500000");
        assertThat(moi.getStatus()).isEqualTo("ACTIVE");
        assertThat(moi.isDeleted()).isFalse();
    }

    @Test
    void phaiFLUSHbanCuTruocKhiChenBanMoi() {
        // Hibernate xếp INSERT trước UPDATE. Không flush thì dòng mới vào DB khi dòng cũ vẫn
        // IsDeleted = 0, và UX_Contract_No_Active (V37) thấy hai hợp đồng sống trùng số.
        hopDongDangCo("HD-001", "8000000");

        service.upsertContract(GV, yeuCau("HD-001", "9500000"));

        verify(contractRepo).saveAndFlush(any());
    }

    @Test
    void luuMaKhongSuaGi_thiKHONGdeRaPhienBanMoi() {
        // Lịch sử đầy bản trùng nhau cũng vô dụng ngang không có lịch sử.
        Contract cu = hopDongDangCo("HD-001", "8000000");

        service.upsertContract(GV, yeuCau("HD-001", "8000000"));

        assertThat(cu.isDeleted()).isFalse();
        assertThat(cu.getStatus()).isEqualTo("ACTIVE");
        verify(contractRepo, never()).save(any());
        verify(contractRepo, never()).saveAndFlush(any());
    }

    @Test
    void tienBangNhauNhungKhacSoLe_thiVanTinhLaKhongDoi() {
        // DB khai DECIMAL(18,2) nên đọc lên là 8000000.00, form gửi lên là 8000000.
        // BigDecimal.equals bảo hai số đó KHÁC nhau, mỗi lần bấm Lưu đẻ một bản y hệt.
        Contract cu = hopDongDangCo("HD-001", "8000000.00");

        service.upsertContract(GV, yeuCau("HD-001", "8000000"));

        assertThat(cu.isDeleted())
                .as("8000000.00 và 8000000 là cùng một số tiền")
                .isFalse();
        verify(contractRepo, never()).save(any());
    }

    @Test
    void chuaCoHopDong_thiChiTaoMoi_khongDongGiCa() {
        Teacher t = new Teacher();
        t.setId(GV);
        when(teacherRepo.findByIdAndDeletedFalse(GV)).thenReturn(Optional.of(t));
        when(contractRepo.findByTeacherIdAndDeletedFalse(GV)).thenReturn(Optional.empty());
        when(contractRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsertContract(GV, yeuCau("HD-001", "8000000"));

        verify(contractRepo).save(any());
        verify(contractRepo, never()).saveAndFlush(any());
    }
}
