package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.CanvaLinkRequest;
import com.kdc.tsdms.dto.LessonFileResponse;
import com.kdc.tsdms.dto.LessonRequest;
import com.kdc.tsdms.dto.LessonResponse;
import com.kdc.tsdms.dto.LessonSummary;
import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.LessonFile;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.LessonFileRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nghiệp vụ module Bài giảng.
 *
 * Môn học (Subject) liên kết qua SubjectId FK — bảng Subject đã có sẵn từ V1.
 * KHÔNG thêm/sửa DB, chỉ dùng đúng cột SubjectId đã tồn tại trong bảng Lesson
 * (V2).
 *
 * GradeLevel là text tự do (vd "Lớp 4", "Lớp 10"), không liên kết bảng
 * SchoolClass.
 */
@Service
public class LessonService {

    private static final String UPLOAD_ROOT = "uploads/lessons";

    /** Khối lớp gợi ý cho dropdown — text tự do, không ràng buộc DB. */
    private static final List<String> GRADE_LEVELS =
            List.of("Lớp 1", "Lớp 2", "Lớp 3", "Lớp 4", "Lớp 5", "Lớp 6", "Lớp 7", "Lớp 8", "Lớp 9");

    private final LessonRepository lessonRepo;
    private final LessonFileRepository lessonFileRepo;
    private final BranchRepository branchRepo;
    private final TeacherRepository teacherRepo;
    private final SubjectRepository subjectRepo;

    public LessonService(
            LessonRepository lessonRepo,
            LessonFileRepository lessonFileRepo,
            BranchRepository branchRepo,
            TeacherRepository teacherRepo,
            SubjectRepository subjectRepo) {
        this.lessonRepo = lessonRepo;
        this.lessonFileRepo = lessonFileRepo;
        this.branchRepo = branchRepo;
        this.teacherRepo = teacherRepo;
        this.subjectRepo = subjectRepo;
    }

    /*
     * ================================================================
     * METADATA cho dropdown form
     * ================================================================
     */

    /** Danh sách môn học ACTIVE từ bảng Subject — dùng cho dropdown "Môn học". */
    public List<Subject> getSubjects() {
        return subjectRepo.findAll().stream()
                .filter(s -> !s.isDeleted() && "ACTIVE".equals(s.getStatus()))
                .toList();
    }

    /** Khối lớp gợi ý. */
    public List<String> getGradeLevels() {
        return GRADE_LEVELS;
    }

    /**
     * Danh sách category duy nhất từ bảng Subject ACTIVE — dùng cho dropdown "Danh
     * mục".
     */
    public List<String> getCategories() {
        return subjectRepo.findAll().stream()
                .filter(s -> !s.isDeleted() && "ACTIVE".equals(s.getStatus()))
                .map(Subject::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /*
     * ================================================================
     * 1. DANH SÁCH (phân trang + lọc)
     * ================================================================
     */

    @Transactional(readOnly = true)
    public Page<LessonSummary> search(
            String category,
            String gradeLevel,
            String status,
            String keyword,
            boolean forcePublished,
            Pageable pageable) {

        String effectiveStatus = forcePublished ? "PUBLISHED" : status;
        Page<Lesson> lessonPage = lessonRepo.search(category, gradeLevel, effectiveStatus, keyword, pageable);

        // Lấy thông tin Subject (tên + category) cho tất cả bài trong trang — 1 query
        // duy nhất (tránh N+1)
        List<Integer> ids = lessonPage.getContent().stream()
                .map(Lesson::getSubjectId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Integer, Subject> subjectMap = buildSubjectMap(ids);

        return lessonPage.map(l -> {
            Subject subj = subjectMap.get(l.getSubjectId());
            String subjectName = subj != null ? subj.getName() : null;
            String subjectcategory = subj != null ? subj.getCategory() : null;
            return LessonSummary.fromEntity(l, subjectName, subjectcategory);
        });
    }

    /*
     * ================================================================
     * 2. CHI TIẾT
     * ================================================================
     */

    @Transactional(readOnly = true)
    public LessonResponse getDetail(Integer id, boolean forcePublished) {
        Lesson lesson = getOrThrow(id);
        if (forcePublished && !"PUBLISHED".equals(lesson.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài giảng id=" + id);
        }
        return buildResponse(lesson);
    }

    /*
     * ================================================================
     * 3. TẠO MỚI
     * ================================================================
     */

    @Transactional
    public LessonResponse create(LessonRequest req) {
        validateRefs(req);
        Lesson lesson = new Lesson();
        apply(lesson, req);
        lesson.setCreatedBy(SecurityUtils.currentUserId());
        return buildResponse(lessonRepo.save(lesson));
    }

    /*
     * ================================================================
     * 4. SỬA
     * ================================================================
     */

    @Transactional
    public LessonResponse update(Integer id, LessonRequest req) {
        Lesson lesson = getOrThrow(id);
        validateRefs(req);
        apply(lesson, req);
        lesson.setUpdatedAt(Instant.now());
        lesson.setUpdatedBy(SecurityUtils.currentUserId());
        return buildResponse(lessonRepo.save(lesson));
    }

    /*
     * ================================================================
     * 5. XÓA MỀM
     * ================================================================
     */

    @Transactional
    public void delete(Integer id) {
        Lesson lesson = getOrThrow(id);
        lesson.setDeleted(true);
        lesson.setDeletedAt(Instant.now());
        lesson.setDeletedBy(SecurityUtils.currentUserId());
        lessonRepo.save(lesson);
    }

    /*
     * ================================================================
     * 6. UPLOAD FILE PPT
     * ================================================================
     */

    @Transactional
    public List<LessonFileResponse> uploadFiles(Integer lessonId, List<MultipartFile> files) {
        Lesson lesson = getOrThrow(lessonId);
        if (files == null || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất 1 file");
        }

        Path dir = Path.of(UPLOAD_ROOT, String.valueOf(lessonId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được thư mục lưu file");
        }

        Integer uid = SecurityUtils.currentUserId();
        List<LessonFile> saved = new ArrayList<>();

        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            String original = f.getOriginalFilename() != null ? f.getOriginalFilename() : "file";
            int dot = original.lastIndexOf('.');
            String ext = dot >= 0 ? original.substring(dot) : "";
            String stored = UUID.randomUUID() + ext;

            try {
                Files.copy(f.getInputStream(), dir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi lưu file: " + original);
            }

            LessonFile lf = new LessonFile();
            lf.setLessonId(lessonId);
            lf.setFileName(original);
            lf.setFileUrl("/" + UPLOAD_ROOT + "/" + lessonId + "/" + stored);
            lf.setFileType(ext.isBlank() ? "file" : ext.substring(1).toLowerCase());
            lf.setFileSizeKb((int) Math.max(1, f.getSize() / 1024));
            lf.setCreatedBy(uid);
            saved.add(lessonFileRepo.save(lf));
        }

        lesson.setUpdatedAt(Instant.now());
        lesson.setUpdatedBy(uid);
        lessonRepo.save(lesson);

        return saved.stream().map(LessonFileResponse::fromEntity).toList();
    }

    /*
     * ================================================================
     * 7. THÊM LINK CANVA
     * ================================================================
     */

    @Transactional
    public LessonFileResponse addCanvaLink(Integer lessonId, CanvaLinkRequest req) {
        Lesson lesson = getOrThrow(lessonId);

        LessonFile lf = new LessonFile();
        lf.setLessonId(lessonId);
        lf.setFileName(req.fileName());
        lf.setFileUrl(req.canvaUrl());
        lf.setFileType("canva");
        lf.setFileSizeKb(null);
        lf.setCreatedBy(SecurityUtils.currentUserId());

        lesson.setUpdatedAt(Instant.now());
        lesson.setUpdatedBy(SecurityUtils.currentUserId());
        lessonRepo.save(lesson);

        return LessonFileResponse.fromEntity(lessonFileRepo.save(lf));
    }

    /*
     * ================================================================
     * 8. XÓA FILE ĐÍNH KÈM (mềm)
     * ================================================================
     */

    @Transactional
    public void deleteFile(Integer lessonId, Integer fileId) {
        getOrThrow(lessonId);
        LessonFile f = lessonFileRepo
                .findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy file id=" + fileId));
        if (!f.getLessonId().equals(lessonId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File không thuộc bài giảng này");
        }
        f.setDeleted(true);
        f.setDeletedAt(Instant.now());
        f.setDeletedBy(SecurityUtils.currentUserId());
        lessonFileRepo.save(f);
    }

    /*
     * ================================================================
     * PRIVATE HELPERS
     * ================================================================
     */

    private Lesson getOrThrow(Integer id) {
        return lessonRepo
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài giảng id=" + id));
    }

    private void validateRefs(LessonRequest req) {
        if (!subjectRepo.existsById(req.subjectId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy môn học id=" + req.subjectId());
        }
        if (!branchRepo.existsById(req.branchId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy chi nhánh id=" + req.branchId());
        }
        if (req.teacherId() != null && !teacherRepo.existsById(req.teacherId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy giáo viên id=" + req.teacherId());
        }
    }

    private void apply(Lesson lesson, LessonRequest req) {
        lesson.setSubjectId(req.subjectId()); // dùng SubjectId FK, không dùng Category text
        lesson.setTeacherId(req.teacherId());
        lesson.setBranchId(req.branchId());
        lesson.setTitle(req.title());
        lesson.setDescription(req.description());
        lesson.setContent(req.content());
        lesson.setGradeLevel(req.gradeLevel());
        lesson.setDuration(req.duration());
        lesson.setDifficultyLevel(
                req.difficultyLevel() != null && !req.difficultyLevel().isBlank() ? req.difficultyLevel() : null);
        lesson.setStatus(req.status());
    }

    private LessonResponse buildResponse(Lesson l) {
        Subject subject = l.getSubjectId() != null
                ? subjectRepo.findById(l.getSubjectId()).orElse(null)
                : null;
        String subjectName = subject != null ? subject.getName() : "";
        String category = subject != null ? subject.getCategory() : null;
        List<LessonFileResponse> files = lessonFileRepo.findByLessonId(l.getId()).stream()
                .map(LessonFileResponse::fromEntity)
                .toList();
        return LessonResponse.fromEntity(l, subjectName, category, files);
    }

    private Map<Integer, Subject> buildSubjectMap(List<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        return subjectRepo.findAllById(ids).stream().collect(Collectors.toMap(Subject::getId, s -> s));
    }
}
