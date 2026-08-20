package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.DashboardAnalyticsResponse;
import com.kdc.tsdms.dto.DashboardFilter;
import com.kdc.tsdms.dto.DashboardOperationsResponse;
import com.kdc.tsdms.dto.DashboardSummaryResponse;
import com.kdc.tsdms.repository.DashboardQueryRepository;
import com.kdc.tsdms.repository.DashboardQueryRepository.Chieu;
import com.kdc.tsdms.repository.DashboardQueryRepository.DanhMucLoc;
import com.kdc.tsdms.service.DashboardService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Bảng điều khiển quản trị — {@code /api/v1/dashboard}.
 *
 * <p>Tách làm ba endpoint thay vì một endpoint gộp như bản trước vì hai lý do:
 *
 * <ul>
 *   <li>HIỆN DẦN. Ba khu có chi phí truy vấn rất chênh nhau — {@code /summary} chỉ quét một lượt,
 *       còn {@code /analytics} phải gom theo ba chiều cộng bản đồ nhiệt. Tách ra thì sáu thẻ chỉ
 *       số hiện ngay khi vừa có, thay vì cả trang đứng im chờ truy vấn chậm nhất.
 *   <li>HỎNG RIÊNG. Một khu lỗi thì hai khu kia vẫn hiển thị được. Một endpoint gộp thì bất kỳ
 *       truy vấn nào hỏng cũng xoá trắng toàn bộ màn hình.
 * </ul>
 *
 * <p>Cả ba đều nhận cùng bộ lọc: khu điều hành cũng cần nó cho hai cảnh báo phụ thuộc kỳ ("giáo
 * viên chưa có lịch", "trường không phát sinh buổi dạy").
 *
 * <p>Toàn bộ endpoint ở đây chỉ dành cho ADMIN. Giáo viên có bảng điều khiển riêng dựng từ dữ liệu
 * của chính họ ({@code TeacherDashboardPage}), không đi qua đây.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private static final DateTimeFormatter TEN_FILE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DashboardService service;
    private final DashboardQueryRepository repo;

    public DashboardController(DashboardService service, DashboardQueryRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    /** Sáu thẻ chỉ số, kèm đối chiếu kỳ trước. */
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Integer categoryId) {
        return service.summary(boLoc(from, to, branchId, schoolId, categoryId));
    }

    /** Bốn biểu đồ và bảng phân tích ba tab. */
    @GetMapping("/analytics")
    public DashboardAnalyticsResponse analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Integer categoryId) {
        return service.analytics(boLoc(from, to, branchId, schoolId, categoryId));
    }

    /** Việc cần xử lý, lịch dạy trong ngày, phân công gần đây. */
    @GetMapping("/operations")
    public DashboardOperationsResponse operations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Integer categoryId) {
        return service.operations(boLoc(from, to, branchId, schoolId, categoryId));
    }

    /** Danh mục đổ vào các ô chọn của thanh lọc. */
    @GetMapping("/filters")
    public DanhMucLoc filters() {
        return repo.danhMucLoc();
    }

    /**
     * Xuất bảng phân tích ra CSV.
     *
     * <p>Chuỗi ba byte đứng đầu nội dung là BOM UTF-8. Không có nó, Excel trên Windows đoán bảng
     * mã theo vùng của máy và mọi tên tiếng Việt biến thành ký tự lạ — file vẫn "mở được" nên lỗi
     * này thường chỉ bị phát hiện khi đã gửi báo cáo cho người khác.
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "GIAO_VIEN") Chieu chieu,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) Integer schoolId,
            @RequestParam(required = false) Integer categoryId) {
        DashboardFilter f = boLoc(from, to, branchId, schoolId, categoryId);
        byte[] noiDung = service.xuatCsv(f, chieu).getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

        byte[] file = new byte[bom.length + noiDung.length];
        System.arraycopy(bom, 0, file, 0, bom.length);
        System.arraycopy(noiDung, 0, file, bom.length, noiDung.length);

        String ten = "thong-ke-%s-%s.csv"
                .formatted(chieu.name().toLowerCase(), f.from().format(TEN_FILE));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ten).build().toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(file);
    }

    /**
     * Dựng bộ lọc từ tham số URL; thiếu khoảng thời gian thì lấy năm học hiện hành.
     *
     * <p>Cho phép bỏ trống có chủ đích: người dùng dán lại đường dẫn {@code /dashboard} trần vẫn
     * phải ra một màn hình có số liệu, chứ không phải một lỗi thiếu tham số.
     */
    private DashboardFilter boLoc(
            LocalDate from, LocalDate to, Integer branchId, Integer schoolId, Integer categoryId) {
        if (from == null || to == null) {
            DashboardFilter mac = DashboardFilter.namHocHienHanh();
            return new DashboardFilter(mac.from(), mac.to(), branchId, schoolId, categoryId);
        }
        return new DashboardFilter(from, to, branchId, schoolId, categoryId);
    }
}
