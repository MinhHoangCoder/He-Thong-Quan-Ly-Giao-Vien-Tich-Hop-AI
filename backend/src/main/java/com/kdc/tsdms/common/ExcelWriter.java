package com.kdc.tsdms.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Xuất một danh sách ra file .xlsx.
 *
 * <p>Vì sao xuất Ở SERVER chứ không dựng file trong trình duyệt: các màn Bảng lương và Chấm
 * công đều PHÂN TRANG Ở SERVER, nên trình duyệt chỉ đang giữ 10 dòng. Xuất từ đó ra thì được
 * đúng 10 dòng — một cái file trông có vẻ đúng nhưng thiếu 99% dữ liệu, và không có gì báo cho
 * người dùng biết.
 *
 * <p>Vì sao .xlsx chứ không CSV: CSV tiếng Việt mở bằng Excel hay vỡ font nếu thiếu BOM, số
 * tiền bị Excel tự đoán thành ngày tháng, và không có cách nào tô đậm dòng tiêu đề. Số ghi
 * xuống đây là SỐ THẬT (không phải chuỗi), nên kế toán cộng/lọc/pivot được ngay.
 *
 * <p>Dùng:
 *
 * <pre>{@code
 * return ExcelWriter.xuat("bang-luong_8-2026", List.of("Giáo viên", "Số tiết"), rows,
 *         r -> new Object[] {r.teacherName, r.taughtHours});
 * }</pre>
 */
public final class ExcelWriter {

    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GIO = DateTimeFormatter.ofPattern("HH:mm");

    /** Bề rộng cột tính theo 1/256 ký tự — trần để một ô ghi chú dài không kéo cột ra vô tận. */
    private static final int RONG_TOI_DA = 60 * 256;

    private ExcelWriter() {}

    /**
     * @param tenFile tên file KHÔNG kèm đuôi
     * @param tieuDe nhãn các cột
     * @param duLieu danh sách bản ghi
     * @param boc quy một bản ghi thành mảng giá trị theo đúng thứ tự cột
     */
    public static <T> ResponseEntity<ByteArrayResource> xuat(
            String tenFile, List<String> tieuDe, List<T> duLieu, Function<T, Object[]> boc) {
        try (Workbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Dữ liệu");
            CellStyle kieuTieuDe = kieuTieuDe(wb);
            CellStyle kieuTien = kieuSo(wb, "#,##0");

            Row header = sheet.createRow(0);
            for (int i = 0; i < tieuDe.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(tieuDe.get(i));
                c.setCellStyle(kieuTieuDe);
            }

            int r = 1;
            for (T item : duLieu) {
                Row row = sheet.createRow(r++);
                Object[] gt = boc.apply(item);
                for (int i = 0; i < gt.length; i++) {
                    ghi(row.createCell(i), gt[i], kieuTien);
                }
            }

            // Khóa dòng tiêu đề: bảng lương một tháng là 150 dòng, cuộn xuống mà mất tiêu đề
            // thì không ai biết cột nào là cột nào.
            sheet.createFreezePane(0, 1);
            if (!duLieu.isEmpty()) {
                sheet.setAutoFilter(
                        new org.apache.poi.ss.util.CellRangeAddress(0, duLieu.size(), 0, tieuDe.size() - 1));
            }
            for (int i = 0; i < tieuDe.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, RONG_TOI_DA));
            }

            wb.write(out);
            return dongGoi(tenFile, out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Không dựng được file Excel", e);
        }
    }

    /**
     * Ghi đúng KIỂU của giá trị, không quy hết về chuỗi — số phải cộng được trong Excel.
     *
     * <p>Dùng {@code instanceof} chứ không phải switch theo mẫu: dự án biên dịch ở {@code
     * -source 17}, mà pattern matching cho switch tới Java 21 mới ra khỏi bản xem trước.
     */
    private static void ghi(Cell c, Object v, CellStyle kieuTien) {
        if (v == null) {
            c.setBlank();
        } else if (v instanceof Number n) {
            c.setCellValue(n.doubleValue());
            c.setCellStyle(kieuTien);
        } else if (v instanceof LocalDate d) {
            c.setCellValue(d.format(NGAY));
        } else if (v instanceof LocalTime t) {
            c.setCellValue(t.format(GIO));
        } else {
            c.setCellValue(String.valueOf(v));
        }
    }

    private static CellStyle kieuTieuDe(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle kieuSo(Workbook wb, String dinhDang) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat(dinhDang));
        return s;
    }

    /**
     * Tên file có dấu tiếng Việt phải đi qua {@code filename*} theo RFC 5987, nếu không trình
     * duyệt lưu thành một chuỗi ký tự vỡ. Vẫn kèm {@code filename=} không dấu làm bản dự phòng
     * cho trình duyệt cũ.
     */
    private static ResponseEntity<ByteArrayResource> dongGoi(String tenFile, byte[] bytes) {
        String ten = tenFile + ".xlsx";
        String maHoa = java.net.URLEncoder.encode(ten, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ten.replaceAll("[^A-Za-z0-9._-]", "_") + "\"; filename*=UTF-8''"
                                + maHoa)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
