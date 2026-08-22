package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.BulkClassDto;
import com.kdc.tsdms.dto.SchoolClassRequest;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.SchoolClassRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * THÊM LỚP HÀNG LOẠT.
 *
 * <p>Vì sao cần: mở một trường mới là phải nhập 12-15 lớp, mỗi lớp một lần bấm "Thêm" rồi điền
 * form rồi lưu. Nhân với 27 trường thì đó là vài trăm lần thao tác giống hệt nhau — công việc
 * mà máy làm đúng hơn người.
 *
 * <p>BA LỐI VÀO, MỘT ĐƯỜNG CODE
 *
 * <ol>
 *   <li><b>Sinh theo mẫu</b> — chọn khối và số lớp mỗi khối, máy tự đặt tên 1A1, 1A2… Đây là
 *       cách CHÍNH XÁC NHẤT vì không có file trung gian để gõ sai.
 *   <li><b>Dán từ Excel</b> — copy vùng ô rồi dán vào ô văn bản. Không cần lưu file, không cần
 *       tải lên.
 *   <li><b>Tải file .xlsx / .csv</b> — cho trường hợp danh sách do nhà trường gửi sang.
 * </ol>
 *
 * Cả ba đều dựng ra cùng một danh sách {@link BulkClassDto.Dong}, rồi qua cùng một bộ kiểm
 * ({@link SchoolClassService#kiemTraMotDong}) và cùng một đường ghi.
 *
 * <p>LUÔN XEM TRƯỚC RỒI MỚI GHI. Người dùng nhìn thấy từng dòng cùng lý do bị loại ("dòng 7:
 * lớp 5A1 đã tồn tại") trước khi bấm lưu. Nhập 100 dòng sai 2 dòng mà bắt làm lại từ đầu là
 * cách nhanh nhất để người ta quay về nhập tay.
 */
@Service
public class BulkClassService {

    /** Trần số dòng một lần nhập — chặn file rác và chặn cả cú dán nhầm cả bảng tính. */
    private static final int SO_DONG_TOI_DA = 500;

    /** Số lớp nhiều nhất một khối, khớp giới hạn tên lớp ở {@link SchoolClassService}. */
    private static final int SO_LOP_MOI_KHOI_TOI_DA = 20;

    private final SchoolClassService classService;
    private final SchoolClassRepository classRepo;
    private final SchoolRepository schoolRepo;

    public BulkClassService(
            SchoolClassService classService, SchoolClassRepository classRepo, SchoolRepository schoolRepo) {
        this.classService = classService;
        this.classRepo = classRepo;
        this.schoolRepo = schoolRepo;
    }

    /* ─────────────────────────── XEM TRƯỚC ─────────────────────────── */

    @Transactional(readOnly = true)
    public BulkClassDto.XemTruocResponse xemTruoc(BulkClassDto.XemTruocRequest req) {
        School school = requireSchool(req.schoolId());
        List<BulkClassDto.Dong> tho =
                "TEXT".equalsIgnoreCase(req.mode()) ? docVanBan(req.duLieu(), req.schoolYear()) : sinhTheoMau(req);
        return kiemTra(school, tho);
    }

    @Transactional(readOnly = true)
    public BulkClassDto.XemTruocResponse xemTruocFile(Integer schoolId, String schoolYear, MultipartFile file) {
        School school = requireSchool(schoolId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa chọn file.");
        }
        String ten = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<BulkClassDto.Dong> tho =
                ten.endsWith(".csv") ? docVanBan(docTextCsv(file), schoolYear) : docExcel(file, schoolYear);
        return kiemTra(school, tho);
    }

    /* ─────────────────────────── ĐỌC NGUỒN ─────────────────────────── */

    /**
     * Sinh tên lớp theo mẫu {@code <khối>A<số>}: khối 1 với 3 lớp ra 1A1, 1A2, 1A3.
     *
     * <p>Đây là lối vào duy nhất KHÔNG có bước gõ tay nào, nên cũng là lối duy nhất không thể
     * sai chính tả tên lớp.
     */
    private List<BulkClassDto.Dong> sinhTheoMau(BulkClassDto.XemTruocRequest req) {
        List<Integer> khoi = req.grades() == null ? List.of() : req.grades();
        int soLop = req.soLopMoiKhoi() == null ? 0 : req.soLopMoiKhoi();
        if (khoi.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một khối.");
        }
        if (soLop < 1 || soLop > SO_LOP_MOI_KHOI_TOI_DA) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Số lớp mỗi khối phải từ 1 đến " + SO_LOP_MOI_KHOI_TOI_DA + ".");
        }
        List<BulkClassDto.Dong> out = new ArrayList<>();
        int dong = 0;
        // LinkedHashSet: người dùng tick trùng khối thì cũng chỉ sinh một lần, và giữ thứ tự đã chọn.
        for (Integer k : new LinkedHashSet<>(khoi)) {
            for (int i = 1; i <= soLop; i++) {
                out.add(new BulkClassDto.Dong(
                        ++dong, k + "A" + i, String.valueOf(k), req.schoolYear(), BulkClassDto.TrangThai.HOP_LE, null));
            }
        }
        return out;
    }

    /**
     * Đọc văn bản dán từ Excel: mỗi dòng một lớp, cột cách nhau bằng Tab hoặc dấu phẩy, thứ tự
     * {@code Tên lớp, Khối, Năm học}.
     *
     * <p>Khối và Năm học đều CÓ THỂ BỎ TRỐNG: khối suy từ chữ số đầu tên lớp ("7A1" → 7), năm
     * học lấy theo ô đã chọn trên màn hình. Bắt điền đủ ba cột chỉ để máy suy được thứ nó tự
     * suy được là bắt người dùng làm việc hộ máy.
     */
    private List<BulkClassDto.Dong> docVanBan(String raw, String schoolYearMacDinh) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa có dữ liệu để đọc.");
        }
        List<BulkClassDto.Dong> out = new ArrayList<>();
        int dong = 0;
        for (String line : raw.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            dong++;
            String[] cot = line.split("\\t|,|;");
            String ten = o(cot, 0);
            // Bỏ dòng tiêu đề nếu người dùng copy cả header từ Excel.
            if (dong == 1 && ten != null && ten.toLowerCase(Locale.ROOT).startsWith("tên")) {
                dong--;
                continue;
            }
            out.add(new BulkClassDto.Dong(
                    dong,
                    ten,
                    o(cot, 1),
                    o(cot, 2) != null ? o(cot, 2) : schoolYearMacDinh,
                    BulkClassDto.TrangThai.HOP_LE,
                    null));
            if (out.size() > SO_DONG_TOI_DA) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Một lần chỉ nhập được tối đa " + SO_DONG_TOI_DA + " dòng.");
            }
        }
        return out;
    }

    /** Đọc file .xlsx / .xls bằng Apache POI, lấy sheet đầu tiên. */
    private List<BulkClassDto.Dong> docExcel(MultipartFile file, String schoolYearMacDinh) {
        List<BulkClassDto.Dong> out = new ArrayList<>();
        try (InputStream in = file.getInputStream();
                Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int dong = 0;
            for (Row r : sheet) {
                String ten = oCell(r, 0);
                if (ten == null || ten.isBlank()) {
                    continue;
                }
                dong++;
                if (dong == 1 && ten.toLowerCase(Locale.ROOT).startsWith("tên")) {
                    dong--;
                    continue;
                }
                String nam = oCell(r, 2);
                out.add(new BulkClassDto.Dong(
                        dong,
                        ten,
                        oCell(r, 1),
                        nam != null ? nam : schoolYearMacDinh,
                        BulkClassDto.TrangThai.HOP_LE,
                        null));
                if (out.size() > SO_DONG_TOI_DA) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST, "Một lần chỉ nhập được tối đa " + SO_DONG_TOI_DA + " dòng.");
                }
            }
        } catch (IOException | org.apache.poi.EncryptedDocumentException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Không đọc được file — hãy kiểm tra định dạng .xlsx hoặc .csv.");
        }
        return out;
    }

    private String docTextCsv(MultipartFile file) {
        try {
            // CSV do Excel xuất ra hay có BOM ở đầu; không cắt thì tên lớp dòng đầu mang thêm
            // một ký tự vô hình và bộ kiểm tên lớp từ chối nó với lý do khó hiểu.
            String s = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không đọc được file CSV.");
        }
    }

    private static String o(String[] arr, int i) {
        if (i >= arr.length) {
            return null;
        }
        String v = arr[i].trim();
        return v.isEmpty() ? null : v;
    }

    /** Giá trị ô Excel dưới dạng chuỗi — ô số phải cắt đuôi ".0" (POI đọc mọi số là double). */
    private static String oCell(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC) {
            double d = c.getNumericCellValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        String v = c.toString().trim();
        return v.isEmpty() ? null : v;
    }

    /* ─────────────────────────── KIỂM & GHI ─────────────────────────── */

    /**
     * Chấm từng dòng: hợp lệ / đã tồn tại / lỗi (kèm lý do).
     *
     * <p>Dùng LẠI đúng bộ kiểm của luồng thêm một lớp ({@link SchoolClassService#kiemTraMotDong})
     * chứ không viết bản thứ hai: hai bộ kiểm sẽ trôi ra khác nhau, và người dùng sẽ gặp cảnh
     * một cái tên lớp bị từ chối khi thêm lẻ nhưng lọt qua khi nhập hàng loạt.
     */
    private BulkClassDto.XemTruocResponse kiemTra(School school, List<BulkClassDto.Dong> tho) {
        // Tên lớp đã có ở trường này — nạp một lần, so không phân biệt hoa thường.
        Set<String> daCo = new java.util.HashSet<>();
        for (SchoolClass c : classRepo.findBySchoolIdAndDeletedFalseOrderByName(school.getId())) {
            daCo.add((c.getName() + "|" + c.getSchoolYear()).toUpperCase(Locale.ROOT));
        }
        // Trùng NGAY TRONG danh sách đang nhập cũng phải bắt: file Excel có hai dòng 5A1 thì
        // dòng thứ hai không được lọt, dù lúc kiểm nó chưa nằm trong DB.
        Set<String> trongLo = new java.util.HashSet<>();

        List<BulkClassDto.Dong> rows = new ArrayList<>();
        int hopLe = 0;
        int tonTai = 0;
        int loi = 0;
        for (BulkClassDto.Dong d : tho) {
            BulkClassDto.Dong ketQua;
            try {
                SchoolClassRequest req =
                        new SchoolClassRequest(school.getId(), d.name(), d.gradeLevel(), d.schoolYear(), "ACTIVE");
                var chuan = classService.kiemTraMotDong(req);
                String khoa = (chuan.name() + "|" + chuan.year()).toUpperCase(Locale.ROOT);
                if (daCo.contains(khoa)) {
                    ketQua = new BulkClassDto.Dong(
                            d.dong(),
                            chuan.name(),
                            chuan.gradeLevel(),
                            chuan.year(),
                            BulkClassDto.TrangThai.DA_TON_TAI,
                            "Lớp này đã có ở trường — bỏ qua");
                    tonTai++;
                } else if (!trongLo.add(khoa)) {
                    ketQua = new BulkClassDto.Dong(
                            d.dong(),
                            chuan.name(),
                            chuan.gradeLevel(),
                            chuan.year(),
                            BulkClassDto.TrangThai.DA_TON_TAI,
                            "Trùng với một dòng phía trên trong cùng lần nhập");
                    tonTai++;
                } else {
                    ketQua = new BulkClassDto.Dong(
                            d.dong(),
                            chuan.name(),
                            chuan.gradeLevel(),
                            chuan.year(),
                            BulkClassDto.TrangThai.HOP_LE,
                            null);
                    hopLe++;
                }
            } catch (ApiException e) {
                ketQua = BulkClassDto.Dong.loi(d.dong(), d.name(), d.gradeLevel(), d.schoolYear(), e.getMessage());
                loi++;
            }
            rows.add(ketQua);
        }
        return new BulkClassDto.XemTruocResponse(school.getId(), school.getName(), rows, hopLe, tonTai, loi);
    }

    /**
     * Ghi các dòng người dùng đã duyệt.
     *
     * <p>KIỂM LẠI TỪ ĐẦU chứ không tin danh sách client gửi lên: giữa lúc xem trước và lúc bấm
     * lưu, người khác có thể đã tạo đúng lớp đó, và không có gì ngăn ai đó gọi thẳng endpoint
     * này với một danh sách tự bịa.
     */
    @Transactional
    public BulkClassDto.TaoResponse tao(BulkClassDto.TaoRequest req) {
        School school = requireSchool(req.schoolId());
        List<BulkClassDto.Dong> rows = req.rows() == null ? List.of() : req.rows();
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không có dòng nào để tạo.");
        }
        if (rows.size() > SO_DONG_TOI_DA) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Một lần chỉ tạo được tối đa " + SO_DONG_TOI_DA + " lớp.");
        }
        BulkClassDto.XemTruocResponse soat = kiemTra(school, rows);

        int daTao = 0;
        for (BulkClassDto.Dong d : soat.rows()) {
            if (d.trangThai() != BulkClassDto.TrangThai.HOP_LE) {
                continue;
            }
            classService.create(
                    new SchoolClassRequest(school.getId(), d.name(), d.gradeLevel(), d.schoolYear(), "ACTIVE"));
            daTao++;
        }
        return new BulkClassDto.TaoResponse(daTao, soat.rows().size() - daTao);
    }

    /**
     * Trường phải tồn tại VÀ còn hợp tác.
     *
     * <p>Kiểm ngay từ bước XEM TRƯỚC chứ không đợi tới lúc ghi: để người dùng chọn khối, xem
     * danh sách 15 lớp "hợp lệ" rồi mới báo "trường này đã ngừng hợp tác" là bắt họ làm việc
     * thừa rồi mới nói không.
     */
    private School requireSchool(Integer id) {
        School s = schoolRepo
                .findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Không tìm thấy trường đã chọn."));
        if (!s.conHopTac(com.kdc.tsdms.common.BusinessTime.today())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Trường " + s.getName()
                            + " đã ngừng hợp tác hoặc hết hạn hợp đồng nên không mở thêm lớp mới được.");
        }
        return s;
    }
}
