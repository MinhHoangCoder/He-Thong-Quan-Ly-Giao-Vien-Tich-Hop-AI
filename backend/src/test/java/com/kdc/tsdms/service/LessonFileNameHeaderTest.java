package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;

/**
 * Tên file tải về đi thẳng từ tên NGƯỜI DÙNG đặt lúc upload vào header
 * {@code Content-Disposition} — chỗ này từng ghép chuỗi tay nên vừa hỏng tên tiếng Việt vừa
 * hở đường chèn header giả. Bộ test này khóa lại hành vi đúng để sau không ai ghép tay lại.
 */
class LessonFileNameHeaderTest {

    /** Dựng header y hệt cách LessonService.openFile đang làm. */
    private static String header(String rawFileName) {
        return ContentDisposition.attachment()
                .filename(LessonService.safeFileName(rawFileName), StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    @Test
    void controlCharsAreStripped() {
        // Tên nặn thủ công qua curl/Postman: xuống dòng giữa header = chèn header giả.
        String h = header("bao-cao\r\nX-Injected: 1.pdf");

        assertThat(h).doesNotContain("\r").doesNotContain("\n");
        assertThat(h).contains("X-Injected"); // còn nguyên như một PHẦN CỦA TÊN, không thành header
    }

    @Test
    void quoteInNameDoesNotBreakHeader() {
        // Dấu " chưa escape sẽ đóng sớm giá trị filename -> trình duyệt đọc sai tên.
        assertThat(header("ke\"hoach.pdf")).contains("\\\"");
    }

    @Test
    void vietnameseNameKeepsAccentsViaRfc5987() {
        String h = header("Giáo án Toán lớp 5.pdf");

        // filename* giữ đủ dấu cho trình duyệt hiện đại...
        assertThat(h).contains("filename*=UTF-8''");
        assertThat(h).contains("Gi%C3%A1o");
        // ...và vẫn kèm filename= ASCII làm bản dự phòng.
        assertThat(h).contains("filename=\"");
    }

    @Test
    void blankOrNullNameFallsBackToDefault() {
        assertThat(LessonService.safeFileName(null)).isEqualTo("tai-lieu");
        assertThat(LessonService.safeFileName("   ")).isEqualTo("tai-lieu");
        assertThat(LessonService.safeFileName("\r\n")).isEqualTo("tai-lieu");
    }

    @Test
    void normalNameIsUnchanged() {
        assertThat(LessonService.safeFileName("giao-an.pdf")).isEqualTo("giao-an.pdf");
    }
}
