package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.common.SearchText;
import com.kdc.tsdms.dto.SchoolDetailResponse;
import com.kdc.tsdms.dto.SchoolRequest;
import com.kdc.tsdms.dto.SchoolResponse;
import com.kdc.tsdms.entity.AssignmentStatus;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import com.kdc.tsdms.repository.RoomRepository;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.ServiceContractRepository;
import com.kdc.tsdms.repository.StudentRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /** Buổi sáng trong khung tiết (cột Period.SessionType). */
    private static final String BUOI_SANG = "MORNING";

    /**
     * Nhãn tiếng Việt cho mã bảng con mà {@code SchoolRepository.countChildRowsBySchoolId} trả về.
     * Thêm bảng con mới thì thêm cả ở đây lẫn ở câu SQL bên repository.
     */
    private static final Map<String, String> NHAN_BANG_CON = Map.of(
            "class", "lớp học",
            "student", "hồ sơ học sinh",
            "assignment", "phân công",
            "slot", "ô lịch tuần",
            "contract", "hợp đồng dịch vụ",
            "holiday", "lịch nghỉ riêng",
            "evaluation", "phiếu đánh giá");

    private static final Logger log = LoggerFactory.getLogger(SchoolService.class);

    private final SchoolRepository sRepo;
    private final BranchRepository bRepo;
    private final SchoolClassRepository classRepo;
    private final AssignmentRepository assignmentRepo;
    private final ServiceContractRepository serviceContractRepo;
    private final StudentRepository studentRepo;
    private final PeriodRepository periodRepo;
    private final RoomRepository roomRepo;
    private final PeriodService periodService;

    public SchoolService(
            SchoolRepository schoolRepo,
            BranchRepository branchRepo,
            SchoolClassRepository classRepo,
            AssignmentRepository assignmentRepo,
            ServiceContractRepository serviceContractRepo,
            StudentRepository studentRepo,
            PeriodRepository periodRepo,
            RoomRepository roomRepo,
            PeriodService periodService) {
        this.sRepo = schoolRepo;
        this.bRepo = branchRepo;
        this.classRepo = classRepo;
        this.assignmentRepo = assignmentRepo;
        this.serviceContractRepo = serviceContractRepo;
        this.studentRepo = studentRepo;
        this.periodRepo = periodRepo;
        this.roomRepo = roomRepo;
        this.periodService = periodService;
    }

    /* ── Danh sách có phân trang + tìm kiếm/lọc ── */
    @Transactional(readOnly = true)
    public Page<SchoolResponse> search(
            String keyword, Integer branchId, String status, Integer expiringInDays, Pageable pageable) {
        LocalDate today = BusinessTime.today();
        Page<School> page = sRepo.search(
                SearchText.escapeLike(SearchText.blankToNull(keyword)),
                branchId,
                SearchText.blankToNull(status),
                today,
                expiringInDays == null ? null : today.plusDays(expiringInDays),
                pageable);
        return dungTrang(page, today);
    }

    /* ── Thùng rác: trường đã xóa mềm ── */
    @Transactional(readOnly = true)
    public List<SchoolResponse> listTrash() {
        LocalDate today = BusinessTime.today();
        List<School> rows = sRepo.findByDeletedTrueOrderByDeletedAtDesc();
        Map<Integer, String> tenChiNhanh = tenChiNhanhCua(rows);
        Map<Integer, Integer> soTiet = soTietCua(rows);
        return rows.stream().map(s -> toResponse(s, tenChiNhanh, soTiet, today)).toList();
    }

    /* ── Chi tiết ── */
    @Transactional(readOnly = true)
    public SchoolResponse getById(Integer id) {
        School s = findActiveOrThrow(id);
        return toResponse(s, tenChiNhanhCua(List.of(s)), soTietCua(List.of(s)), BusinessTime.today());
    }

    /**
     * Số liệu kèm theo một trường, cho khối chi tiết mở rộng ở màn danh sách.
     *
     * <p>Hợp đồng dịch vụ ở đây là CHỈ ĐỌC và lấy từ bảng {@code ServiceContract} — khác với hai ô
     * ngày hợp đồng nằm ngay trên bảng School mà form này sửa được. Hai nguồn đó không tự đồng bộ
     * với nhau, nên hiện cả hai để người dùng thấy ngay khi chúng lệch, thay vì mỗi màn hình tin
     * một con số.
     */
    @Transactional(readOnly = true)
    public SchoolDetailResponse detail(Integer id) {
        School s = findActiveOrThrow(id);
        List<Period> khungTiet = periodRepo.findBySchoolIdAndDeletedFalseOrderByPeriodNumber(id);
        List<SchoolDetailResponse.ContractRow> hopDong =
                serviceContractRepo.findBySchoolIdAndDeletedFalseOrderByEndDateDesc(id).stream()
                        .map(c -> new SchoolDetailResponse.ContractRow(
                                c.getContractCode(),
                                c.getStartDate(),
                                c.getEndDate(),
                                c.getContractValue(),
                                c.getStatus()))
                        .toList();
        return new SchoolDetailResponse(
                s.getId(),
                classRepo.countBySchoolIdAndDeletedFalse(id),
                assignmentRepo.demGiaoVienDangDay(id, PHAN_CONG_CON_HIEU_LUC),
                studentRepo.countBySchoolIdAndDeletedFalse(id),
                khungTiet.size(),
                (int) khungTiet.stream()
                        .filter(p -> BUOI_SANG.equals(p.getSessionType()))
                        .count(),
                hopDong);
    }

    /* ── Tạo mới ── */
    @Transactional
    public SchoolResponse create(SchoolRequest req) {
        validateBranch(req.branchId());

        School s = new School();
        apply(s, req);
        // Tên lưu = tiền tố cấp học + tên riêng ("Ban Mai" + THCS -> "THCS Ban Mai").
        s.setName(ghepTenTheoCap(req.name(), req.educationLevel()));
        assertTenChuaDung(req.branchId(), s.getName(), null);
        s.setCreatedBy(SecurityUtils.currentUserId());
        School saved = sRepo.save(s);

        sinhKhungTietChuan(saved, req.educationLevel());
        return toResponse(saved, tenChiNhanhCua(List.of(saved)), soTietCua(List.of(saved)), BusinessTime.today());
    }

    /* ── Cập nhật ── */
    @Transactional
    public SchoolResponse update(Integer id, SchoolRequest req) {
        School s = findActiveOrThrow(id);
        validateBranch(req.branchId());

        apply(s, req);
        // Form SỬA không có ô Cấp học (khung tiết đã dùng xếp lịch, không đổi ngầm được) nên cấp
        // học lấy từ chính cái tên đang nhập. Cùng MỘT luật ghép tên cho cả tạo lẫn sửa: trước
        // đây update lưu thẳng chuỗi người dùng gõ, nên mở "THCS Ban Mai" ra sửa số điện thoại
        // rồi bấm Lưu là tên mất tiền tố, tra bằng tên không ra trường nữa.
        s.setName(ghepTenTheoCap(req.name(), capTheoTen(req.name())));
        assertTenChuaDung(req.branchId(), s.getName(), id);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        School saved = sRepo.save(s);
        return toResponse(saved, tenChiNhanhCua(List.of(saved)), soTietCua(List.of(saved)), BusinessTime.today());
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
        s.setStatus(School.INACTIVE);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        sRepo.save(s);
    }

    /**
     * Khôi phục một trường từ thùng rác.
     *
     * <p>Trạng thái CỐ Ý giữ nguyên INACTIVE (do {@link #delete} hạ xuống) chứ không tự bật lại
     * ACTIVE: trường vừa moi ra khỏi thùng rác chưa chắc đã ký lại hợp đồng, mà ACTIVE là nó nhận
     * được phân công mới ngay. Người dùng tự bật khi đã chắc.
     */
    @Transactional
    public SchoolResponse restore(Integer id) {
        School s = sRepo.findByIdAndDeletedTrue(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường trong thùng rác id=" + id));
        // Trong lúc trường nằm trong thùng rác, người khác có thể đã tạo trường trùng tên ở cùng
        // chi nhánh. Khôi phục vào là hai dòng trùng tên — chỉ mục lọc UX_School_BranchName (V36)
        // sẽ chặn ở tầng DB, nhưng lỗi của nó khó hiểu hơn câu này nhiều.
        assertTenChuaDung(s.getBranchId(), s.getName(), id);
        s.setDeleted(false);
        s.setDeletedAt(null);
        s.setDeletedBy(null);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserId());
        School saved = sRepo.save(s);
        return toResponse(saved, tenChiNhanhCua(List.of(saved)), soTietCua(List.of(saved)), BusinessTime.today());
    }

    /**
     * Xóa VĨNH VIỄN một trường đang nằm trong thùng rác.
     *
     * <p>Chặn nếu còn bất kỳ dòng con nào ở 7 bảng nghiệp vụ — kể cả dòng đã xóa mềm, vì khóa
     * ngoại chặn theo sự tồn tại của dòng chứ không nhìn cờ xóa. Hệ quả cố ý: trường đã từng chạy
     * thật thì gần như không xóa cứng được, nó nằm lại thùng rác và khôi phục được. Chỉ trường
     * tạo nhầm mới xóa hẳn — đúng thứ nút này cần phục vụ.
     *
     * <p>Khung tiết và phòng học thì XÓA KÈM: chúng là cấu hình của riêng trường, giữ lại chỉ để
     * lại rác trỏ vào một trường không còn tồn tại. Phải xóa TRƯỚC dòng School vì khóa ngoại của
     * chúng trỏ vào đây.
     */
    @Transactional
    public void purge(Integer id) {
        School s = sRepo.findByIdAndDeletedTrue(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường trong thùng rác id=" + id));
        DeleteGuard.of("vĩnh viễn trường " + s.getName())
                .blockAll(moTaDuLieuCon(sRepo.countChildRowsBySchoolId(id)))
                .huongDan("Lớp, phân công, hợp đồng và hồ sơ học sinh là dữ liệu nghiệp vụ đã phát sinh — không "
                        + "xóa kèm để dọn đường. Trường vẫn nằm trong thùng rác và khôi phục lại được.")
                .check();
        periodRepo.xoaCungTheoTruong(id);
        roomRepo.xoaCungTheoTruong(id);
        sRepo.delete(s);
        // flush để lỗi khóa ngoại (nếu còn bảng nào chưa kể trong câu đếm) nổ NGAY tại đây thay vì
        // lúc commit — khi đó stack trace không còn chỉ về hàm này nữa.
        sRepo.flush();
    }

    /* ── PRIVATE ── */

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
     * <p>Không chọn cấp (gọi API trực tiếp, script cũ) thì giữ nguyên tên như đã gửi. Gõ đúng mỗi
     * tiền tố ("THCS") thì cắt xong không còn gì — lúc đó trả lại nguyên chuỗi người dùng gõ chứ
     * không ghép thành "THCS THCS".
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
            if (token.length >= cum.length && khopDau(khongDau, cum)) {
                token = Arrays.copyOfRange(token, cum.length, token.length);
                break;
            }
        }
        String tenRieng = String.join(" ", token);
        return tenRieng.isEmpty() ? ten : educationLevel + " " + tenRieng;
    }

    /** "TH" | "THCS" | null — cấp học suy từ tên trường, dạng chuỗi để ghép thẳng vào tên. */
    static String capTheoTen(String name) {
        Boolean tieuHoc = PeriodService.suyCapTuTen(name);
        if (tieuHoc == null) {
            return null;
        }
        return tieuHoc ? "TH" : "THCS";
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
     * KHÔNG sinh gì cả và cũng KHÔNG báo lỗi: nút áp khung ở màn Quản lý trường vẫn còn nguyên
     * làm lối thoát. Đoán bừa nguy hiểm hơn là bỏ trống — gán nhầm khung 35 phút cho trường THCS
     * là sai giờ toàn bộ lịch dạy về sau mà không có lỗi nào bắn ra.
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
                            + " dùng nút 'Áp khung tiết chuẩn' ở màn Quản lý trường.",
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

    /**
     * Chặn hai trường trùng tên trong CÙNG một chi nhánh.
     *
     * <p>Trùng tên không làm hỏng dữ liệu, nhưng làm hỏng mọi chỗ CHỌN trường: dropdown phân công
     * hiện hai dòng "THCS Ban Mai" y hệt nhau, chọn nhầm là cả chuỗi lịch dạy — chấm công — lương
     * đi sang trường khác. Khác chi nhánh thì cho phép: hai tỉnh có thể có trường trùng tên thật.
     *
     * <p>Chốt cuối nằm ở chỉ mục lọc UX_School_BranchName (V36) — hai request gửi cùng lúc thì
     * câu {@code exists} này đều thấy "chưa có".
     */
    private void assertTenChuaDung(Integer branchId, String name, Integer selfId) {
        boolean trung = selfId == null
                ? sRepo.existsByBranchIdAndNameAndDeletedFalse(branchId, name)
                : sRepo.existsByBranchIdAndNameAndDeletedFalseAndIdNot(branchId, name, selfId);
        if (trung) {
            throw new ApiException(HttpStatus.CONFLICT, "Chi nhánh này đã có trường tên '" + name + "'");
        }
    }

    /** Đổi kết quả thô của {@code countChildRowsBySchoolId} thành các cụm chữ kiểu "3 lớp học". */
    private static List<String> moTaDuLieuCon(List<Object[]> rows) {
        return rows.stream()
                .map(r -> {
                    String loai = String.valueOf(r[0]);
                    long soLuong = ((Number) r[1]).longValue();
                    return soLuong + " " + NHAN_BANG_CON.getOrDefault(loai, loai);
                })
                .toList();
    }

    private School findActiveOrThrow(Integer id) {
        return sRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường id=" + id));
    }

    private void validateBranch(Integer branchId) {
        if (!bRepo.existsById(branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chi nhánh id=" + branchId + " không tồn tại");
        }
    }

    /** Gán các cột nhập trực tiếp từ form. Cột Name do bên gọi tự đặt (còn phải ghép cấp học). */
    private void apply(School s, SchoolRequest req) {
        s.setBranchId(req.branchId());
        s.setAddress(req.address());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setContactPerson(req.contactPerson());
        s.setContractStartDate(req.contractStartDate());
        s.setContractEndDate(req.contractEndDate());
        s.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : School.ACTIVE);
    }

    /**
     * Dựng response cho CẢ TRANG: lấy tên chi nhánh và số tiết bằng 2 câu gộp thay vì hỏi lẻ từng
     * dòng. Bản cũ gọi {@code findById} cho mỗi trường nên một trang 20 dòng là 21 lượt truy vấn.
     */
    private Page<SchoolResponse> dungTrang(Page<School> page, LocalDate today) {
        List<School> rows = page.getContent();
        Map<Integer, String> tenChiNhanh = tenChiNhanhCua(rows);
        Map<Integer, Integer> soTiet = soTietCua(rows);
        return page.map(s -> toResponse(s, tenChiNhanh, soTiet, today));
    }

    private SchoolResponse toResponse(
            School s, Map<Integer, String> tenChiNhanh, Map<Integer, Integer> soTiet, LocalDate today) {
        return SchoolResponse.fromEntity(s, tenChiNhanh.get(s.getBranchId()), soTiet.getOrDefault(s.getId(), 0), today);
    }

    private Map<Integer, String> tenChiNhanhCua(List<School> rows) {
        List<Integer> ids = rows.stream().map(School::getBranchId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bRepo.findAllById(ids).stream().collect(Collectors.toMap(Branch::getId, Branch::getName, (a, b) -> a));
    }

    private Map<Integer, Integer> soTietCua(List<School> rows) {
        List<Integer> ids = rows.stream().map(School::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return periodRepo.demTietTheoTruong(ids).stream()
                .collect(Collectors.toMap(r -> (Integer) r[0], r -> ((Number) r[1]).intValue()));
    }
}
