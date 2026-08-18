package com.kdc.tsdms.service;

import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.dto.SchoolResponse;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.StudentRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ CRUD "Trường khách hàng" (bảng School). */
@Service
public class SchoolService {

    /** Trạng thái phân công còn GIỮ CHỖ khung giờ — xem {@code Assignment.holdsTimeSlot()}. */
    private static final List<String> PHAN_CONG_CON_HIEU_LUC =
            List.of(AssignmentStatus.ACTIVE, AssignmentStatus.PENDING);

    private static final Logger log = LoggerFactory.getLogger(SchoolService.class);

    private final SchoolRepository sRepo;
    private final BranchRepository bRepo;
    private final SchoolClassRepository classRepo;
    private final AssignmentRepository assignmentRepo;
    private final ServiceContractRepository serviceContractRepo;
    private final StudentRepository studentRepo;
    private final PeriodService periodService;

    public SchoolService(
            SchoolRepository schoolRepo,
            BranchRepository branchRepo,
            SchoolClassRepository classRepo,
            AssignmentRepository assignmentRepo,
            ServiceContractRepository serviceContractRepo,
            StudentRepository studentRepo,
            PeriodService periodService) {
        this.sRepo = schoolRepo;
        this.bRepo = branchRepo;
        this.classRepo = classRepo;
        this.assignmentRepo = assignmentRepo;
        this.serviceContractRepo = serviceContractRepo;
        this.studentRepo = studentRepo;
        this.periodService = periodService;
    }

    /* ── Danh sách có phân trang + tìm kiếm/lọc ── */
    @Transactional(readOnly = true)
    public Page<SchoolResponse> search(String keyword, Integer branchId, String status, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String st = (status == null || status.isBlank()) ? null : status;
        return sRepo.search(kw, branchId, st, pageable).map(this::toResponse);
    }

    /* ── Chi tiết ── */
    @Transactional(readOnly = true)
    public SchoolResponse getById(Integer id) {
        return toResponse(findActiveOrThrow(id));
    }

    /* ── Tạo mới ── */
    @Transactional
    public SchoolResponse create(SchoolRequest req) {
        validateBranch(req.branchId());

        School s = new School();
        apply(s, req);
        // Ghi đè tên bằng bản đã ghép tiền tố cấp học ("Ban Mai" + THCS -> "THCS Ban Mai").
        s.setName(ghepTenTheoCap(req.name(), req.educationLevel()));
        s.setCreatedBy(SecurityUtils.currentUserId());
        School saved = sRepo.save(s);

        sinhKhungTietChuan(saved, req.educationLevel());
        return toResponse(saved);
    }

    /**
     * Các cụm từ chỉ cấp học có thể đứng đầu tên trường, viết KHÔNG DẤU và tách sẵn thành token.
     * Xếp từ dài tới ngắn để "truong trung hoc co so" được cắt trọn thay vì chỉ cắt được "truong".
     */
    private static final List<String[]> TIEN_TO_CAP = List.of(
            new String[] {"truong", "trung", "hoc", "co", "so"},
            new String[] {"trung", "hoc", "co", "so"},
            new String[] {"truong", "tieu", "hoc"},
            new String[] {"tieu", "hoc"},
            new String[] {"truong", "thcs"},
            new String[] {"truong", "th"},
            new String[] {"thcs"},
            new String[] {"th"});

    /**
     * Ghép tên hiển thị của trường = tiền tố cấp học + tên riêng.
     *
     * <p>Ô "Tên trường" trên form giờ chỉ nhận TÊN RIÊNG ("Ban Mai"); phần "TH"/"THCS" do ô Cấp
     * học quyết định. Nhờ vậy tên hiển thị và khung tiết LUÔN khớp nhau — trước đây hai thứ này
     * nhập rời nhau nên có thể chỏi ("THCS Ban Mai" mà lại chạy khung tiểu học 35 phút, khiến mọi
     * buổi dạy sai giờ mà không có lỗi nào bắn ra).
     *
     * <p>Người dùng lỡ gõ/dán cả tiền tố ("THCS Ban Mai") thì cắt bỏ rồi ghép lại, để không ra
     * "THCS THCS Ban Mai". Cắt theo TOKEN chứ không theo vị trí ký tự: bỏ dấu tiếng Việt bằng
     * NFD làm đổi độ dài chuỗi nên đếm chỉ số là sai.
     *
     * <p>Không chọn cấp (gọi API trực tiếp, script cũ) thì giữ nguyên tên như đã gửi.
     */
    static String ghepTenTheoCap(String name, String educationLevel) {
        String ten = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (educationLevel == null || educationLevel.isBlank()) {
            return ten;
        }
        String[] token = ten.isEmpty() ? new String[0] : ten.split(" ");
        String[] khongDau = new String[token.length];
        for (int i = 0; i < token.length; i++) {
            khongDau[i] = boDau(token[i].toLowerCase());
        }
        for (String[] cum : TIEN_TO_CAP) {
            if (token.length > cum.length && khopDau(khongDau, cum)) {
                token = Arrays.copyOfRange(token, cum.length, token.length);
                break;
            }
        }
        String tenRieng = String.join(" ", token);
        return tenRieng.isEmpty() ? ten : educationLevel + " " + tenRieng;
    }

    private static boolean khopDau(String[] khongDau, String[] cum) {
        for (int i = 0; i < cum.length; i++) {
            if (!cum[i].equals(khongDau[i])) {
                return false;
            }
        }
        return true;
    }

    /** Bỏ dấu tiếng Việt để so khớp không phụ thuộc người nhập có gõ dấu hay không. */
    private static String boDau(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
    }

    /**
     * Sinh sẵn khung tiết chuẩn cho trường VỪA TẠO.
     *
     * <p>VÌ SAO: trường mới luôn có 0 tiết, mà 0 tiết thì KHÔNG phân công được. Trước đây người
     * dùng phải tự mò sang màn Phân công bấm nút "Áp khung tiết chuẩn" — mà nút đó lại đòi trường
     * phải có lớp trước (cấp học suy từ khối lớp cao nhất). Thêm trường xong không dùng được
     * ngay là một cái bẫy im lặng.
     *
     * <p>Cấp học lấy theo thứ tự: người tạo chọn -> đoán từ tên trường -> chịu. Trường hợp cuối
     * KHÔNG sinh gì cả và cũng KHÔNG báo lỗi: nút áp khung ở màn Phân công vẫn còn nguyên làm
     * lối thoát. Đoán bừa nguy hiểm hơn là bỏ trống — gán nhầm khung 35 phút cho trường THCS là
     * sai giờ toàn bộ lịch dạy về sau mà không có lỗi nào bắn ra.
     *
     * <p>Lỗi ở bước này KHÔNG được làm hỏng việc tạo trường: khung tiết là tiện ích đi kèm, còn
     * bản ghi trường mới là thao tác chính người dùng yêu cầu.
     */
    private void sinhKhungTietChuan(School saved, String educationLevel) {
        Boolean tieuHoc;
        if ("TH".equals(educationLevel)) {
            tieuHoc = Boolean.TRUE;
        } else if ("THCS".equals(educationLevel)) {
            tieuHoc = Boolean.FALSE;
        } else {
            tieuHoc = PeriodService.suyCapTuTen(saved.getName());
        }
        if (tieuHoc == null) {
            log.info(
                    "Trường '{}' (id={}) chưa xác định được cấp học nên chưa sinh khung tiết —"
                            + " dùng nút 'Áp khung tiết chuẩn' ở màn Phân công sau khi đã thêm lớp.",
                    saved.getName(),
                    saved.getId());
            return;
        }
        try {
            periodService.applyStandardFrame(saved.getId(), saved.getName(), tieuHoc);
        } catch (RuntimeException ex) {
            log.warn(
                    "Tạo trường '{}' (id={}) thành công nhưng sinh khung tiết thất bại: {}",
                    saved.getName(),
                    saved.getId(),
                    ex.getMessage());
        }
    }

    /* ── Cập nhật ── */
    @Transactional
    public SchoolResponse update(Integer id, SchoolRequest req) {
        School s = findActiveOrThrow(id);
        validateBranch(req.branchId());

        apply(s, req);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        return toResponse(sRepo.save(s));
    }

    /**
     * Xóa mềm một trường.
     *
     * <p>Luật RESTRICT: còn lớp, phân công đang chạy, hợp đồng dịch vụ hoặc hồ sơ học sinh thì
     * CẤM xóa. Trước đây hàm này không kiểm gì cả — xóa xong thì lớp, lịch dạy, hợp đồng của
     * trường vẫn sống nguyên và vẫn hiện ở mọi màn hình, chỉ là trỏ vào một cái tên đã biến mất
     * (không query nào trong dự án lọc theo cờ xóa của bảng CHA). Buổi dạy vẫn được chấm công,
     * vẫn vào lương, cho một trường về mặt sổ sách đã không còn.
     *
     * <p>CỐ Ý KHÔNG chặn theo Room và Period: đó là cấu hình thuộc về chính trường (phòng học,
     * khung tiết), không phải dữ liệu nghiệp vụ độc lập — chặn theo chúng thì mọi trường đã
     * seed đều không bao giờ xóa được.
     */
    @Transactional
    public void delete(Integer id) {
        School s = findActiveOrThrow(id);
        DeleteGuard.of("trường " + s.getName())
                .blockIf(classRepo.countBySchoolIdAndDeletedFalse(id), "lớp học")
                .blockIf(
                        assignmentRepo.countBySchoolIdAndStatusInAndDeletedFalse(id, PHAN_CONG_CON_HIEU_LUC),
                        "phân công đang chạy")
                .blockIf(serviceContractRepo.countBySchoolIdAndDeletedFalse(id), "hợp đồng dịch vụ")
                .blockIf(studentRepo.countBySchoolIdAndDeletedFalse(id), "hồ sơ học sinh")
                .check();
        s.setDeleted(true);
        s.setDeletedAt(Instant.now());
        s.setDeletedBy(SecurityUtils.currentUserId());
        s.setStatus("INACTIVE");
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        sRepo.save(s);
    }

    /* ── PRIVATE ── */

    private School findActiveOrThrow(Integer id) {
        return sRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường id=" + id));
    }

    private void validateBranch(Integer branchId) {
        if (!bRepo.existsById(branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chi nhánh id=" + branchId + " không tồn tại");
        }
    }

    private void apply(School s, SchoolRequest req) {
        s.setBranchId(req.branchId());
        s.setName(req.name());
        s.setAddress(req.address());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setContactPerson(req.contactPerson());
        s.setContractStartDate(req.contractStartDate());
        s.setContractEndDate(req.contractEndDate());
        s.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
    }

    private SchoolResponse toResponse(School s) {
        String branchName = bRepo.findById(s.getBranchId()).map(Branch::getName).orElse(null);
        return SchoolResponse.fromEntity(s, branchName);
    }
}
