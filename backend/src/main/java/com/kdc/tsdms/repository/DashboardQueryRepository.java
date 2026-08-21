package com.kdc.tsdms.repository;

import com.kdc.tsdms.dto.DashboardAnalyticsResponse.DiemThang;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse.LatCat;
import com.kdc.tsdms.dto.DashboardBreakdownRow;
import com.kdc.tsdms.dto.DashboardFilter;
import com.kdc.tsdms.dto.DashboardOperationsResponse.DongPhanCong;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn thống kê cho Bảng điều khiển. Viết SQL tay để đẩy hết GROUP BY/SUM/COUNT xuống DB —
 * gom bằng Java Stream thì phải nạp cả bảng Schedule (hơn 27.000 dòng) lên bộ nhớ.
 *
 * <p>Quy ước chung:
 *
 * <ul>
 *   <li>"Buổi dạy" = Schedule đã DUYỆT, chưa xóa mềm. Buổi huỷ/từ chối chỉ dùng tính tỉ lệ duyệt.
 *   <li>Trường của buổi lấy từ Ô LỊCH trước, phân công sau (V27: một phiếu trải nhiều trường).
 *   <li>Chi phí phân bổ về từng buổi = NetAmount / TaughtHours của kỳ lương tương ứng.
 * </ul>
 */
@Repository
public class DashboardQueryRepository {

    /** Chiều phân tích của bảng ba tab. */
    public enum Chieu {
        GIAO_VIEN,
        TRUONG,
        MON
    }

    /**
     * Năm bảng cần có để biết một buổi dạy thuộc giáo viên / môn / trường nào. Tách riêng vì
     * truy vấn lịch sắp tới cũng cần đúng chỗ này nhưng KHÔNG cần chấm công và bảng lương.
     *
     * <p>LEFT JOIN AssignmentSlot phải đứng TRƯỚC JOIN School vì trường của buổi suy ra từ ô lịch.
     */
    private static final String FROM_BUOI_DAY_GON = """
            FROM Schedule sch
            JOIN Assignment a            ON a.Id = sch.AssignmentId
            LEFT JOIN AssignmentSlot slot ON slot.Id = sch.SourceSlotId
            JOIN Subject sj              ON sj.Id = a.SubjectId
            JOIN Teacher t               ON t.Id = sch.TeacherId
            JOIN School scl              ON scl.Id = COALESCE(slot.SchoolId, a.SchoolId)
            """;

    /**
     * Khối FROM cho các truy vấn thống kê: thêm chấm công và bảng lương. Nối Attendance an toàn
     * cho các phép COUNT vì có ràng buộc duy nhất UX_Attendance_ScheduleId (mỗi buổi tối đa 1
     * dòng chấm công).
     */
    private static final String FROM_BUOI_DAY = FROM_BUOI_DAY_GON + """
            LEFT JOIN Attendance att     ON att.ScheduleId = sch.Id
            LEFT JOIN Payroll pr         ON pr.TeacherId = sch.TeacherId
                                        AND pr.PeriodYear = YEAR(sch.StartTime)
                                        AND pr.PeriodMonth = MONTH(sch.StartTime)
            """;

    /**
     * Lọc PHẠM VI (chi nhánh / trường / nhóm môn). Tham số NULL nghĩa là "không lọc theo tiêu
     * chí đó". Tách khỏi khoảng thời gian vì bảng "Buổi dạy sắp tới" cần đúng phần này nhưng
     * mốc thời gian của nó là "từ hôm nay trở đi", không có giới hạn trên.
     */
    private static final String LOC_PHAM_VI = """
              AND (:branchId   IS NULL OR scl.BranchId  = :branchId)
              AND (:schoolId   IS NULL OR scl.Id        = :schoolId)
              AND (:categoryId IS NULL OR sj.CategoryId = :categoryId)
            """;

    /** Điều kiện lọc dùng chung cho các truy vấn thống kê: khoảng kỳ + phạm vi. */
    private static final String WHERE_LOC = """
            WHERE sch.IsDeleted = 0
              AND sch.StartTime >= :tuNgay
              AND sch.StartTime <  :denNgay
            """ + LOC_PHAM_VI;

    /** Thời lượng một buổi, quy ra giờ. */
    private static final String GIO = "(DATEDIFF(MINUTE, sch.StartTime, sch.EndTime) / 60.0)";

    /**
     * Chi phí lương phân bổ cho MỘT buổi dạy = NetAmount / TaughtHours.
     *
     * <p>BẪY: cột TaughtHours lưu SỐ TIẾT chứ không phải số giờ (xem PayrollService — trung tâm
     * trả theo tiết, tên cột là di sản). Chia xong còn nhân thêm thời lượng giờ là đếm trùng.
     *
     * <p>Chỉ buổi CÓ MẶT mới sinh chi phí vì mẫu số cũng chỉ đếm những buổi ấy; tính cả buổi vắng
     * thì tổng vượt cột "Thực nhận" của Bảng lương. Tháng chưa lập bảng lương tính 0.
     */
    private static final String CHI_PHI = """
            (CASE WHEN att.Status IN ('PRESENT','LATE') AND ISNULL(pr.TaughtHours, 0) > 0
                  THEN pr.NetAmount / pr.TaughtHours ELSE 0 END)""";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ═════════════════════════ THAM SỐ ═════════════════════════ */

    /** Tham số lọc phải khai kiểu tường minh: SQL Server từ chối tham số NULL không rõ kiểu. */
    private MapSqlParameterSource thamSo(DashboardFilter f) {
        return new MapSqlParameterSource()
                .addValue("tuNgay", f.from().atStartOfDay())
                .addValue("denNgay", f.to().plusDays(1).atStartOfDay())
                .addValue("branchId", f.branchId(), Types.INTEGER)
                .addValue("schoolId", f.schoolId(), Types.INTEGER)
                .addValue("categoryId", f.categoryId(), Types.INTEGER);
    }

    /* ═════════════════════════ CHỈ SỐ TỔNG HỢP ═════════════════════════ */

    /**
     * Toàn bộ số liệu của một kỳ trong MỘT câu lệnh. Gộp lại để chín con số chắc chắn thuộc
     * cùng một ảnh chụp dữ liệu — chạy rời nhau thì giữa hai câu lệnh có thể có người vừa duyệt
     * thêm một buổi, ra tỉ lệ lớn hơn 100%.
     */
    public ThongKeKy thongKeKy(DashboardFilter f) {
        String sql = """
                SELECT
                    buoiDuyet     = COUNT(CASE WHEN sch.Status = 'APPROVED' THEN 1 END),
                    buoiTatCa     = COUNT(*),
                    chiPhi        = ISNULL(SUM(CASE WHEN sch.Status = 'APPROVED' THEN %s END), 0),
                    ccTong        = COUNT(att.Id),
                    ccCoMat       = COUNT(CASE WHEN att.Status IN ('PRESENT','LATE') THEN 1 END),
                    ccDungGio     = COUNT(CASE WHEN att.Status = 'PRESENT' THEN 1 END)
                %s
                %s
                """.formatted(CHI_PHI, FROM_BUOI_DAY, WHERE_LOC);

        return jdbc.queryForObject(
                sql,
                thamSo(f),
                (rs, i) -> new ThongKeKy(
                        rs.getLong("buoiDuyet"),
                        rs.getLong("buoiTatCa"),
                        rs.getDouble("chiPhi"),
                        rs.getLong("ccTong"),
                        rs.getLong("ccCoMat"),
                        rs.getLong("ccDungGio")));
    }

    /** Số giáo viên đang làm việc (mẫu số của chỉ số khai thác nhân sự). */
    public long demGiaoVienHoatDong(Integer branchId) {
        return demTheoChiNhanh(
                "SELECT COUNT(*) FROM Teacher t WHERE t.IsDeleted = 0 AND t.Status = 'ACTIVE'"
                        + " AND (:branchId IS NULL OR t.BranchId = :branchId)",
                branchId);
    }

    /**
     * Số trường còn hợp đồng dịch vụ hiệu lực. Đếm theo hợp đồng chứ không theo bảng School vì
     * danh bạ có cả trường đã ngừng hợp tác.
     */
    public long demTruongDangHopDong(Integer branchId) {
        return demTheoChiNhanh("""
                SELECT COUNT(DISTINCT scl.Id)
                FROM School scl
                JOIN ServiceContract sv ON sv.SchoolId = scl.Id AND sv.Status = 'ACTIVE'
                WHERE scl.IsDeleted = 0 AND (:branchId IS NULL OR scl.BranchId = :branchId)
                """, branchId);
    }

    private long demTheoChiNhanh(String sql, Integer branchId) {
        Long n = jdbc.queryForObject(
                sql, new MapSqlParameterSource().addValue("branchId", branchId, Types.INTEGER), Long.class);
        return n == null ? 0 : n;
    }

    /* ═════════════════════════ BIỂU ĐỒ ═════════════════════════ */

    /** Chuỗi thời gian theo tháng cho biểu đồ cột + đường. */
    public List<DiemThang> theoThang(DashboardFilter f) {
        String sql = """
                SELECT
                    nam      = YEAR(sch.StartTime),
                    thang    = MONTH(sch.StartTime),
                    buoiDay  = COUNT(*),
                    gioGiang = ISNULL(SUM(%s), 0),
                    chiPhi   = ISNULL(SUM(%s), 0)
                %s
                %s
                  AND sch.Status = 'APPROVED'
                GROUP BY YEAR(sch.StartTime), MONTH(sch.StartTime)
                ORDER BY 1, 2
                """.formatted(GIO, CHI_PHI, FROM_BUOI_DAY, WHERE_LOC);

        return jdbc.query(
                sql,
                thamSo(f),
                (rs, i) -> new DiemThang(
                        "T" + rs.getInt("thang") + "/" + rs.getInt("nam"),
                        rs.getLong("buoiDay"),
                        rs.getDouble("gioGiang"),
                        rs.getDouble("chiPhi")));
    }

    /** Cơ cấu buổi dạy theo nhóm môn (biểu đồ tròn khuyết). */
    public List<LatCat> coCauNhomMon(DashboardFilter f) {
        String sql = """
                SELECT
                    id     = MIN(sc2.Id),
                    nhan   = ISNULL(sc2.Name, N'Chưa phân nhóm'),
                    giaTri = COUNT(*)
                %s
                LEFT JOIN SubjectCategory sc2 ON sc2.Id = sj.CategoryId
                %s
                  AND sch.Status = 'APPROVED'
                GROUP BY sc2.Name
                ORDER BY COUNT(*) DESC
                """.formatted(FROM_BUOI_DAY, WHERE_LOC);

        return jdbc.query(
                sql,
                thamSo(f),
                (rs, i) -> new LatCat((Integer) rs.getObject("id"), rs.getString("nhan"), rs.getLong("giaTri"), null));
    }

    /* ═════════════════════════ BẢNG PHÂN TÍCH ═════════════════════════ */

    /**
     * Bảng thống kê chi tiết theo một trong ba chiều. Cột nhóm lấy từ hằng số trong enum, KHÔNG
     * ghép từ chuỗi client gửi lên — đây là chỗ duy nhất trong lớp có mảnh SQL thay đổi được.
     */
    public List<DashboardBreakdownRow> phanTich(DashboardFilter f, Chieu chieu) {
        String cotId =
                switch (chieu) {
                    case GIAO_VIEN -> "t.Id";
                    case TRUONG -> "scl.Id";
                    case MON -> "sj.Id";
                };
        String cotTen =
                switch (chieu) {
                    case GIAO_VIEN -> "(t.LastName + N' ' + t.FirstName)";
                    case TRUONG -> "scl.Name";
                    case MON -> "sj.Name";
                };
        String cotPhu =
                switch (chieu) {
                    case GIAO_VIEN ->
                        "(CASE t.EmploymentType WHEN 'CO_HUU' THEN N'Cơ hữu'"
                                + " WHEN 'THINH_GIANG' THEN N'Thỉnh giảng' ELSE N'—' END)";
                    case TRUONG -> "ISNULL(scl.Address, N'—')";
                    case MON -> "ISNULL(sc2.Name, N'Chưa phân nhóm')";
                };
        String joinNhomMon = chieu == Chieu.MON ? "LEFT JOIN SubjectCategory sc2 ON sc2.Id = sj.CategoryId" : "";

        String sql = """
                SELECT
                    id        = %s,
                    ten       = %s,
                    phu       = %s,
                    buoiDay   = COUNT(CASE WHEN sch.Status = 'APPROVED' THEN 1 END),
                    buoiTatCa = COUNT(*),
                    gioGiang  = ISNULL(SUM(CASE WHEN sch.Status = 'APPROVED' THEN %s END), 0),
                    chiPhi    = ISNULL(SUM(CASE WHEN sch.Status = 'APPROVED' THEN %s END), 0),
                    ccTong    = COUNT(att.Id),
                    ccCoMat   = COUNT(CASE WHEN att.Status IN ('PRESENT','LATE') THEN 1 END)
                %s
                %s
                %s
                GROUP BY %s, %s, %s
                HAVING COUNT(CASE WHEN sch.Status = 'APPROVED' THEN 1 END) > 0
                ORDER BY COUNT(CASE WHEN sch.Status = 'APPROVED' THEN 1 END) DESC
                """.formatted(
                cotId, cotTen, cotPhu, GIO, CHI_PHI, FROM_BUOI_DAY, joinNhomMon, WHERE_LOC, cotId, cotTen, cotPhu);

        return jdbc.query(sql, thamSo(f), (rs, i) -> {
            long buoiDay = rs.getLong("buoiDay");
            long buoiTatCa = rs.getLong("buoiTatCa");
            long ccTong = rs.getLong("ccTong");
            return new DashboardBreakdownRow(
                    (Integer) rs.getObject("id"),
                    rs.getString("ten"),
                    rs.getString("phu"),
                    buoiDay,
                    rs.getDouble("gioGiang"),
                    buoiTatCa == 0 ? 0 : buoiDay * 100.0 / buoiTatCa,
                    ccTong == 0 ? null : rs.getLong("ccCoMat") * 100.0 / ccTong,
                    null, // điểm đánh giá ghép ở tầng service, xem diemDanhGiaTheo()
                    rs.getDouble("chiPhi"));
        });
    }

    /**
     * Điểm đánh giá trung bình theo giáo viên / theo trường. Tách khỏi truy vấn bảng chi tiết vì
     * đánh giá không gắn với buổi dạy — nối chung sẽ nhân bản mỗi buổi lên bằng số lượt đánh giá.
     */
    public Map<Integer, Double> diemDanhGiaTheo(DashboardFilter f, Chieu chieu) {
        if (chieu == Chieu.MON) {
            return Map.of();
        }
        String cot = chieu == Chieu.GIAO_VIEN ? "ev.TeacherId" : "ev.SchoolId";
        String sql = """
                SELECT khoa = %s, diem = AVG(CAST(ev.Score AS FLOAT))
                FROM TeacherEvaluation ev
                JOIN Teacher t ON t.Id = ev.TeacherId
                WHERE ev.IsDeleted = 0
                  AND ev.Score IS NOT NULL
                  AND %s IS NOT NULL
                  AND ev.CreatedAt >= :tuNgay AND ev.CreatedAt < :denNgay
                  AND (:branchId IS NULL OR t.BranchId = :branchId)
                GROUP BY %s
                """.formatted(cot, cot, cot);

        Map<Integer, Double> ketQua = new HashMap<>();
        jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("tuNgay", f.from().atStartOfDay())
                        .addValue("denNgay", f.to().plusDays(1).atStartOfDay())
                        .addValue("branchId", f.branchId(), Types.INTEGER),
                rs -> {
                    ketQua.put(rs.getInt("khoa"), rs.getDouble("diem"));
                });
        return ketQua;
    }

    /* ═════════════════════════ CẢNH BÁO ═════════════════════════ */

    /** Số buổi dạy đang chờ duyệt (mọi thời điểm — việc tồn đọng không có hạn dùng). */
    public long demLichChoDuyet() {
        return demDon("SELECT COUNT(*) FROM Schedule WHERE IsDeleted = 0 AND Status = 'PENDING'");
    }

    /** Hợp đồng lao động của giáo viên sẽ hết hạn trong {@code soNgay} ngày tới. */
    public long demHopDongSapHetHan(int soNgay) {
        return demDon("""
                SELECT COUNT(*) FROM Contract c
                JOIN Teacher t ON t.Id = c.TeacherId AND t.IsDeleted = 0 AND t.Status = 'ACTIVE'
                WHERE c.IsDeleted = 0 AND c.Status = 'ACTIVE'
                  AND c.EndDate IS NOT NULL
                  AND c.EndDate >= CAST(GETDATE() AS DATE)
                  AND c.EndDate <  DATEADD(DAY, %d, CAST(GETDATE() AS DATE))
                """.formatted(soNgay));
    }

    /** Hợp đồng dịch vụ với trường đã quá hạn mà chưa gia hạn. */
    public long demHopDongDichVuHetHan() {
        return demDon("SELECT COUNT(*) FROM ServiceContract WHERE Status = 'EXPIRED'");
    }

    /**
     * Buổi đã dạy nhưng chưa chấm công. Chỉ soi 30 ngày gần nhất — bới cả năm học ra con số
     * hàng nghìn thì không ai xử lý nổi.
     */
    public long demBuoiChuaChamCong() {
        return demDon("""
                SELECT COUNT(*) FROM Schedule sch
                WHERE sch.IsDeleted = 0 AND sch.Status = 'APPROVED'
                  AND sch.EndTime < GETDATE()
                  AND sch.EndTime >= DATEADD(DAY, -30, GETDATE())
                  AND NOT EXISTS (SELECT 1 FROM Attendance att WHERE att.ScheduleId = sch.Id)
                """);
    }

    /** Kỳ lương đã có dòng nhưng còn ở trạng thái nháp — chưa chốt thì chưa chi được. */
    public long demKyLuongChuaChot() {
        return demDon("SELECT COUNT(DISTINCT PeriodYear * 100 + PeriodMonth) FROM Payroll WHERE Status = 'DRAFT'");
    }

    /** Giáo viên đang làm việc nhưng không có buổi dạy nào trong kỳ — năng lực đang để không. */
    public long demGiaoVienKhongCoLich(DashboardFilter f) {
        String sql = """
                SELECT COUNT(*) FROM Teacher t
                WHERE t.IsDeleted = 0 AND t.Status = 'ACTIVE'
                  AND (:branchId IS NULL OR t.BranchId = :branchId)
                  AND NOT EXISTS (
                        SELECT 1 FROM Schedule sch
                        WHERE sch.TeacherId = t.Id AND sch.IsDeleted = 0 AND sch.Status = 'APPROVED'
                          AND sch.StartTime >= :tuNgay AND sch.StartTime < :denNgay)
                """;
        Long n = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("tuNgay", f.from().atStartOfDay())
                        .addValue("denNgay", f.to().plusDays(1).atStartOfDay())
                        .addValue("branchId", f.branchId(), Types.INTEGER),
                Long.class);
        return n == null ? 0 : n;
    }

    /** Trường còn hợp đồng nhưng không phát sinh buổi dạy nào trong kỳ. */
    public long demTruongKhongPhatSinh(DashboardFilter f) {
        String sql = """
                SELECT COUNT(*) FROM School scl
                JOIN ServiceContract sv ON sv.SchoolId = scl.Id AND sv.Status = 'ACTIVE'
                WHERE scl.IsDeleted = 0
                  AND (:branchId IS NULL OR scl.BranchId = :branchId)
                  AND NOT EXISTS (
                        SELECT 1 FROM Schedule sch
                        JOIN Assignment a ON a.Id = sch.AssignmentId
                        LEFT JOIN AssignmentSlot slot ON slot.Id = sch.SourceSlotId
                        WHERE COALESCE(slot.SchoolId, a.SchoolId) = scl.Id
                          AND sch.IsDeleted = 0 AND sch.Status = 'APPROVED'
                          AND sch.StartTime >= :tuNgay AND sch.StartTime < :denNgay)
                """;
        Long n = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("tuNgay", f.from().atStartOfDay())
                        .addValue("denNgay", f.to().plusDays(1).atStartOfDay())
                        .addValue("branchId", f.branchId(), Types.INTEGER),
                Long.class);
        return n == null ? 0 : n;
    }

    private long demDon(String sql) {
        Long n = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /* ═════════════════════════ KHU ĐIỀU HÀNH ═════════════════════════ */

    /**
     * {@code gioiHan} buổi dạy kế tiếp kể từ {@code tuNgay}, KHÔNG chặn giới hạn trên.
     *
     * <p>Bản đầu chặn ở "7 ngày tới" và trả về rỗng suốt kỳ nghỉ hè: buổi cuối của năm học là
     * 29/05, buổi đầu của năm sau là 07/09, ở giữa là hơn ba tháng trống. Một khối trống giữa
     * màn hình thì không phân biệt được với hệ thống hỏng.
     *
     * <p>Dùng lại {@link #LOC_PHAM_VI} nên khối này CÓ chịu bộ lọc trường / nhóm môn — lọc
     * "THCS Chu Văn An" mà bảng lịch vẫn liệt kê buổi của trường khác thì nhìn như hỏng.
     */
    public List<BuoiDayTho> lichSapToi(DashboardFilter f, LocalDate tuNgay, int gioiHan) {
        String sql = """
                SELECT TOP (:gioiHan)
                    sch.Id, sch.StartTime, sch.EndTime,
                    giaoVien = t.LastName + N' ' + t.FirstName,
                    mon      = sj.Name,
                    truong   = scl.Name
                %s
                WHERE sch.IsDeleted = 0
                  AND sch.Status = 'APPROVED'
                  AND sch.StartTime >= :tuNgay
                %s
                ORDER BY sch.StartTime, scl.Name
                """.formatted(FROM_BUOI_DAY_GON, LOC_PHAM_VI);

        return jdbc.query(
                sql,
                // Đè tuNgay của kỳ đang xem — bảng này luôn nhìn về phía trước. denNgay thừa lại
                // trong map cũng vô hại: JDBC chỉ bind những tham số thực sự có trong câu lệnh.
                thamSo(f).addValue("tuNgay", tuNgay.atStartOfDay()).addValue("gioiHan", gioiHan),
                (rs, i) -> new BuoiDayTho(
                        rs.getLong("Id"),
                        rs.getTimestamp("StartTime").toLocalDateTime(),
                        rs.getTimestamp("EndTime").toLocalDateTime(),
                        rs.getString("giaoVien"),
                        rs.getString("mon"),
                        rs.getString("truong")));
    }

    /**
     * Các phân công lập gần nhất. Nhãn trường gộp sẵn bằng FOR XML PATH ngay trong SQL; bản cũ
     * chạy một truy vấn ô lịch cho TỪNG dòng.
     */
    public List<DongPhanCong> phanCongGanDay(int soLuong) {
        String sql = """
                SELECT TOP (:soLuong)
                    a.Id,
                    giaoVien = t.LastName + N' ' + t.FirstName,
                    mon      = sj.Name,
                    a.StartDate, a.EndDate, a.Status,
                    truong   = STUFF((
                                SELECT DISTINCT N', ' + sc3.Name
                                FROM AssignmentSlot s3
                                JOIN School sc3 ON sc3.Id = s3.SchoolId
                                WHERE s3.AssignmentId = a.Id AND s3.IsDeleted = 0
                                FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 2, N''),
                    truongGoc = scl.Name
                FROM Assignment a
                JOIN Teacher t   ON t.Id = a.TeacherId
                JOIN Subject sj  ON sj.Id = a.SubjectId
                JOIN School scl  ON scl.Id = a.SchoolId
                WHERE a.IsDeleted = 0
                ORDER BY a.Id DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("soLuong", soLuong), (rs, i) -> {
            String truong = rs.getString("truong");
            if (truong == null || truong.isBlank()) {
                truong = rs.getString("truongGoc"); // dữ liệu cũ chưa gắn trường ở cấp ô lịch
            }
            LocalDate batDau = rs.getDate("StartDate").toLocalDate();
            LocalDate ketThuc =
                    rs.getDate("EndDate") == null ? null : rs.getDate("EndDate").toLocalDate();
            String[] trangThai = nhanTrangThaiPhanCong(rs.getString("Status"), batDau, ketThuc);
            return new DongPhanCong(
                    rs.getInt("Id"),
                    rs.getString("giaoVien"),
                    rutGon(truong),
                    rs.getString("mon"),
                    "%02d/%02d/%d".formatted(batDau.getDayOfMonth(), batDau.getMonthValue(), batDau.getYear()),
                    trangThai[1],
                    trangThai[0]);
        });
    }

    /** Quá hai trường thì rút gọn — ô bảng trên dashboard không đủ rộng để liệt kê hết. */
    private static String rutGon(String danhSach) {
        if (danhSach == null) {
            return "—";
        }
        String[] phan = danhSach.split(", ");
        return phan.length <= 2 ? danhSach : phan[0] + " +" + (phan.length - 1);
    }

    /** Trạng thái hiển thị của phân công: mã màu + nhãn tiếng Việt. */
    private static String[] nhanTrangThaiPhanCong(String status, LocalDate batDau, LocalDate ketThuc) {
        LocalDate homNay = com.kdc.tsdms.common.BusinessTime.today();
        if ("CANCELLED".equals(status)) {
            return new String[] {"no", "Đã huỷ"};
        }
        if ("COMPLETED".equals(status)) {
            return new String[] {"done", "Đã hoàn thành"};
        }
        if (ketThuc != null && ketThuc.isBefore(homNay)) {
            return new String[] {"done", "Đã kết thúc"};
        }
        if (batDau.isAfter(homNay)) {
            return new String[] {"wait", "Sắp bắt đầu"};
        }
        return new String[] {"ok", "Đang dạy"};
    }

    /* ═════════════════════════ DANH MỤC CHO THANH LỌC ═════════════════════════ */

    /**
     * Danh mục cho các ô lọc, lấy một lượt. Chỉ liệt kê trường CÒN HỢP ĐỒNG hiệu lực để người
     * dùng không lọc ra màn hình trống.
     */
    public DanhMucLoc danhMucLoc() {
        List<MucLoc> truong = jdbc.query(
                """
                SELECT DISTINCT scl.Id, scl.Name
                FROM School scl
                JOIN ServiceContract sv ON sv.SchoolId = scl.Id AND sv.Status = 'ACTIVE'
                WHERE scl.IsDeleted = 0
                ORDER BY scl.Name
                """, new MapSqlParameterSource(), (rs, i) -> new MucLoc(rs.getInt("Id"), rs.getString("Name")));

        List<MucLoc> nhomMon = jdbc.query(
                "SELECT Id, Name FROM SubjectCategory WHERE IsDeleted = 0 ORDER BY Name",
                new MapSqlParameterSource(),
                (rs, i) -> new MucLoc(rs.getInt("Id"), rs.getString("Name")));

        return new DanhMucLoc(truong, nhomMon);
    }

    /** Một lựa chọn trong ô chọn của thanh lọc. */
    public record MucLoc(Integer id, String ten) {}

    /** Hai danh mục của thanh lọc. */
    public record DanhMucLoc(List<MucLoc> truong, List<MucLoc> nhomMon) {}

    /** Một buổi dạy đọc thẳng từ CSDL, chưa định dạng để hiển thị. */
    public record BuoiDayTho(
            long id, LocalDateTime batDau, LocalDateTime ketThuc, String giaoVien, String mon, String truong) {}

    /* ═════════════════════════ KIỂU TRẢ VỀ NỘI BỘ ═════════════════════════ */

    /** Số liệu thô của một kỳ. TaughtHours ở đây là SỐ TIẾT, xem ghi chú ở CHI_PHI. */
    public record ThongKeKy(
            long buoiDuyet,
            long buoiTatCa,
            double chiPhi,
            long chamCongTong,
            long chamCongCoMat,
            long chamCongDungGio) {}
}
