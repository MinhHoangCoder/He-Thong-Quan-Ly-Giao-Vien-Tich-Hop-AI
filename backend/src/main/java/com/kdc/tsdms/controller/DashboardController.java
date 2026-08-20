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
 * REST API Bảng điều khiển (admin) — /api/v1/dashboard. Chỉ ADMIN.
 *
 * <p>Tách 3 endpoint đọc số liệu vì chi phí truy vấn chênh nhau nhiều: /summary quét một lượt
 * nên về gần như tức thì, /analytics gom theo ba chiều nên nặng hơn hẳn. Gộp một cục thì cả
 * trang phải chờ truy vấn chậm nhất, và một truy vấn hỏng là xoá trắng màn hình.
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
     * Xuất CSV. BOM UTF-8 ở đầu file là bắt buộc: thiếu nó Excel trên Windows đoán bảng mã theo
     * vùng và toàn bộ tên tiếng Việt thành ký tự lạ — file vẫn mở được nên rất dễ lọt.
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

    /** Thiếu khoảng thời gian thì lấy năm học hiện hành, để mở /dashboard trần vẫn ra số liệu. */
    private DashboardFilter boLoc(
            LocalDate from, LocalDate to, Integer branchId, Integer schoolId, Integer categoryId) {
        if (from == null || to == null) {
            DashboardFilter mac = DashboardFilter.namHocHienHanh();
            return new DashboardFilter(mac.from(), mac.to(), branchId, schoolId, categoryId);
        }
        return new DashboardFilter(from, to, branchId, schoolId, categoryId);
    }
}
