package com.kdc.tsdms.dto;

import java.time.Instant;

/**
 * Một dòng trong Thùng rác của Kho bài giảng.
 *
 * <p>Gọn hơn {@link LessonSummary} vì màn thùng rác chỉ cần đủ để NHẬN RA bài nào là bài mình
 * lỡ xóa: tiêu đề, môn, khối, xóa lúc nào, và còn bao nhiêu file đính kèm sẽ về theo. Nội dung
 * rich text hay mô tả dài không giúp gì cho quyết định "khôi phục hay không".
 *
 * @param soFileKemTheo số file đính kèm sẽ được khôi phục cùng bài giảng — con số này là lý do
 *     chính khiến người ta bấm Khôi phục thay vì soạn lại.
 */
public record LessonTrashItem(
        Integer id,
        String title,
        Integer subjectId,
        String subjectName,
        String gradeLevel,
        String status,
        int soFileKemTheo,
        Instant deletedAt) {}
