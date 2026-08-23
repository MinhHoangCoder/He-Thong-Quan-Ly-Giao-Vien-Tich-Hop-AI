package com.kdc.tsdms.controller;

import com.kdc.tsdms.dto.TeacherResponse;
import com.kdc.tsdms.service.TeacherService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    // Danh sách + tìm kiếm ===================================

    //   Toàn bộ GV active. Tìm kiếm do FRONTEND tự lọc trên mảng này.
    //  <p>Chỉ staff (hoặc quyền TEACHER_VIEW) — hồ sơ chứa CCCD/địa chỉ/ngày sinh,

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_VIEW')")
    public List<TeacherResponse.Response> getAllTeachers() {
        return teacherService.getAllTeachers();
    }

    //   Xem chi tiết 1 GV (kèm chứng chỉ + hợp đồng hiện tại).
    //   Staff/TEACHER_VIEW xem mọi hồ sơ; GV thường chỉ xem được CHÍNH MÌNH
    //   (chốt chặn sở hữu nằm trong service — chống IDOR).

    @GetMapping("/{id}")
    public TeacherResponse.Response getTeacherById(@PathVariable Integer id) {
        return teacherService.getTeacherById(id);
    }

    // CRUD=========================================
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public ResponseEntity<TeacherResponse.Response> createTeacher(
            @Valid @RequestBody TeacherResponse.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.createTeacher(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public TeacherResponse.Response updateTeacher(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.UpdateRequest request) {
        return teacherService.updateTeacher(id, request);
    }

    //  Đổi RIÊNG username/email — tách khỏi form Sửa hồ sơ chính. Không có password.

    @GetMapping("/{id}/account")
    public TeacherResponse.AccountInfo getAccount(@PathVariable Integer id) {
        return teacherService.getAccount(id);
    }

    @PutMapping("/{id}/account")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public TeacherResponse.Response updateAccount(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.AccountUpdateRequest request) {
        return teacherService.updateAccount(id, request);
    }

    //  Xóa mềm — CHỈ ADMIN được xóa, GV chuyển vào thùng rác chứ không mất khỏi DB.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTeacher(@PathVariable Integer id) {
        teacherService.deleteTeacher(id);
        return ResponseEntity.noContent().build();
    }

    // Certificate========================
    @PostMapping("/{id}/certificates")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public ResponseEntity<TeacherResponse.CertificateDTO> addCertificate(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.CertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.addCertificate(id, request));
    }

    @DeleteMapping("/{id}/certificates/{certId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Integer id, @PathVariable Integer certId) {
        teacherService.deleteCertificate(id, certId);
        return ResponseEntity.noContent().build();
    }

    /** Upload file PDF THẬT cho 1 bằng cấp/chứng chỉ đã tồn tại (multipart/form-data, field "file"). */
    @PostMapping("/{id}/certificates/{certId}/file")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public ResponseEntity<TeacherResponse.CertificateDTO> uploadCertificateFile(
            @PathVariable Integer id, @PathVariable Integer certId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(teacherService.uploadCertificateFile(id, certId, file));
    }

    /** Mở/xem file PDF của 1 bằng cấp/chứng chỉ — trả inline để mở thẳng tab mới. */
    @GetMapping("/{id}/certificates/{certId}/file")
    public ResponseEntity<?> openCertificateFile(@PathVariable Integer id, @PathVariable Integer certId) {
        return teacherService.openCertificateFile(id, certId);
    }

    // Contract(1 hđ / 1 gv) =============================
    @PutMapping("/{id}/contract")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CONTRACT_MANAGE')")
    public TeacherResponse.ContractDTO upsertContract(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.ContractRequest request) {
        return teacherService.upsertContract(id, request);
    }
    // History===============================
    /** Danh sách GV đã bị xóa mềm — "thùng rác". */
    @GetMapping("/trash")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_VIEW')")
    public List<TeacherResponse.HistoryItem> getTrash() {
        return teacherService.getTrash();
    }

    /** Khôi phục GV từ thùng rác — hiện lại trên danh sách chính. */
    @PostMapping("/trash/{id}/restore")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TEACHER_MANAGE')")
    public TeacherResponse.Response restoreTeacher(@PathVariable Integer id) {
        return teacherService.restoreTeacher(id);
    }
}
