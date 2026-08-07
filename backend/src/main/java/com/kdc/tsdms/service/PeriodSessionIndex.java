package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.repository.PeriodRepository;
import java.util.HashMap;
import java.util.Map;

/**
 * Quy đổi SỐ TIẾT TRONG NGÀY (PeriodNumber, 1..n) sang SỐ TIẾT TRONG BUỔI — buổi chiều đánh
 * lại từ 1, đúng cách nhà trường gọi tên ("Chiều · Tiết 5" chứ không phải "Tiết 10").
 *
 * <p>Trước đây frontend tự trừ hằng số 5 vì giả định mọi trường có đúng 5 tiết sáng. Giả định
 * đó không phải luật: khung tiết là dữ liệu VẬN HÀNH của từng trường. Tính ở backend — nơi
 * biết khung thật — rồi trả kèm DTO, frontend chỉ việc hiển thị.
 *
 * <p>Không phải bean Spring: mỗi lần map một danh sách thì tạo một thể hiện, cache số tiết
 * sáng theo trường trong phạm vi lần map đó (danh sách thường chỉ đụng vài trường).
 */
public final class PeriodSessionIndex {

    private static final String AFTERNOON = "AFTERNOON";

    private final PeriodRepository periodRepo;
    private final Map<Integer, Integer> morningCountBySchool = new HashMap<>();

    public PeriodSessionIndex(PeriodRepository periodRepo) {
        this.periodRepo = periodRepo;
    }

    /**
     * Tiết thứ mấy trong buổi của nó. Sáng = giữ nguyên số tiết; chiều = trừ đi số tiết sáng
     * của CHÍNH trường đó.
     *
     * @return null nếu không đủ dữ liệu để quy đổi (tiết đã bị xóa mềm, thiếu số tiết...)
     */
    public Short of(Period period) {
        if (period == null || period.getPeriodNumber() == null) {
            return null;
        }
        if (!AFTERNOON.equals(period.getSessionType())) {
            return period.getPeriodNumber();
        }
        int morning = morningCountBySchool.computeIfAbsent(
                period.getSchoolId(), id -> periodRepo.countBySchoolIdAndSessionTypeAndDeletedFalse(id, "MORNING"));
        return (short) (period.getPeriodNumber() - morning);
    }
}
