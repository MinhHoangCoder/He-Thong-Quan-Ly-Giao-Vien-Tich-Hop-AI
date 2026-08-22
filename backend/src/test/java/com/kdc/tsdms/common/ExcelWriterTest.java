package com.kdc.tsdms.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * FILE EXCEL XUẤT RA PHẢI MỞ ĐƯỢC VÀ CỘNG ĐƯỢC.
 *
 * <p>Điều dễ làm sai nhất khi xuất bảng tính không phải là thiếu dòng, mà là ghi mọi thứ thành
 * CHUỖI. File vẫn mở ra bình thường, nhìn vẫn đúng, nhưng kế toán bôi cột "Thực nhận" thì
 * Excel không cộng được — và không có gì báo lỗi, chỉ là ô tổng trống.
 *
 * <p>Vì vậy test này đọc lại file bằng chính Apache POI và kiểm tra KIỂU của từng ô, không chỉ
 * kiểm tra nội dung.
 */
class ExcelWriterTest {

    private record Dong(String ten, BigDecimal tien, Integer soTiet, LocalDate ngay, LocalTime gio, String ghiChu) {}

    private static final List<String> TIEU_DE =
            List.of("Giáo viên", "Thực nhận", "Số tiết", "Ngày", "Giờ vào", "Ghi chú");

    private static Sheet docLai(ResponseEntity<ByteArrayResource> res) throws Exception {
        byte[] bytes = res.getBody().getByteArray();
        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes));
        return wb.getSheetAt(0);
    }

    private static ResponseEntity<ByteArrayResource> xuatMau(List<Dong> duLieu) {
        return ExcelWriter.xuat("bang-luong_8-2026", TIEU_DE, duLieu, d ->
                new Object[] {d.ten(), d.tien(), d.soTiet(), d.ngay(), d.gio(), d.ghiChu()});
    }

    @Test
    @DisplayName("Số ghi xuống là SỐ THẬT, không phải chuỗi — kế toán phải cộng được")
    void so_phai_la_so_that() throws Exception {
        Sheet sheet = docLai(xuatMau(List.of(new Dong(
                "Nguyễn Văn An",
                new BigDecimal("16533500"),
                78,
                LocalDate.of(2026, 8, 17),
                LocalTime.of(7, 0),
                null))));

        Row r = sheet.getRow(1);
        assertThat(r.getCell(1).getCellType())
                .as("cột tiền phải là NUMERIC, ghi thành chuỗi thì Excel không cộng được")
                .isEqualTo(CellType.NUMERIC);
        assertThat(r.getCell(1).getNumericCellValue()).isEqualTo(16_533_500d);
        assertThat(r.getCell(2).getNumericCellValue()).isEqualTo(78d);
    }

    @Test
    @DisplayName("Tiêu đề đúng thứ tự cột và có đủ số dòng dữ liệu")
    void du_dong_va_dung_tieu_de() throws Exception {
        List<Dong> duLieu = List.of(
                new Dong("A", BigDecimal.ONE, 1, LocalDate.of(2026, 1, 1), LocalTime.NOON, "x"),
                new Dong("B", BigDecimal.TEN, 2, LocalDate.of(2026, 1, 2), LocalTime.NOON, "y"),
                new Dong("C", BigDecimal.ZERO, 3, LocalDate.of(2026, 1, 3), LocalTime.NOON, "z"));

        Sheet sheet = docLai(xuatMau(duLieu));

        assertThat(sheet.getLastRowNum()).as("3 dòng dữ liệu + 1 dòng tiêu đề").isEqualTo(3);
        for (int i = 0; i < TIEU_DE.size(); i++) {
            assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(TIEU_DE.get(i));
        }
        assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("C");
    }

    @Test
    @DisplayName("Ngày và giờ ghi theo định dạng Việt Nam, ô trống thì để trống")
    void ngay_gio_va_o_trong() throws Exception {
        Sheet sheet = docLai(xuatMau(
                List.of(new Dong("A", BigDecimal.ONE, 1, LocalDate.of(2026, 8, 17), LocalTime.of(14, 5), null))));

        Row r = sheet.getRow(1);
        assertThat(r.getCell(3).getStringCellValue()).isEqualTo("17/08/2026");
        assertThat(r.getCell(4).getStringCellValue()).isEqualTo("14:05");
        assertThat(r.getCell(5).getCellType()).as("ghi chú null thì để ô trống").isEqualTo(CellType.BLANK);
    }

    @Test
    @DisplayName("Tên file tiếng Việt đi qua filename* (RFC 5987), không thì trình duyệt lưu tên vỡ")
    void ten_file_tieng_viet() {
        ResponseEntity<ByteArrayResource> res =
                ExcelWriter.xuat("bảng-lương_8-2026", List.of("Cột"), List.of("giá trị"), v -> new Object[] {v});

        String cd = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cd).contains("filename*=UTF-8''");
        assertThat(cd).as("vẫn phải có bản không dấu làm dự phòng").contains("filename=\"b_ng-l_");
    }

    @Test
    @DisplayName("Danh sách rỗng vẫn ra file mở được, chỉ có dòng tiêu đề")
    void danh_sach_rong_van_ra_file() throws Exception {
        Sheet sheet = docLai(xuatMau(List.of()));
        assertThat(sheet.getLastRowNum()).isZero();
        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Giáo viên");
    }
}
