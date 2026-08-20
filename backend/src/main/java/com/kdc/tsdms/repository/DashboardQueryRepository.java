package com.kdc.tsdms.repository;

import com.kdc.tsdms.dto.DashboardAnalyticsResponse.DiemThang;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse.DongPhanTich;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse.LatCat;
import com.kdc.tsdms.dto.DashboardAnalyticsResponse.ODoNhiet;
import com.kdc.tsdms.dto.DashboardFilter;
import com.kdc.tsdms.dto.DashboardOperationsResponse.DongPhanCong;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Toàn bộ truy vấn thống kê của Bảng điều khiển.
 *
 * <p>VÌ SAO VIẾT SQL TAY THAY VÌ DÙNG JPA: bản dashboard trước nạp nguyên cả bảng {@code Schedule}
 * (hơn 27.000 dòng), cả {@code Assignment}, {@code Teacher}, {@code School}, {@code Subject},
 * {@code Payroll} vào bộ nhớ rồi mới lọc bằng Java Stream — mỗi lần mở trang là một lần kéo toàn
 * bộ cơ sở dữ liệu qua mạng. Mọi phép đếm và cộng ở đây đều là việc SQL Server làm giỏi nhất và
 * có sẵn chỉ mục để làm; đẩy xuống đó thì mỗi truy vấn chỉ trả về vài chục dòng kết quả.
 *
 * <p>QUY ƯỚC CHUNG CỦA MỌI TRUY VẤN Ở ĐÂY:
 *
 * <ul>
 *   <li>"Buổi dạy" chỉ tính bản ghi {@code Schedule} đã DUYỆT và chưa xóa mềm. Buổi bị huỷ hay
 *       bị từ chối vẫn được đếm riêng để tính tỉ lệ duyệt, nhưng không bao giờ vào tổng.
 *   <li>Trường của một buổi lấy từ Ô LỊCH trước, phân công sau — từ V27 một phiếu phân công trải
 *       được nhiều trường nên {@code Assignment.SchoolId} không còn là câu trả lời đúng.
 *   <li>CHI PHÍ được PHÂN BỔ về từng buổi dạy: lương thực nhận của giáo viên trong tháng chia cho
 *       số tiết đã dạy tháng đó ({@code NetAmount / TaughtHours}). Nhờ vậy chi phí lọc được theo
 *       trường và theo môn — điều mà bảng {@code Payroll} (chỉ có grain giáo viên × tháng) tự nó
 *       không làm được.
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
     * Khối FROM dùng chung cho mọi thống kê dựa trên buổi dạy.
     *
     * <p>{@code LEFT JOIN AssignmentSlot} phải đứng TRƯỚC {@code JOIN School} vì trường của buổi
     * suy ra từ ô lịch. {@code LEFT JOIN Payroll} kéo theo đơn giá tiết của đúng tháng chứa buổi
     * đó — bảng lương chốt theo tháng nên khoá nối là (giáo viên, năm, tháng).
     *
     * <p>{@code LEFT JOIN Attendance} an toàn với mọi phép đếm ở đây vì quan hệ là MỘT–MỘT: DB có
     * ràng buộc duy nhất {@code UX_Attendance_ScheduleId}, mỗi buổi tối đa một dòng chấm công.
     * Nhờ nối sẵn nên các truy vấn vừa đếm được buổi, vừa đếm được chuyên cần, vừa cộng được chi
     * phí trong một lượt quét.
     */
    private static final String FROM_BUOI_DAY = """
            FROM Schedule sch
            JOIN Assignment a            ON a.Id = sch.AssignmentId
            LEFT JOIN AssignmentSlot slot ON slot.Id = sch.SourceSlotId
            JOIN Subject sj              ON sj.Id = a.SubjectId
            JOIN Teacher t               ON t.Id = sch.TeacherId
            JOIN School scl              ON scl.Id = COALESCE(slot.SchoolId, a.SchoolId)
            LEFT JOIN Attendance att     ON att.ScheduleId = sch.Id
            LEFT JOIN Payroll pr         ON pr.TeacherId = sch.TeacherId
                                        AND pr.PeriodYear = YEAR(sch.StartTime)
                                        AND pr.PeriodMonth = MONTH(sch.StartTime)
            """;

    /** Điều kiện lọc dùng chung. Tham số NULL nghĩa là "không lọc theo tiêu chí đó". */
    private static final String WHERE_LOC = """
            WHERE sch.IsDeleted = 0
              AND sch.StartTime >= :tuNgay
              AND sch.StartTime <  :denNgay
              AND (:branchId   IS NULL OR scl.BranchId  = :branchId)
              AND (:schoolId   IS NULL OR scl.Id        = :schoolId)
              AND (:categoryId IS NULL OR sj.CategoryId = :categoryId)
            """;

    /** Thời lượng một buổi, quy ra giờ. */
    private static final String GIO = "(DATEDIFF(MINUTE, sch.StartTime, sch.EndTime) / 60.0)";

    /**
     * Chi phí lương phân bổ cho MỘT buổi dạy.
     *
     * <p>Trung tâm trả lương theo TIẾT và mỗi buổi có mặt đúng bằng một tiết, nên chi phí một buổi
     * chính là lương thực nhận của tháng chia cho số tiết đã dạy trong tháng đó. Cột
     * {@code TaughtHours} lưu SỐ TIẾT chứ không phải số giờ — tên cột là di sản, ý nghĩa đã đổi từ
     * khi trung tâm chuyển sang trả theo tiết (xem {@code PayrollService}); chia cho nó rồi nhân
     * thêm thời lượng giờ là đếm trùng và cho ra con số cao gấp mấy lần thực tế.
     *
     * <p>Phép chia này còn kéo theo cả thưởng và khấu trừ mà admin nhập tay, nên tổng chi phí trên
     * Bảng điều khiển khớp đúng với tổng cột "Thực nhận" của màn hình Bảng lương.
     *
     * <p>Chỉ buổi giáo viên CÓ MẶT mới sinh chi phí, vì mẫu số {@code TaughtHours} cũng chỉ đếm
     * những buổi ấy. Phân bổ cho cả buổi vắng thì tổng chi phí trên Bảng điều khiển sẽ vượt tổng
     * cột "Thực nhận" của Bảng lương đúng bằng tỉ lệ vắng mặt — một khoản tiền không có thật.
     *
     * <p>Tháng chưa lập bảng lương thì tính 0 — không suy đoán một con số chưa ai duyệt.
     */
    private static final String CHI_PHI = """
            (CASE WHEN att.Status IN ('PRESENT','LATE') AND ISNULL(pr.TaughtHours, 0) > 0
                  THEN pr.NetAmount / pr.TaughtHours ELSE 0 END)""";

    /** 1 = Thứ Hai … 7 = Chủ Nhật, không phụ thuộc SET DATEFIRST của phiên kết nối. */
    private static final String THU_TRONG_TUAN = "(((DATEPART(WEEKDAY, sch.StartTime) + @@DATEFIRST - 2) % 7) + 1)";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ═════════════════════════ THAM SỐ ═════════════════════════ */

    /**
     * Dựng bộ tham số từ bộ lọc.
     *
     * <p>Ba tham số lọc phải khai báo KIỂU tường minh: SQL Server từ chối một tham số NULL không
     * rõ kiểu ngay trong biểu thức {@code :p IS NULL}, và lỗi báo về lại rất khó lần.
     */
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
     * Toàn bộ số liệu của một kỳ trong MỘT câu lệnh, một lượt quét.
     *
     * <p>Chín con số này đáng lẽ là chín truy vấn. Gộp lại không chỉ nhanh hơn: chúng chắc chắn
     * thuộc về cùng một ảnh chụp dữ liệu. Chạy rời nhau thì giữa hai câu lệnh có thể có người vừa
     * duyệt thêm một buổi, và trên màn hình sẽ hiện ra một tỉ lệ phần trăm lớn hơn 100 mà không ai
     * tái hiện lại được.
     */
    public ThongKeKy thongKeKy(DashboardFilter f) {
        String sql = """
                SELECT
                    buoiDuyet     = COUNT(CASE WHEN sch.Status = 'APPROVED' THEN 1 END),
                    buoiTatCa     = COUNT(*),
                    gioGiang      = ISNULL(SUM(CASE WHEN sch.Status = 'APPROVED' THEN %s END), 0),
                    chiPhi        = ISNULL(SUM(CASE WHEN sch.Status = 'APPROVED' THEN %s END), 0),
                    gvCoLich      = COUNT(DISTINCT CASE WHEN sch.Status = 'APPROVED' THEN sch.TeacherId END),
                    truongCoLich  = COUNT(DISTINCT CASE WHEN sch.Status = 'APPROVED' THEN scl.Id END),
                    ccTong        = COUNT(att.Id),
                    ccCoMat       = COUNT(CASE WHEN att.Status IN ('PRESENT','LATE') THEN 1 END),
                    ccDungGio     = COUNT(CASE WHEN att.Status = 'PRESENT' THEN 1 END)
                %s
                %s
                """.formatted(GIO, CHI_PHI, FROM_BUOI_DAY, WHERE_LOC);

        return jdbc.queryForObject(
                sql,
                thamSo(f),
                (rs, i) -> new ThongKeKy(
                        rs.getLong("buoiDuyet"),
                        rs.getLong("buoiTatCa"),
                        rs.getDouble("gioGiang"),
                        rs.getDouble("chiPhi"),
                        rs.getLong("gvCoLich"),
                        rs.getLong("truongCoLich"),
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
     * Số trường đang có hợp đồng dịch vụ còn hiệu lực — mẫu số của chỉ số "trường đang phục vụ".
     *
     * <p>Đếm theo hợp đồng chứ không theo số bản ghi {@code School}: trong danh bạ có cả trường đã
     * ngừng hợp tác, lấy tổng danh bạ làm mẫu số sẽ khiến tỉ lệ phục vụ luôn thấp một cách giả tạo.
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

    /**
     * Mật độ buổi dạy theo (thứ × tiết) cho bản đồ nhiệt.
     *
     * <p>Buổi nào chưa gắn tiết ({@code PeriodId} rỗng — dữ liệu có từ trước V9) thì bỏ qua thay
     * vì dồn vào tiết 0: một ô "tiết 0" sáng rực ở góc bảng chỉ là lỗi dữ liệu đội lốt thông tin.
     */
    public List<ODoNhiet> nhietDo(DashboardFilter f) {
        String sql = """
                SELECT
                    thu    = %s,
                    tiet   = p.PeriodNumber,
                    soBuoi = COUNT(*)
                %s
                JOIN Period p ON p.Id = sch.PeriodId
                %s
                  AND sch.Status = 'APPROVED'
                GROUP BY %s, p.PeriodNumber
                """.formatted(THU_TRONG_TUAN, FROM_BUOI_DAY, WHERE_LOC, THU_TRONG_TUAN);

        return jdbc.query(
                sql, thamSo(f), (rs, i) -> new ODoNhiet(rs.getInt("thu"), rs.getInt("tiet"), rs.getLong("soBuoi")));
    }

    /** Số tiết nhiều nhất mà một trường xếp trong ngày — dựng trục dọc cho bản đồ nhiệt. */
    public int soTietToiDa() {
        Integer n = jdbc.queryForObject(
                "SELECT ISNULL(MAX(PeriodNumber), 0) FROM Period WHERE IsDeleted = 0",
                new MapSqlParameterSource(),
                Integer.class);
        return n == null ? 0 : n;
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

    /** Xếp hạng trường theo số buổi dạy (biểu đồ thanh ngang). */
    public List<LatCat> topTruong(DashboardFilter f, int soLuong) {
        String sql = """
                SELECT TOP (:soLuong)
                    id     = scl.Id,
                    nhan   = scl.Name,
                    giaTri = COUNT(*)
                %s
                %s
                  AND sch.Status = 'APPROVED'
                GROUP BY scl.Id, scl.Name
                ORDER BY COUNT(*) DESC, scl.Name
                """.formatted(FROM_BUOI_DAY, WHERE_LOC);

        return jdbc.query(
                sql,
                thamSo(f).addValue("soLuong", soLuong),
                (rs, i) -> new LatCat(rs.getInt("id"), rs.getString("nhan"), rs.getLong("giaTri"), null));
    }

    /* ═════════════════════════ BẢNG PHÂN TÍCH ═════════════════════════ */

    /**
     * Bảng phân tích sâu theo một trong ba chiều.
     *
     * <p>Cột nhóm được chọn từ hằng số trong {@code enum}, KHÔNG ghép từ chuỗi người dùng gửi lên —
     * đây là chỗ duy nhất trong lớp này có mảnh SQL thay đổi được, nên nó phải đóng kín.
     *
     * <p>Chấm công nối bằng {@code LEFT JOIN} một-một qua {@code Attendance.ScheduleId} (đã có ràng
     * buộc duy nhất ở DB) nên không làm sai các phép đếm buổi ở cùng câu lệnh.
     */
    public List<DongPhanTich> phanTich(DashboardFilter f, Chieu chieu) {
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
            return new DongPhanTich(
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
     * Điểm đánh giá trung bình theo giáo viên hoặc theo trường, cho các lượt đánh giá lập trong kỳ.
     *
     * <p>Tách khỏi truy vấn bảng phân tích vì đánh giá KHÔNG gắn với buổi dạy: nối chung sẽ nhân
     * bản mỗi buổi lên đúng bằng số lượt đánh giá của giáo viên đó, thổi phồng mọi phép cộng.
     * Chiều "theo môn" không có số liệu này — hệ thống đánh giá con người, không đánh giá môn học.
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
     * Buổi dạy đã diễn ra nhưng chưa có dòng chấm công.
     *
     * <p>Chỉ soi 30 ngày gần nhất: chấm công là đầu vào tính lương nên chỉ những kỳ còn sửa được
     * mới đáng báo. Bới cả năm học lên sẽ ra một con số hàng nghìn mà không ai xử lý nổi, và một
     * cảnh báo không ai xử lý được thì chỉ dạy người dùng thói quen phớt lờ cảnh báo.
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

    /**
     * Trường còn hợp đồng dịch vụ nhưng không phát sinh buổi dạy nào trong kỳ.
     *
     * <p>Đây là cảnh báo đáng tiền nhất trên màn hình: một khách hàng ngừng đặt lịch mà không ai
     * để ý thường là khách hàng sắp rời đi.
     */
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
     * Ngày dạy gần nhất kể từ {@code tuNgay} — dùng khi hôm nay không có buổi nào.
     *
     * <p>Không có bước này thì khối "Lịch dạy hôm nay" sẽ trống rỗng suốt ba tháng hè, và một ô
     * trống thì không phân biệt được "hôm nay nghỉ" với "hệ thống hỏng".
     */
    public LocalDate ngayDayKeTiep(LocalDate tuNgay) {
        return jdbc.queryForObject(
                """
                SELECT TOP 1 CAST(sch.StartTime AS DATE)
                FROM Schedule sch
                WHERE sch.IsDeleted = 0 AND sch.Status = 'APPROVED' AND sch.StartTime >= :tuNgay
                ORDER BY sch.StartTime
                """, new MapSqlParameterSource("tuNgay", tuNgay.atStartOfDay()), (rs, i) -> rs.getDate(1)
                        .toLocalDate());
    }

    /** Các buổi dạy của một ngày, kèm tên giáo viên / môn / trường / phòng. */
    public List<Object[]> lichTrongNgay(LocalDate ngay, int gioiHan) {
        String sql = """
                SELECT TOP (:gioiHan)
                    sch.Id, sch.StartTime, sch.EndTime,
                    giaoVien = t.LastName + N' ' + t.FirstName,
                    mon      = sj.Name,
                    truong   = scl.Name,
                    phong    = r.Name
                FROM Schedule sch
                JOIN Assignment a            ON a.Id = sch.AssignmentId
                LEFT JOIN AssignmentSlot slot ON slot.Id = sch.SourceSlotId
                JOIN Subject sj              ON sj.Id = a.SubjectId
                JOIN Teacher t               ON t.Id = sch.TeacherId
                JOIN School scl              ON scl.Id = COALESCE(slot.SchoolId, a.SchoolId)
                LEFT JOIN Room r             ON r.Id = sch.RoomId
                WHERE sch.IsDeleted = 0 AND sch.Status = 'APPROVED'
                  AND sch.StartTime >= :tuNgay AND sch.StartTime < :denNgay
                ORDER BY sch.StartTime, scl.Name
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("tuNgay", ngay.atStartOfDay())
                        .addValue("denNgay", ngay.plusDays(1).atStartOfDay())
                        .addValue("gioiHan", gioiHan),
                (rs, i) -> new Object[] {
                    rs.getLong("Id"),
                    rs.getTimestamp("StartTime").toLocalDateTime(),
                    rs.getTimestamp("EndTime").toLocalDateTime(),
                    rs.getString("giaoVien"),
                    rs.getString("mon"),
                    rs.getString("truong"),
                    rs.getString("phong")
                });
    }

    /**
     * Sáu phân công lập gần nhất.
     *
     * <p>Nhãn trường gộp sẵn bằng {@code STRING_AGG} ngay trong SQL. Bản cũ chạy một truy vấn ô
     * lịch cho TỪNG dòng để làm việc này — sáu dòng là bảy lượt đi lại cơ sở dữ liệu.
     */
    public List<DongPhanCong> phanCongGanDay(int soLuong) {
        String sql = """
                SELECT TOP (:soLuong)
                    a.Id,
                    giaoVien = t.LastName + N' ' + t.FirstName,
                    mon      = sj.Name,
                    a.StartDate, a.EndDate, a.Status,
                    soTiet   = (SELECT COUNT(*) FROM AssignmentSlot s2
                                WHERE s2.AssignmentId = a.Id AND s2.IsDeleted = 0),
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
                    rs.getInt("soTiet"),
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
     * Ba danh sách đổ vào các ô chọn của thanh lọc, lấy trong một lượt gọi.
     *
     * <p>Gọi ba API danh mục có sẵn cũng ra kết quả tương tự, nhưng chúng đều phân trang và trả về
     * đầy đủ địa chỉ, số điện thoại, người liên hệ… Thanh lọc chỉ cần cặp (mã, tên), nên hỏi thẳng
     * đúng hai cột là đủ và rẻ hơn hẳn.
     *
     * <p>Chỉ liệt kê TRƯỜNG CÒN HỢP ĐỒNG hiệu lực: một ô chọn đầy tên trường đã ngừng hợp tác chỉ
     * làm người dùng lọc ra màn hình trống.
     */
    public DanhMucLoc danhMucLoc() {
        List<MucLoc> chiNhanh = jdbc.query(
                "SELECT Id, Name FROM Branch ORDER BY Name",
                new MapSqlParameterSource(),
                (rs, i) -> new MucLoc(rs.getInt("Id"), rs.getString("Name")));

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

        return new DanhMucLoc(chiNhanh, truong, nhomMon);
    }

    /** Một lựa chọn trong ô chọn của thanh lọc. */
    public record MucLoc(Integer id, String ten) {}

    /** Ba danh mục của thanh lọc. */
    public record DanhMucLoc(List<MucLoc> chiNhanh, List<MucLoc> truong, List<MucLoc> nhomMon) {}

    /* ═════════════════════════ KIỂU TRẢ VỀ NỘI BỘ ═════════════════════════ */

    /**
     * Số liệu thô của một kỳ, trước khi tầng service nặn thành thẻ chỉ số.
     *
     * @param buoiDuyet số buổi đã duyệt
     * @param buoiTatCa tổng số buổi đã xếp, kể cả huỷ và bị từ chối
     * @param gioGiang tổng giờ giảng của các buổi đã duyệt
     * @param chiPhi chi phí lương phân bổ
     * @param gvCoLich số giáo viên có ít nhất một buổi
     * @param truongCoLich số trường có ít nhất một buổi
     * @param chamCongTong số dòng chấm công
     * @param chamCongCoMat số dòng có mặt (đúng giờ hoặc đi muộn)
     * @param chamCongDungGio số dòng đúng giờ
     */
    public record ThongKeKy(
            long buoiDuyet,
            long buoiTatCa,
            double gioGiang,
            double chiPhi,
            long gvCoLich,
            long truongCoLich,
            long chamCongTong,
            long chamCongCoMat,
            long chamCongDungGio) {}
}
