package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.RoomRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.StudentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Bốn luật của màn Quản lý trường mà hỏng thì không có lỗi nào bắn ra — chỉ dữ liệu sai âm thầm:
 * ghép tên theo cấp học, trùng tên trong một chi nhánh, hết hạn hợp đồng tính theo NGÀY, và xóa
 * vĩnh viễn.
 */
@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    private static final LocalDate HOM_NAY = LocalDate.of(2026, 8, 21);

    @Nested
    @DisplayName("Ghép tên theo cấp học")
    class GhepTen {

        @Test
        void tenRieng_thiThemTienTo() {
            assertThat(SchoolService.ghepTenTheoCap("Ban Mai", "THCS")).isEqualTo("THCS Ban Mai");
        }

        @Test
        void goSanTienTo_thiKhongNhanDoi() {
            // Người dùng quen gõ cả cụm "THCS Ban Mai" vào ô tên; không cắt thì ra "THCS THCS Ban Mai".
            assertThat(SchoolService.ghepTenTheoCap("THCS Ban Mai", "THCS")).isEqualTo("THCS Ban Mai");
        }

        @Test
        void tienToVietDayDuVaCoDau_thiVanCatDuoc() {
            assertThat(SchoolService.ghepTenTheoCap("Trường Trung học cơ sở Ba Đình", "THCS"))
                    .isEqualTo("THCS Ba Đình");
        }

        @Test
        void tenChiCoDungTienTo_thiGiuNguyen() {
            // "THCS" trơ trọi mà cắt tiền tố là còn chuỗi rỗng — thà giữ nguyên cái người dùng gõ.
            assertThat(SchoolService.ghepTenTheoCap("THCS", "THCS")).isEqualTo("THCS");
        }

        @Test
        void khongChonCap_thiGiuNguyenTen() {
            assertThat(SchoolService.ghepTenTheoCap("Ban Mai", null)).isEqualTo("Ban Mai");
        }

        @Test
        void suyCapTuTen_xetTHCS_truocTH() {
            // "THCS" cũng bắt đầu bằng "TH" — xét sai thứ tự là trường THCS nhận khung tiểu học.
            assertThat(SchoolService.capTheoTen("THCS Ban Mai")).isEqualTo("THCS");
            assertThat(SchoolService.capTheoTen("TH Ban Mai")).isEqualTo("TH");
            assertThat(SchoolService.capTheoTen("Ban Mai")).isNull();
        }
    }

    @Nested
    @DisplayName("Trạng thái suy theo ngày")
    class TrangThaiTheoNgay {

        @Test
        void dangHoatDongMaHopDongQuaHan_thiTinhLaHetHan() {
            School s = truong(School.ACTIVE, LocalDate.of(2026, 4, 11));
            assertThat(s.effectiveStatus(HOM_NAY)).isEqualTo(School.EXPIRED);
            assertThat(s.conHopTac(HOM_NAY)).isFalse();
        }

        @Test
        void hopDongConHan_thiVanHoatDong() {
            School s = truong(School.ACTIVE, LocalDate.of(2027, 5, 25));
            assertThat(s.effectiveStatus(HOM_NAY)).isEqualTo(School.ACTIVE);
            assertThat(s.conHopTac(HOM_NAY)).isTrue();
        }

        @Test
        void hetHanDungHomNay_thiVanConHieuLuc() {
            // Hợp đồng hết hạn NGÀY 21 thì ngày 21 vẫn còn dạy — chỉ từ ngày 22 mới hết.
            School s = truong(School.ACTIVE, HOM_NAY);
            assertThat(s.conHopTac(HOM_NAY)).isTrue();
            assertThat(s.soNgayConLai(HOM_NAY)).isZero();
        }

        @Test
        void nguoiDungTuDatNgungHoatDong_thiKhongDoiThanhHetHan() {
            // Hai thứ khác nhau về nghiệp vụ: ngừng hợp tác vs hết hạn hợp đồng.
            School s = truong(School.INACTIVE, LocalDate.of(2020, 1, 1));
            assertThat(s.effectiveStatus(HOM_NAY)).isEqualTo(School.INACTIVE);
        }

        @Test
        void chuaNhapHanHopDong_thiKhongCoSoNgayConLai() {
            School s = truong(School.ACTIVE, null);
            assertThat(s.soNgayConLai(HOM_NAY)).isNull();
            assertThat(s.conHopTac(HOM_NAY)).isTrue();
        }

        private School truong(String status, LocalDate hetHan) {
            School s = new School();
            s.setStatus(status);
            s.setContractEndDate(hetHan);
            return s;
        }
    }

    @Nested
    @DisplayName("Tạo / sửa / xóa vĩnh viễn")
    class NghiepVu {

        @Mock
        private SchoolRepository schoolRepo;

        @Mock
        private BranchRepository branchRepo;

        @Mock
        private SchoolClassRepository classRepo;

        @Mock
        private AssignmentRepository assignmentRepo;

        @Mock
        private ServiceContractRepository serviceContractRepo;

        @Mock
        private StudentRepository studentRepo;

        @Mock
        private PeriodRepository periodRepo;

        @Mock
        private RoomRepository roomRepo;

        @Mock
        private PeriodService periodService;

        @Mock
        private AuditService auditService;

        @InjectMocks
        private SchoolService service;

        @Test
        void tao_luuTenDaGhepCapHoc() {
            chiNhanhTonTai();
            luuTraVeChinhNo();

            service.create(request("Ban Mai", "THCS"));

            assertThat(daLuu().getName()).isEqualTo("THCS Ban Mai");
        }

        @Test
        void tao_trungTenTrongCungChiNhanh_thiChan() {
            when(branchRepo.existsById(1)).thenReturn(true);
            when(schoolRepo.existsByBranchIdAndNameAndDeletedFalse(1, "THCS Ban Mai"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.create(request("Ban Mai", "THCS")))
                    .satisfies(SchoolServiceTest::laXungDot)
                    .hasMessageContaining("THCS Ban Mai");
            verify(schoolRepo, never()).save(any());
        }

        @Test
        void sua_vanGiuTienToCapHoc() {
            // Trước đây update lưu thẳng chuỗi gõ vào, nên mở form sửa số điện thoại rồi bấm Lưu
            // là tên rụng mất tiền tố cấp học.
            School dangCo = new School();
            dangCo.setId(7);
            dangCo.setName("THCS Ban Mai");
            when(schoolRepo.findByIdAndDeletedFalse(7)).thenReturn(Optional.of(dangCo));
            chiNhanhTonTai();
            luuTraVeChinhNo();

            service.update(7, request("Trường THCS Ban Mai", null));

            assertThat(daLuu().getName()).isEqualTo("THCS Ban Mai");
        }

        /* ── tiện ích dựng dữ liệu ── */

        private void chiNhanhTonTai() {
            when(branchRepo.existsById(1)).thenReturn(true);
            when(branchRepo.findAllById(List.of(1))).thenReturn(List.of(chiNhanh()));
        }

        private void luuTraVeChinhNo() {
            when(schoolRepo.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        private void trongThungRac() {
            School s = new School();
            s.setId(7);
            s.setName("THCS Ban Mai");
            when(schoolRepo.findByIdAndDeletedTrue(7)).thenReturn(Optional.of(s));
        }

        private School daLuu() {
            ArgumentCaptor<School> captor = ArgumentCaptor.forClass(School.class);
            verify(schoolRepo).save(captor.capture());
            return captor.getValue();
        }

        private Branch chiNhanh() {
            Branch b = new Branch();
            b.setId(1);
            b.setName("Chi nhánh Hải Phòng");
            return b;
        }

        private SchoolRequest request(String name, String capHoc) {
            return new SchoolRequest(1, name, null, null, null, null, null, null, School.ACTIVE, capHoc);
        }
    }

    private static void laXungDot(Throwable e) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
