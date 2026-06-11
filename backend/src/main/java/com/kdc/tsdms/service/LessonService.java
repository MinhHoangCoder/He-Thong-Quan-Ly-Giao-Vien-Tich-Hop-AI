package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.LessonDto.*;
import com.kdc.tsdms.dto.LessonDto.LessonFileRequest;
import com.kdc.tsdms.dto.LessonDto.LessonFilter;
import com.kdc.tsdms.dto.LessonDto.LessonRequest;
import com.kdc.tsdms.dto.LessonDto.LessonResponse;
import com.kdc.tsdms.dto.LessonDto.LessonSummaryDto;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.LessonFile;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.mapper.LessonMapper;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.LessonFileRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic cho module Bài giảng.
 *
 * QUY TẮC PHÂN QUYỀN (được áp dụng TẠI ĐÂY, không chỉ ở Controller):
 *
 *  EMPLOYEE / ADMIN
 *    - Xem tất cả bài giảng (mọi trạng thái).
 *    - Toàn quyền CRUD + quản lý file.
 *
 *  TEACHER
 *    - Chỉ được XEM danh sách: những bài PUBLISHED được gán cho mình
 *      (teacherId của bài giảng == teacherId của tài khoản đang đăng nhập).
 *    - Chỉ được XEM chi tiết bài giảng khi thoả mãn cả 2 điều kiện trên.
 *    - KHÔNG được tạo / sửa / đổi trạng thái / xóa / quản lý file.
 *      (Controller đã chặn bằng @PreAuthorize; Service chặn thêm 1 lớp nữa
 *       để phòng trường hợp Security config bị bypass.)
 *
 *  SCHOOL
 *    - Không có quyền truy cập endpoint bài giảng (Controller trả 403).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonFileRepository lessonFileRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BranchRepository branchRepository;
    private final LessonMapper mapper;

    // ════════════════════════════════════════════════════════════════
    // READ
    // ════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách bài giảng có lọc + phân trang.
     *
     * Logic ẩn trạng thái với TEACHER:
     *  - Nếu caller là TEACHER → ép status = "PUBLISHED"
     *                          → ép teacherId = teacherId của bản thân
     *    (dù frontend có gửi filter khác cũng bị ghi đè ở đây).
     */
    public Page<LessonSummaryDto> getAll(LessonFilter filter) {
        if (SecurityUtils.hasRole("TEACHER")) {

            Integer appUserId = SecurityUtils.currentUserId();

            Teacher teacher = teacherRepository.findAll().stream()
                    .filter(t -> appUserId.equals(t.getAppUserId()))
                    .findFirst()
                    .orElseThrow(() -> new AccessDeniedException("Không tìm thấy giáo viên"));

            filter.setStatus("PUBLISHED");
            filter.setTeacherId(teacher.getId());
        }

        Sort sort = filter.getSortDir().equalsIgnoreCase("asc")
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<Lesson> page = lessonRepository.findWithFilters(
                filter.getBranchId(),
                filter.getSubjectId(),
                filter.getTeacherId(),
                filter.getStatus(),
                filter.getDifficultyLevel(),
                filter.getGradeLevel(),
                filter.getKeyword() != null ? filter.getKeyword().trim() : null,
                pageable);
        return page.map(mapper::toSummary);
    }

    /**
     * Lấy chi tiết 1 bài giảng.
     *
     * Logic với TEACHER:
     *  - Bài phải PUBLISHED.
     *  - teacherId của bài phải == teacherId của người đang đăng nhập.
     *  → Nếu không thoả → ném AccessDeniedException (→ HTTP 403).
     */
    public LessonResponse getById(Integer lessonId) {
        Lesson lesson = findActiveLesson(lessonId);
        if (SecurityUtils.hasRole("TEACHER")) {
            boolean isPublished = "PUBLISHED".equals(lesson.getStatus());
            Integer appUserId = SecurityUtils.currentUserId();

            Teacher teacher = teacherRepository.findAll().stream()
                    .filter(t -> appUserId.equals(t.getAppUserId()))
                    .findFirst()
                    .orElseThrow(() -> new AccessDeniedException("Không tìm thấy giáo viên"));

            boolean isAssignedToMe =
                    lesson.getTeacher() != null && lesson.getTeacher().getId().equals(teacher.getId());

            if (!isPublished || !isAssignedToMe) {
                throw new AccessDeniedException("Giáo viên chỉ được xem bài giảng đã xuất bản và được gán cho mình.");
            }
        }
        return mapper.toResponse(lesson);
    }

    // ════════════════════════════════════════════════════════════════
    // CREATE — chỉ EMPLOYEE / ADMIN (Controller đã chặn TEACHER)
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public LessonResponse create(LessonRequest req, Integer actorUserId) {
        Subject subject = subjectRepository
                .findById(req.getSubjectId())
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Môn học không tồn tại: " + req.getSubjectId()));
        Branch branch = branchRepository
                .findById(req.getBranchId())
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Chi nhánh không tồn tại: " + req.getBranchId()));
        Teacher teacher = teacherRepository
                .findById(req.getTeacherId())
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Giáo viên không tồn tại: " + req.getTeacherId()));

        Lesson lesson = mapper.toEntity(req);
        lesson.setSubject(subject);
        lesson.setBranch(branch);
        lesson.setTeacher(teacher);
        lesson.setCreatedBy(actorUserId);

        if (req.getFiles() != null) {
            for (LessonFileRequest fr : req.getFiles()) {
                lesson.getFiles()
                        .add(LessonFile.builder()
                                .lesson(lesson)
                                .fileName(fr.getFileName())
                                .fileUrl(fr.getFileUrl())
                                .fileType(fr.getFileType())
                                .fileSizeKb(fr.getFileSizeKb())
                                .createdBy(actorUserId)
                                .build());
            }
        }

        Lesson saved = lessonRepository.save(lesson);
        log.info("[LESSON] Tạo id={} bởi userId={}", saved.getLessonId(), actorUserId);
        return mapper.toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════════
    // UPDATE — chỉ EMPLOYEE / ADMIN
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public LessonResponse update(Integer lessonId, LessonRequest req, Integer actorUserId) {
        Lesson lesson = findActiveLesson(lessonId);

        if (req.getSubjectId() != null
                && !req.getSubjectId()
                        .equals(
                                lesson.getSubject() == null
                                        ? null
                                        : lesson.getSubject().getSubjectId())) {
            lesson.setSubject(subjectRepository
                    .findById(req.getSubjectId())
                    .orElseThrow(() ->
                            new ApiException(HttpStatus.NOT_FOUND, "Môn học không tồn tại: " + req.getSubjectId())));
        }
        if (req.getBranchId() != null
                && !req.getBranchId()
                        .equals(
                                lesson.getBranch() == null
                                        ? null
                                        : lesson.getBranch().getId())) {
            lesson.setBranch(branchRepository
                    .findById(req.getBranchId())
                    .orElseThrow(() ->
                            new ApiException(HttpStatus.NOT_FOUND, "Chi nhánh không tồn tại: " + req.getBranchId())));
        }
        if (req.getTeacherId() != null) {
            lesson.setTeacher(teacherRepository
                    .findById(req.getTeacherId())
                    .orElseThrow(() ->
                            new ApiException(HttpStatus.NOT_FOUND, "Giáo viên không tồn tại: " + req.getTeacherId())));
        }

        mapper.updateEntity(lesson, req);
        lesson.setUpdatedBy(actorUserId);

        Lesson saved = lessonRepository.save(lesson);
        log.info("[LESSON] Cập nhật id={} bởi userId={}", lessonId, actorUserId);
        return mapper.toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════════
    // CHANGE STATUS — chỉ EMPLOYEE / ADMIN
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public LessonResponse changeStatus(Integer lessonId, String newStatus, Integer actorUserId) {
        Lesson lesson = findActiveLesson(lessonId);
        String oldStatus = lesson.getStatus();
        lesson.setStatus(newStatus);
        lesson.setUpdatedBy(actorUserId);
        lessonRepository.save(lesson);
        log.info("[LESSON] Đổi trạng thái id={}: {} → {} bởi userId={}", lessonId, oldStatus, newStatus, actorUserId);
        return mapper.toResponse(lesson);
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE — chỉ EMPLOYEE / ADMIN
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(Integer lessonId, Integer actorUserId) {
        Lesson lesson = findActiveLesson(lessonId);
        lesson.setIsDeleted(true);
        lesson.setDeletedAt(Instant.now());
        lesson.setDeletedBy(actorUserId);
        lessonFileRepository.findByLesson_LessonId(lessonId).forEach(f -> f.setIsDeleted(true));
        lessonRepository.save(lesson);
        log.info("[LESSON] Xóa mềm id={} bởi userId={}", lessonId, actorUserId);
    }

    // ════════════════════════════════════════════════════════════════
    // FILE MANAGEMENT — chỉ EMPLOYEE / ADMIN
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public LessonResponse addFile(Integer lessonId, LessonFileRequest req, Integer actorUserId) {
        Lesson lesson = findActiveLesson(lessonId);
        lesson.getFiles()
                .add(LessonFile.builder()
                        .lesson(lesson)
                        .fileName(req.getFileName())
                        .fileUrl(req.getFileUrl())
                        .fileType(req.getFileType())
                        .fileSizeKb(req.getFileSizeKb())
                        .createdBy(actorUserId)
                        .build());
        lessonRepository.save(lesson);
        return mapper.toResponse(lesson);
    }

    @Transactional
    public void removeFile(Integer lessonId, Integer fileId, Integer actorUserId) {
        findActiveLesson(lessonId);
        LessonFile file = lessonFileRepository
                .findById(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File không tồn tại: " + fileId));
        if (!file.getLesson().getLessonId().equals(lessonId)) {
            throw new IllegalArgumentException("File không thuộc bài giảng này");
        }
        file.setIsDeleted(true);
        lessonFileRepository.save(file);
    }

    // ════════════════════════════════════════════════════════════════
    // STATS — chỉ EMPLOYEE / ADMIN (Controller đã chặn TEACHER)
    // ════════════════════════════════════════════════════════════════

    public Map<String, Long> getStatusStats(Integer branchId) {
        List<Object[]> rows = lessonRepository.countByStatusAndBranch(branchId);
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("DRAFT", 0L);
        result.put("PUBLISHED", 0L);
        result.put("ARCHIVED", 0L);
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER
    // ════════════════════════════════════════════════════════════════

    private Lesson findActiveLesson(Integer lessonId) {
        return lessonRepository
                .findByLessonIdAndIsDeletedFalse(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bài giảng không tồn tại: " + lessonId));
    }
}
