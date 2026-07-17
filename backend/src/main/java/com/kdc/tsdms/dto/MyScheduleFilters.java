package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Options bộ lọc cho trang "Lịch dạy của tôi" (giáo viên tự phục vụ): danh sách TRƯỜNG và
 * LỚP mà chính giáo viên đó có buổi dạy đã duyệt. Không có danh sách GV (đã cố định là người
 * đang đăng nhập). Lớp mang kèm {@code schoolId} để frontend lọc lớp theo trường đang chọn.
 */
public record MyScheduleFilters(List<OptionItem> schools, List<ClassOption> classes) {

    /** Mục lớp cho dropdown — kèm schoolId để lọc theo trường ở phía frontend. */
    public record ClassOption(Integer id, String name, Integer schoolId) {}
}
