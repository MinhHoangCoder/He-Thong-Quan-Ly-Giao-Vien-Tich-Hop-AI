package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.Holiday;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Một dòng lịch nghỉ trả về cho giao diện.
 *
 * <p>{@code needsReview} soi ghi chú tìm dấu {@value #REVIEW_MARK}: các ngày suy từ âm lịch
 * (Tết, Giỗ Tổ) và ngày nghỉ bù 2/9 do Chính phủ chốt riêng từng năm được seed kèm dấu này ở
 * V29. Không đẩy ra giao diện thì chẳng ai nhớ đi đối chiếu, và lịch dạy cả năm học sẽ sinh
 * theo một ngày đoán mò.
 */
public record HolidayResponse(
        Integer id,
        LocalDate fromDate,
        LocalDate toDate,
        String name,
        String kind,
        Integer schoolId,
        String schoolName,
        String note,
        /** Số ngày của kỳ nghỉ (đã tính cả ngày đầu và ngày cuối). */
        long dayCount,
        /** Ghi chú có dấu "[CẦN RÀ SOÁT]" — ngày chưa được đối chiếu nguồn chính thức. */
        boolean needsReview,
        Instant createdAt,
        Instant updatedAt) {

    private static final String REVIEW_MARK = "[CẦN RÀ SOÁT]";

    public static HolidayResponse fromEntity(Holiday h, String schoolName) {
        return new HolidayResponse(
                h.getId(),
                h.getFromDate(),
                h.getToDate(),
                h.getName(),
                h.getKind(),
                h.getSchoolId(),
                schoolName,
                h.getNote(),
                ChronoUnit.DAYS.between(h.getFromDate(), h.getToDate()) + 1,
                h.getNote() != null && h.getNote().contains(REVIEW_MARK),
                h.getCreatedAt(),
                h.getUpdatedAt());
    }
}
