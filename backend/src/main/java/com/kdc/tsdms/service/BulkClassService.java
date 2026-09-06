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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p>MỘT BẢNG NHẬP, MỘT ĐƯỜNG GHI. Người dùng gõ thẳng vào bảng nhiều dòng trên màn hình, hoặc
 * bấm nạp file Excel mẫu 3 cột để đổ sẵn vào chính bảng đó rồi sửa tiếp. File chỉ là cách điền
 * bảng nhanh hơn, không phải một luồng nghiệp vụ riêng, nên {@link #docFile} chỉ đọc chữ và
 * mọi luật kiểm dồn hết vào {@link #tao}.
 *
 * <p>ĐƯỢC ĂN CẢ, NGÃ VỀ KHÔNG. Chỉ cần một dòng trùng hoặc sai là cả lô dừng, không lớp nào
 * được tạo, và câu báo lỗi chỉ đích danh dòng nào hỏng vì cớ gì. Bản cũ ghi được dòng nào hay
 * dòng ấy rồi báo "đã tạo 37, bỏ qua 3" — người dùng phải tự dò xem 3 dòng nào bị bỏ, và nếu
 * nạp lại cả file thì 37 dòng kia lại báo trùng. Toàn bộ nằm trong một {@code @Transactional}
 * nên "không tạo dòng nào" là thật, không phải dọn tay.
 */
@Service
public class BulkClassService {

    /** Trần số dòng một lần nhập — chặn file rác và chặn cả cú nạp nhầm cả bảng tính. */
    private static final int SO_DONG_TOI_DA = 500;

    /** Số dòng lỗi kể ra trong một câu báo — kể hết 200 dòng thì không ai đọc nổi. */
    private static final int SO_DONG_KE_TOI_DA = 10;

    private final SchoolClassService classService;
    private final SchoolClassRepository classRepo;
    private final SchoolRepository schoolRepo;

    public BulkClassService(
            SchoolClassService classService, SchoolClassRepository classRepo, SchoolRepository schoolRepo) {
        this.classService = classService;
        this.classRepo = classRepo;
        this.schoolRepo = schoolRepo;
    }

    /* ─────────────────────────── ĐỌC FILE ─────────────────────────── */

    /**
     * Đọc file mẫu 3 cột {@code Tên lớp | Khối | Năm học} thành các dòng cho bảng nhập.
     *
     * <p>Nhận cả .xlsx/.xls (Apache POI) lẫn .csv: file nhà trường gửi sang có đủ hai kiểu, mà
     * bắt người dùng tự đổi định dạng trước khi nạp là một bước thừa hoàn toàn.
     *
     * <p>KHÔNG kiểm gì ở đây — kể cả tên lớp rỗng hay khối sai. Bảng nhập là chỗ sửa, nên đọc
     * được chữ gì thì đổ lên chữ đó để người dùng nhìn thấy và sửa tại chỗ; chặn ngay lúc đọc
     * thì họ phải quay về Excel sửa rồi nạp lại từ đầu.
     */
    public List<BulkClassDto.Dong> docFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa chọn file.");
        }
        String ten = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        return ten.endsWith(".csv") ? docCsv(file) : docExcel(file);
    }

    /** Đọc file .xlsx / .xls bằng Apache POI, lấy sheet đầu tiên. */
    private List<BulkClassDto.Dong> docExcel(MultipartFile file) {
        List<BulkClassDto.Dong> out = new ArrayList<>();
        try (InputStream in = file.getInputStream();
                Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row r : sheet) {
                themDong(out, oCell(r, 0), oCell(r, 1), oCell(r, 2));
            }
        } catch (IOException | org.apache.poi.EncryptedDocumentException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Không đọc được file — hãy kiểm tra định dạng .xlsx hoặc .csv.");
        }
        return out;
    }

    /** Đọc .csv: mỗi dòng một lớp, cột cách nhau bằng dấu phẩy, chấm phẩy hoặc Tab. */
    private List<BulkClassDto.Dong> docCsv(MultipartFile file) {
        String raw;
        try {
            // CSV do Excel xuất ra hay có BOM ở đầu; không cắt thì tên lớp dòng đầu mang thêm
            // một ký tự vô hình và bộ kiểm tên lớp từ chối nó với lý do khó hiểu.
            String s = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            raw = s.startsWith("﻿") ? s.substring(1) : s;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không đọc được file CSV.");
        }
        List<BulkClassDto.Dong> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] cot = line.split("\\t|,|;");
            themDong(out, o(cot, 0), o(cot, 1), o(cot, 2));
        }
        return out;
    }

    /**
     * Thêm một dòng đọc được vào danh sách — bỏ dòng trống và bỏ DÒNG TIÊU ĐỀ của file mẫu.
     *
     * <p>Nhận diện tiêu đề bằng chữ "tên" ở ô đầu (file mẫu ghi "Tên lớp") và chỉ xét ở dòng
     * đầu tiên: không lớp nào tên bắt đầu bằng chữ cái, nên không có nguy cơ nuốt nhầm dữ liệu
     * thật, còn xét mọi dòng thì một ngày nào đó sẽ nuốt nhầm.
     */
    private void themDong(List<BulkClassDto.Dong> out, String ten, String khoi, String namHoc) {
        if (ten == null || ten.isBlank()) {
            return;
        }
        if (out.isEmpty() && ten.toLowerCase(Locale.ROOT).startsWith("tên")) {
            return;
        }
        if (out.size() >= SO_DONG_TOI_DA) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Một lần chỉ nhập được tối đa " + SO_DONG_TOI_DA + " dòng.");
        }
        out.add(new BulkClassDto.Dong(out.size() + 1, ten.trim(), khoi, namHoc));
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

    /* ─────────────────────────── GHI CẢ LÔ ─────────────────────────── */

    /**
     * Ghi cả lô lớp — kiểm sạch trước, ghi sau.
     *
     * <p>BA VÒNG TÁCH BẠCH, KHÔNG GỘP: (1) chuẩn hóa và kiểm từng dòng, (2) soi trùng, (3) mới
     * ghi. Gộp lại thành một vòng "kiểm rồi ghi luôn" thì dòng 1-5 đã nằm trong DB lúc dòng 6
     * mới lộ ra là trùng; rollback gỡ được dữ liệu nhưng câu báo lỗi thì vẫn phải kể một danh
     * sách mà nửa sau chưa ai buồn kiểm. Kiểm trọn trước thì câu báo lỗi kể ĐỦ mọi dòng hỏng,
     * người dùng sửa một lượt là xong.
     *
     * <p>Bộ kiểm dùng lại đúng của luồng thêm một lớp ({@link SchoolClassService#kiemTraMotDong})
     * chứ không viết bản thứ hai: hai bộ kiểm sẽ trôi ra khác nhau, và người dùng gặp cảnh một
     * cái tên lớp bị từ chối khi thêm lẻ nhưng lọt qua khi nhập hàng loạt.
     */
    @Transactional
    public BulkClassDto.TaoResponse tao(BulkClassDto.TaoRequest req) {
        School school = requireSchool(req.schoolId());
        List<BulkClassDto.Dong> rows = req.rows() == null ? List.of() : req.rows();
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa có dòng nào để tạo lớp.");
        }
        if (rows.size() > SO_DONG_TOI_DA) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Một lần chỉ tạo được tối đa " + SO_DONG_TOI_DA + " lớp.");
        }

        // ── Vòng 1: chuẩn hóa + kiểm nghiệp vụ từng dòng, gom hết lỗi rồi mới báo ──
        List<String> loi = new ArrayList<>();
        Map<Integer, SchoolClassService.ValidatedClassFields> hopLe = new HashMap<>();
        for (BulkClassDto.Dong d : rows) {
            try {
                SchoolClassRequest mot = new SchoolClassRequest(
                        school.getId(), d.name(), khoiCuaDong(d), namHocCuaDong(d, req.schoolYear()), "ACTIVE");
                hopLe.put(d.dong(), classService.kiemTraMotDong(mot));
            } catch (ApiException e) {
                loi.add(moTaDong(d) + ": " + e.getMessage());
            }
        }
        if (!loi.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Chưa tạo lớp nào — sửa các dòng sau rồi bấm lại. " + gop(loi));
        }

        // ── Vòng 2: soi trùng theo khóa (Trường + Tên lớp + Năm học) ──
        List<String> trung = soiTrung(school, rows, hopLe);
        if (!trung.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Trùng lớp — chưa tạo dòng nào ở trường " + school.getName() + ". " + gop(trung));
        }

        // ── Vòng 3: ghi ──
        for (BulkClassDto.Dong d : rows) {
            SchoolClassService.ValidatedClassFields chuan = hopLe.get(d.dong());
            classService.create(
                    new SchoolClassRequest(school.getId(), chuan.name(), chuan.gradeLevel(), chuan.year(), "ACTIVE"));
        }
        return new BulkClassDto.TaoResponse(rows.size());
    }

    /**
     * Liệt kê ĐÍCH DANH những dòng trùng — trùng với lớp đã có ở trường, hoặc trùng lẫn nhau
     * ngay trong lô đang nhập.
     *
     * <p>Phải soi CẢ HAI phía: file Excel có hai dòng 7A1 thì dòng thứ hai chưa nằm trong DB
     * lúc kiểm, mà để nó lọt thì đúng lúc ghi mới nổ ở chỉ mục {@code
     * UX_SchoolClass_SchoolNameYear} (V40) với một câu lỗi SQL không ai đọc được.
     *
     * <p>Khóa gồm CẢ NĂM HỌC: "7A1" của 2025-2026 và "7A1" của 2026-2027 là hai lớp khác nhau,
     * chặn cả hai là chặn nhầm. Lớp đã xóa mềm không tính — đúng như điều kiện lọc {@code WHERE
     * IsDeleted = 0} của chỉ mục, nếu không thì xóa nhầm một lớp là vĩnh viễn không tạo lại
     * được tên đó.
     */
    private List<String> soiTrung(
            School school, List<BulkClassDto.Dong> rows, Map<Integer, SchoolClassService.ValidatedClassFields> hopLe) {
        Set<String> daCoTrongDb = new HashSet<>();
        for (SchoolClass c : classRepo.findBySchoolIdAndDeletedFalseOrderByName(school.getId())) {
            daCoTrongDb.add(khoaTrung(c.getName(), c.getSchoolYear()));
        }
        // Dòng đầu tiên giữ mỗi khóa — dòng sau mới là dòng "trùng với dòng trên".
        Map<String, Integer> dongDauTien = new HashMap<>();
        List<String> ketQua = new ArrayList<>();
        for (BulkClassDto.Dong d : rows) {
            SchoolClassService.ValidatedClassFields chuan = hopLe.get(d.dong());
            String khoa = khoaTrung(chuan.name(), chuan.year());
            Integer truoc = dongDauTien.putIfAbsent(khoa, d.dong());
            if (daCoTrongDb.contains(khoa)) {
                ketQua.add(
                        "dòng " + d.dong() + " (" + chuan.name() + " · năm học " + chuan.year() + ") đã có ở trường");
            } else if (truoc != null) {
                ketQua.add("dòng " + d.dong() + " (" + chuan.name() + " · năm học " + chuan.year() + ") trùng với dòng "
                        + truoc + " trong cùng lần nhập");
            }
        }
        return ketQua;
    }

    private static String khoaTrung(String name, String schoolYear) {
        return (name + "|" + schoolYear).toUpperCase(Locale.ROOT);
    }

    /**
     * Khối của dòng: lấy cột Khối nếu có, không thì suy từ chữ số đầu tên lớp ("7A1" → "7").
     *
     * <p>Cột Khối trong file mẫu hay bị bỏ trống vì nó vốn nằm sẵn trong tên lớp. Bắt điền một
     * thứ máy tự suy được là bắt người dùng làm việc hộ máy — mà bộ kiểm vẫn đối chiếu số khối
     * với chữ số đầu tên lớp, nên suy như vậy không nới lỏng luật nào cả.
     */
    private static String khoiCuaDong(BulkClassDto.Dong d) {
        if (d.gradeLevel() != null && !d.gradeLevel().isBlank()) {
            return d.gradeLevel();
        }
        String ten = d.name() == null ? "" : d.name().trim();
        return ten.isEmpty() ? null : ten.substring(0, 1);
    }

    /** Năm học của dòng, lùi về năm học chung của cả lô khi dòng bỏ trống. */
    private static String namHocCuaDong(BulkClassDto.Dong d, String namChung) {
        return d.schoolYear() != null && !d.schoolYear().isBlank() ? d.schoolYear() : namChung;
    }

    private static String moTaDong(BulkClassDto.Dong d) {
        String ten = d.name() == null || d.name().isBlank() ? "chưa có tên lớp" : d.name();
        return "dòng " + d.dong() + " (" + ten + ")";
    }

    /** Gộp danh sách lý do thành một câu, cắt bớt khi quá dài nhưng vẫn nói còn bao nhiêu. */
    private static String gop(List<String> lyDo) {
        List<String> ke = lyDo.size() <= SO_DONG_KE_TOI_DA ? lyDo : lyDo.subList(0, SO_DONG_KE_TOI_DA);
        String s = String.join("; ", ke);
        return lyDo.size() > ke.size() ? s + "; … và " + (lyDo.size() - ke.size()) + " dòng nữa." : s + ".";
    }

    /**
     * Trường phải tồn tại VÀ còn hợp tác.
     *
     * <p>Kiểm ngay từ đầu chứ không đợi tới lúc ghi từng dòng: để người dùng gõ xong 15 dòng
     * rồi mới báo "trường này đã ngừng hợp tác" là bắt họ làm việc thừa rồi mới nói không.
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
