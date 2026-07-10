package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Gói dữ liệu tổng hợp cho trang Bảng điều khiển (admin) — tất cả tính TRỰC TIẾP
 * từ DB. Chỗ nào chưa có dữ liệu thật thì trả 0 / null (FE hiển thị "—") thay vì
 * số giả.
 */
public record DashboardResponse(
        List<StatCard> stats,
        ChartData chart,
        List<SideStat> sideStats,
        List<AssignmentRow> recentAssignments,
        List<ScheduleRow> todaySchedule,
        List<TopTeacher> topTeachers) {

    /** Thẻ chỉ số lớn ở đầu trang. {@code trend} null = chưa đủ dữ liệu để so sánh. */
    public record StatCard(
            String key, String icon, String label, long value, String hint, Double trend, String color) {}

    /** Biểu đồ đường: nhãn trục X + nhiều series (mỗi nhóm môn một series). */
    public record ChartData(List<String> labels, List<ChartSeries> series) {}

    public record ChartSeries(String name, String color, List<Long> data) {}

    /** Thẻ chỉ số phụ (có sparkline). {@code value} là chuỗi đã định dạng sẵn. */
    public record SideStat(String key, String label, String value, Double trend, List<Long> data, String color) {}

    /** Một dòng bảng "Phân công gần đây". {@code tone}: ok | wait | no. */
    public record AssignmentRow(
            Integer id, String teacher, String school, String subject, String date, String statusLabel, String tone) {}

    /** Một buổi trong "Lịch dạy hôm nay". */
    public record ScheduleRow(Long id, String time, String teacher, String subject, String school, String color) {}

    /** Giáo viên nổi bật theo giờ dạy trong tháng. */
    public record TopTeacher(Integer id, String name, String subject, long hours, String initials) {}
}
