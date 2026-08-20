package com.kdc.tsdms.dto;

import java.util.List;

/**
 * Dữ liệu nạp form "Tạo phân công": danh sách GV, môn, trường.
 *
 * <p>Mỗi mục mang theo thông tin PHỤ để ô tìm kiếm hiển thị được dòng hai — thứ quyết định
 * người xếp lịch chọn ai, chứ không phải cái tên. Nạp một lần rồi lọc tại chỗ ở frontend:
 * dữ liệu ở quy mô vài chục bản ghi nên gọi lại server theo từng phím gõ là phí.
 */
public record AssignmentFormOptions(
        List<TeacherOption> teachers, List<SubjectOption> subjects, List<SchoolOption> schools) {

    /**
     * @param code TÊN ĐĂNG NHẬP của giáo viên (gv01, gv02…) — bảng Teacher không có cột "mã giáo
     *     viên", nên đây là thứ gần nhất với một mã tra cứu được. KHÔNG hiển thị trên form (user
     *     chốt chỉ hiện tên), nhưng vẫn trả về để ô tìm kiếm gõ "gv07" ra đúng người.
     * @param weeklyPeriods tổng số tiết/tuần giáo viên đang gánh (các phiếu còn giữ chỗ) — con
     *     số quyết định có nên giao thêm việc hay không
     * @param schoolCount số trường giáo viên đang dạy
     * @param subjectIds các môn giáo viên DẠY ĐƯỢC (bảng TeacherSubject). Form dùng để lọc danh
     *     sách theo môn đang chọn — không có nó thì xếp lịch cho 90 người là 90 lần tra trí nhớ
     *     xem ai dạy được môn gì. Rỗng = chưa khai môn nào, form vẫn cho chọn nhưng có cảnh báo.
     */
    public record TeacherOption(
            Integer id,
            String name,
            String code,
            int weeklyPeriods,
            int schoolCount,
            java.util.List<Integer> subjectIds) {}

    /**
     * @param level "Tiểu học" | "THCS" — SUY từ khối lớp cao nhất của trường (1–5 là tiểu học),
     *     không hardcode theo SchoolId. null khi trường chưa có lớp nào.
     * @param classCount số lớp đang hoạt động
     * @param periodCount số tiết/ngày trong khung tiết của trường — 0 nghĩa là trường CHƯA có
     *     khung tiết, form phải báo rõ thay vì hiện một lưới trống không giải thích
     */
    public record SchoolOption(Integer id, String name, String level, int classCount, int periodCount) {}

    /**
     * @param categoryName tên nhóm môn (null nếu môn chưa xếp nhóm)
     */
    public record SubjectOption(Integer id, String name, String code, String categoryName) {}
}
