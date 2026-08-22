package com.kdc.tsdms.controller;

import com.kdc.tsdms.common.Paging;
import com.kdc.tsdms.dto.SchoolDetailResponse;
import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.dto.SchoolResponse;
import com.kdc.tsdms.service.SchoolService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Trường khách hàng — /api/v1/schools
 *
 * <p>GET (list/detail/thùng rác) : SCHOOL_VIEW · ghi (POST/PUT/DELETE/khôi phục) : SCHOOL_MANAGE.
 * Từ V33 hệ thống chỉ còn hai tác nhân ADMIN và GIÁO VIÊN, nên trên thực tế cả hai quyền này chỉ
 * ADMIN có; hai authority vẫn giữ nguyên để mở lại phòng ban sau này chỉ là thêm RolePermission.
 */
@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {

    private final SchoolService sService;

    public SchoolController(SchoolService schoolService) {
        this.sService = schoolService;
    }

    /**
     * Danh sách có phân trang + tìm kiếm + lọc theo chi nhánh/trạng thái.
     *
     * @param expiringInDays chỉ lấy trường có hợp đồng còn hạn nhưng kết thúc trong ngần này ngày
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_VIEW')")
    public Page<SchoolResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer expiringInDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("name").ascending());
        return sService.search(keyword, branchId, status, expiringInDays, pageable);
    }

    /** Thùng rác — trường đã xóa mềm. Không phân trang: đây là chỗ dọn dẹp, không phải chỗ tra cứu. */
    @GetMapping("/trash")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_VIEW')")
    public List<SchoolResponse> trash() {
        return sService.listTrash();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_VIEW')")
    public SchoolResponse detail(@PathVariable Integer id) {
        return sService.getById(id);
    }

    /** Số liệu kèm theo (lớp, giáo viên, học sinh, khung tiết, hợp đồng dịch vụ) — nạp khi mở chi tiết. */
    @GetMapping("/{id}/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_VIEW')")
    public SchoolDetailResponse summary(@PathVariable Integer id) {
        return sService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public ResponseEntity<SchoolResponse> create(@Valid @RequestBody SchoolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public SchoolResponse update(@PathVariable Integer id, @Valid @RequestBody SchoolRequest req) {
        return sService.update(id, req);
    }

    /** Xóa MỀM (đưa vào thùng rác). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        sService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCHOOL_MANAGE')")
    public SchoolResponse restore(@PathVariable Integer id) {
        return sService.restore(id);
    }
}
