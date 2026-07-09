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

    /** Toàn bộ GV active. Tìm kiếm do FRONTEND tự lọc trên mảng này. */
    @GetMapping
    public List<TeacherResponse.Response> getAllTeachers() {
        return teacherService.getAllTeachers();
    }

    /** Xem chi tiết 1 GV (kèm chứng chỉ + hợp đồng hiện tại). */
    @GetMapping("/{id}")
    public TeacherResponse.Response getTeacherById(@PathVariable Integer id) {
        return teacherService.getTeacherById(id);
    }

    // CRUD=========================================
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<TeacherResponse.Response> createTeacher(
            @Valid @RequestBody TeacherResponse.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.createTeacher(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public TeacherResponse.Response updateTeacher(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.UpdateRequest request) {
        return teacherService.updateTeacher(id, request);
    }

    /** Xóa mềm — CHỈ ADMIN được xóa, GV chuyển vào thùng rác chứ không mất khỏi DB. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTeacher(@PathVariable Integer id) {
        teacherService.deleteTeacher(id);
        return ResponseEntity.noContent().build();
    }

    // Certificate========================
    @PostMapping("/{id}/certificates")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<TeacherResponse.CertificateDTO> addCertificate(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.CertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.addCertificate(id, request));
    }

    @DeleteMapping("/{id}/certificates/{certId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Integer id, @PathVariable Integer certId) {
        teacherService.deleteCertificate(id, certId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/certificates/{certId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public TeacherResponse.CertificateDTO updateCertificate(
            @PathVariable Integer id,
            @PathVariable Integer certId,
            @Valid @RequestBody TeacherResponse.CertificateRequest request) {
        return teacherService.updateCertificate(id, certId, request);
    }

    /** Upload file PDF đính kèm cho chứng chỉ. */
    @PostMapping("/{id}/certificates/{certId}/file")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public TeacherResponse.CertificateDTO uploadCertificateFile(
            @PathVariable Integer id, @PathVariable Integer certId, @RequestParam("file") MultipartFile file) {
        return teacherService.uploadCertificateFile(id, certId, file);
    }

    // Contract(1 hđ / 1 gv) =============================
    @PutMapping("/{id}/contract")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public TeacherResponse.ContractDTO upsertContract(
            @PathVariable Integer id, @Valid @RequestBody TeacherResponse.ContractRequest request) {
        return teacherService.upsertContract(id, request);
    }
    // History===============================
    /** Danh sách GV đã bị xóa mềm — "thùng rác". */
    @GetMapping("/trash")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public List<TeacherResponse.HistoryItem> getTrash() {
        return teacherService.getTrash();
    }

    /** Khôi phục GV từ thùng rác — hiện lại trên danh sách chính. */
    @PostMapping("/trash/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public TeacherResponse.Response restoreTeacher(@PathVariable Integer id) {
        return teacherService.restoreTeacher(id);
    }
}
