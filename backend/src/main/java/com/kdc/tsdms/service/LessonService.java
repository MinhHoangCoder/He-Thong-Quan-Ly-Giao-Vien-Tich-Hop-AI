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
import com.kdc.tsdms.repository.SubjectCategoryRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * GradeLevel là text tự do (vd "Lớp 4", "Lớp 9"), không liên kết bảng
 * SchoolClass.
 */
@Service
public class LessonService {

    private static final Path UPLOAD_ROOT =
            Paths.get("uploads/lessons").toAbsolutePath().normalize();

    private static final long MAX_UPLOAD_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB

    /**
     * Giới hạn mô tả bài giảng: tối đa 200 TỪ (không phải ký tự). @Size trên
     * LessonRequest chỉ đếm ký tự nên không đủ để chặn theo số từ -> chặn thêm ở
     * đây, trước khi lưu (áp dụng cho cả tạo mới lẫn sửa).
     */
    private static final int MAX_DESCRIPTION_WORDS = 200;

    /** Khối lớp gợi ý cho dropdown — text tự do, không ràng buộc DB. */
    private static final List<String> GRADE_LEVELS =
            List.of("Lớp 1", "Lớp 2", "Lớp 3", "Lớp 4", "Lớp 5", "Lớp 6", "Lớp 7", "Lớp 8", "Lớp 9");

    private final LessonRepository lessonRepo;
    private final SubjectCategoryRepository subjectCategoryRepo;
    private final LessonFileRepository lessonFileRepo;
    private final BranchRepository branchRepo;
    private final TeacherRepository teacherRepo;
    private final SubjectRepository subjectRepo;

    public LessonService(
            LessonRepository lessonRepo,
            LessonFileRepository lessonFileRepo,
            BranchRepository branchRepo,
            TeacherRepository teacherRepo,
            SubjectRepository subjectRepo,
            SubjectCategoryRepository subjectCategoryRepo) {
        this.lessonRepo = lessonRepo;
        this.lessonFileRepo = lessonFileRepo;
        this.branchRepo = branchRepo;
        this.teacherRepo = teacherRepo;
        this.subjectRepo = subjectRepo;
        this.subjectCategoryRepo = subjectCategoryRepo;
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
     * Danh sách tên nhóm môn ACTIVE — dùng cho dropdown bộ lọc bài giảng.
     * Nguồn: SubjectCategory table (thay vì distinct string từ Subject.Category).
     */
    public List<String> getCategories() {
        return subjectCategoryRepo.findByStatusAndDeletedFalseOrderByName("ACTIVE").stream()
                .map(sc -> sc.getName())
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
            String categoryName = subj != null && subj.getCategory() != null
                    ? subj.getCategory().getName()
                    : null;
            return LessonSummary.fromEntity(l, subjectName, categoryName);
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
        validateDescriptionWordLimit(req.description());
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
        validateDescriptionWordLimit(req.description());
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

    /**
     * Xóa mềm bài giảng, CASCADE mềm xuống các file đính kèm.
     *
     * <p>Khác {@code School}/{@code Teacher} (chặn hẳn): {@code LessonFile} không có đời sống
     * riêng — nó chỉ tồn tại vì bài giảng, không nơi nào tham chiếu tới nó ngoài bài giảng, nên
     * xóa theo là đúng chứ không phải là mất dữ liệu ngoài ý muốn. Trước đây chỉ đánh dấu mỗi
     * bài giảng, file con vẫn {@code deleted = false} nên vẫn lọt vào các truy vấn đếm/liệt kê
     * file (không truy vấn nào lọc theo cờ xóa của bài giảng cha).
     *
     * <p>KHÔNG đụng tới file vật lý trên đĩa: xóa mềm phải khôi phục được.
     */
    @Transactional
    public void delete(Integer id) {
        Lesson lesson = getOrThrow(id);
        Instant now = Instant.now();
        Integer uid = SecurityUtils.currentUserId();

        List<LessonFile> files = lessonFileRepo.findByLessonIdAndDeletedFalse(id);
        for (LessonFile f : files) {
            f.setDeleted(true);
            f.setDeletedAt(now);
            f.setDeletedBy(uid);
        }
        lessonFileRepo.saveAll(files);

        lesson.setDeleted(true);
        lesson.setDeletedAt(now);
        lesson.setDeletedBy(uid);
        lessonRepo.save(lesson);
    }

    /*
     * ================================================================
     * 6. UPLOAD FILE PDF
     * ================================================================
     */

    @Transactional
    public List<LessonFileResponse> uploadFiles(Integer lessonId, List<MultipartFile> files) {
        Lesson lesson = getOrThrow(lessonId);
        if (files == null || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất 1 file");
        }

        Path dir = UPLOAD_ROOT.resolve(String.valueOf(lessonId));
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

            if (f.getSize() > MAX_UPLOAD_FILE_SIZE_BYTES) {
                throw new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "File \"" + original + "\" vượt quá dung lượng cho phép (tối đa 20MB)");
            }

            // Kiểm tra Content-Type
            if (!"application/pdf".equalsIgnoreCase(f.getContentType())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ cho phép upload file PDF");
            }
            try (InputStream is = f.getInputStream()) {
                byte[] header = new byte[4];

                if (is.read(header) != 4
                        || header[0] != '%'
                        || header[1] != 'P'
                        || header[2] != 'D'
                        || header[3] != 'F') {

                    throw new ApiException(HttpStatus.BAD_REQUEST, "File không phải PDF hợp lệ");
                }
            } catch (IOException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể đọc file PDF");
            }

            // Kiểm tra phần mở rộng
            int dot = original.lastIndexOf('.');
            String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase() : "";

            if (!"pdf".equals(ext)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ cho phép upload file PDF");
            }
            String stored = UUID.randomUUID() + "." + ext;

            // Phòng thủ nhiều lớp: khẳng định đường dẫn đích vẫn nằm TRONG thư mục upload.
            Path target = dir.resolve(stored).normalize();
            if (!target.startsWith(dir.normalize())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
            }

            try {
                Files.copy(f.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi lưu file: " + original);
            }

            LessonFile lf = new LessonFile();
            lf.setLessonId(lessonId);
            lf.setFileName(original);
            String relativePath = lessonId + "/" + stored;
            lf.setFileUrl(relativePath);
            lf.setFileType(ext);
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

        // FIX (2026-07-28): bản vá trước (2026-07-10) coi mỗi bài giảng chỉ có 1
        // link Canva "chính" nên tìm bản ghi fileType=canva ĐẦU TIÊN của bài giảng
        // rồi UPDATE đè lên nó thay vì tạo bản ghi mới. Hệ quả: khi người dùng
        // thêm link Canva thứ 2, backend trả về CÙNG id với link thứ nhất (vì đã
        // bị ghi đè trong DB), trong khi FE (LessonFormPage.vue) vẫn push thêm 1
        // phần tử mới vào mảng hiển thị -> mảng có 2 dòng NHÌN khác nhau nhưng
        // TRÙNG id. Bấm xóa 1 dòng (theo id) sẽ khớp cả 2 dòng trùng id đó -> cả
        // 2 biến mất cùng lúc thay vì xóa từng cái.
        // -> Mỗi lần "Thêm" LUÔN INSERT một bản ghi LessonFile MỚI (id riêng), để
        // 1 bài giảng có thể có nhiều link Canva (nhiều "slide") và xóa được từng
        // link một cách độc lập.
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

    /**
     * Làm sạch tên file trước khi đưa vào header Content-Disposition.
     *
     * <p>{@link ContentDisposition} escape dấu nháy kép và mã hóa tên có dấu, nhưng KHÔNG
     * bỏ ký tự điều khiển: tên chứa xuống dòng (CR/LF) vẫn lọt nguyên vào phần
     * {@code filename="..."}. Mà xuống dòng giữa header chính là cách chèn header giả —
     * nhẹ thì Tomcat chặn và trả 500, nặng thì client đọc nhầm response. Người dùng gõ tay
     * không tạo ra tên như vậy, nhưng request nặn thủ công (curl/Postman) thì có.
     *
     * <p>Package-private để test gọi trực tiếp — cùng kiểu với hằng đơn giá của PayrollService.
     */
    static String safeFileName(String raw) {
        if (raw == null) {
            return "tai-lieu";
        }
        String cleaned = raw.replaceAll("\\p{Cntrl}", "").trim();
        return cleaned.isEmpty() ? "tai-lieu" : cleaned;
    }

    /**
     * Đếm số từ (tách theo khoảng trắng) của mô tả và chặn nếu vượt quá
     * {@link #MAX_DESCRIPTION_WORDS}. Mô tả rỗng/null luôn hợp lệ.
     */
    private void validateDescriptionWordLimit(String description) {
        if (description == null || description.isBlank()) return;
        int wordCount = description.trim().split("\\s+").length;
        if (wordCount > MAX_DESCRIPTION_WORDS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Mô tả tối đa " + MAX_DESCRIPTION_WORDS + " từ (hiện tại: " + wordCount + " từ)");
        }
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
        String category = subject != null && subject.getCategory() != null
                ? subject.getCategory().getName()
                : null;
        List<LessonFileResponse> files = lessonFileRepo.findByLessonId(l.getId()).stream()
                .map(LessonFileResponse::fromEntity)
                .toList();
        return LessonResponse.fromEntity(l, subjectName, category, files);
    }

    private Map<Integer, Subject> buildSubjectMap(List<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        return subjectRepo.findAllById(ids).stream().collect(Collectors.toMap(Subject::getId, s -> s));
    }

    /**
     * Mở / tải 1 file đính kèm của bài giảng.
     *
     * FIX (2026-07-10): trước đây method này KHÔNG được controller nào gọi tới
     * (dead code) nên FE không có cách nào tải file PDF về máy một cách an toàn
     * (có kiểm tra quyền) — phải trỏ thẳng vào đường dẫn tĩnh "/uploads/..." vốn
     * chưa được cấu hình proxy ở môi trường dev nên xem/tải bị lỗi âm thầm.
     * Nay được gọi từ
     * {@code GET /api/v1/lessons/{lessonId}/files/{fileId}/download}.
     *
     * @param lessonId       id bài giảng — dùng để xác thực file thực sự thuộc
     *                       bài giảng này (chặn dò fileId của bài giảng khác).
     * @param fileId         id file đính kèm cần mở.
     * @param forcePublished true nếu người gọi là TEACHER (chỉ được phép mở file
     *                       của bài giảng đã PUBLISHED, khớp rule ở getDetail()).
     */
    @Transactional(readOnly = true)
    public ResponseEntity<?> openFile(Integer lessonId, Integer fileId, boolean forcePublished) {

        Lesson lesson = getOrThrow(lessonId);
        if (forcePublished && !"PUBLISHED".equals(lesson.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài giảng id=" + lessonId);
        }

        LessonFile file = lessonFileRepo
                .findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy file"));

        if (!file.getLessonId().equals(lessonId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File không thuộc bài giảng này");
        }

        // Nếu là Canva -> trả về redirect (FE thường tự window.open() thẳng
        // fileUrl nên ít khi cần gọi endpoint này cho canva, nhưng vẫn hỗ trợ
        // để link chia sẻ trực tiếp /download cũng mở đúng).
        if ("canva".equalsIgnoreCase(file.getFileType())) {

            URI uri;
            try {
                uri = URI.create(file.getFileUrl());
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "URL Canva không hợp lệ");
            }

            String host = uri.getHost();

            if (host == null
                    || !(host.equalsIgnoreCase("canva.com")
                            || host.equalsIgnoreCase("www.canva.com")
                            || host.equalsIgnoreCase("canva.link"))) {

                throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ cho phép liên kết Canva");
            }

            return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
        }

        try {
            // fileUrl dạng: /uploads/lessons/{lessonId}/{storedFileName} -> lấy phần
            // storedFileName cuối cùng
            Path path = UPLOAD_ROOT.resolve(file.getFileUrl()).normalize();

            if (!path.startsWith(UPLOAD_ROOT)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Đường dẫn file không hợp lệ");
            }
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "File không tồn tại trên server: " + file.getFileName());
            }

            // FileName là tên NGƯỜI DÙNG đặt lúc upload -> KHÔNG ghép thẳng vào header.
            // Ghép chuỗi tay có 2 vấn đề:
            //   1. Tên chứa dấu " sẽ cắt ngang giá trị header -> trình duyệt đọc sai tên,
            //      xa hơn là mở đường cho chèn header giả (header injection).
            //   2. Tên tiếng Việt có dấu về máy thành chuỗi rác "GiÃ¡o Ã¡n ToÃ¡n.pdf".
            // ContentDisposition lo cả hai: escape dấu " và thêm filename*=UTF-8'' (RFC 5987).
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(safeFileName(file.getFileName()), StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy file");
        }
    }
}
