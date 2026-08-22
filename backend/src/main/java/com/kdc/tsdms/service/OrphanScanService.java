package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.OrphanScanResponse;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RÀ SOÁT DỮ LIỆU MỒ CÔI — đọc kết quả của {@code usp_ScanOrphanRows} (Flyway V35).
 *
 * <p>V35 dựng sẵn thủ tục quét và bảng nhật ký {@code OrphanScan} nhưng KHÔNG có đường nào từ
 * ứng dụng tới chúng: phải mở SSMS gõ tay mới xem được. Service này nối đoạn còn thiếu.
 *
 * <p>Vì sao gọi thủ tục thay vì viết lại phép quét bằng Java: thủ tục quét ĐỘNG qua
 * {@code sys.foreign_keys}, nên nó luôn khớp với schema hiện tại. Một danh sách bảng chép tay
 * sẽ lệch khỏi schema đúng vào lúc không ai để ý, và lệch theo hướng nguy hiểm nhất — bỏ SÓT
 * bảng mới mà vẫn báo "sạch".
 *
 * <p>KHÔNG có hàm dọn. Mỗi cặp mồ côi có hai cách xử lý trái ngược nhau và chỉ con người mới
 * chọn được: một trường bị xóa nhầm mà còn 12 lớp đang học thì việc đúng là KHÔI PHỤC trường,
 * không phải xóa nốt 12 lớp.
 */
@Service
public class OrphanScanService {

    /**
     * Các cặp mồ côi LÀNH TÍNH — hệ quả cố ý của luật xóa, không phải lỗi.
     *
     * <p>Phòng học và khung tiết là cấu hình nội bộ của chính trường đó. {@code
     * SchoolService.delete} cố ý KHÔNG chặn theo chúng: chặn thì mọi trường seed sẵn không bao
     * giờ xóa được. Nên trường đóng cửa luôn để lại phòng và khung tiết "mồ côi" — và chúng
     * chẳng gây hại gì vì không màn hình nào đọc phòng/tiết của một trường đã xóa.
     *
     * <p>Vẫn liệt kê ra màn hình, chỉ là không tính vào con số cần xử lý: giấu đi thì lần sau
     * có người chạy thủ tục trong SSMS sẽ thấy số khác với màn hình và tưởng hệ thống nói dối.
     */
    private static final Set<String> CAP_VO_HAI = Set.of("School>Room", "School>Period");

    private static final Map<String, String> GIAI_THICH = Map.of(
            "School>Room", "Phòng học là cấu hình nội bộ của trường — trường đóng cửa thì phòng ở lại, không hại gì.",
            "School>Period", "Khung tiết cũng vậy: nó thuộc về trường, không ai đọc khung tiết của một trường đã xóa.",
            "AppUser>UserRole",
                    "Tài khoản đã xóa nhưng dòng phân vai còn lại. Vô hại về hiển thị, nhưng nên dọn để bảng "
                            + "phân quyền không kể tên người không còn tồn tại.",
            "AppUser>RefreshToken",
                    "Phiên đăng nhập của tài khoản đã xóa. Job dọn token định kỳ sẽ tự xử lý sau 30 ngày.");

    private final EntityManager em;

    public OrphanScanService(EntityManager em) {
        this.em = em;
    }

    /**
     * Quét lại NGAY và ghi một ảnh chụp vào nhật ký.
     *
     * <p>Ghi nhật ký chứ không chỉ trả về: có lịch sử thì mới trả lời được câu quan trọng nhất
     * — "số mồ côi có ĐANG TĂNG không?". Nếu tăng thì tức là còn một đường sinh mồ côi nào đó
     * lọt qua các chốt chặn.
     */
    @Transactional
    public OrphanScanResponse quet() {
        int truoc = tongLanQuetTruoc();

        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery("EXEC usp_ScanOrphanRows @GhiNhatKy = 1").getResultList();

        List<OrphanScanResponse.Cap> caps = new ArrayList<>();
        int tong = 0;
        for (Object[] r : rows) {
            String cha = (String) r[0];
            String con = (String) r[1];
            String cot = (String) r[2];
            // Cột BIT: driver JDBC của SQL Server trả về Boolean, không phải Number. Nhận cả hai
            // để không phụ thuộc vào chi tiết của driver.
            boolean conXoaMem = r[3] instanceof Boolean b ? b : ((Number) r[3]).intValue() == 1;
            int soDong = ((Number) r[4]).intValue();
            String khoa = cha + ">" + con;
            boolean voHai = CAP_VO_HAI.contains(khoa);
            if (!voHai) {
                tong += soDong;
            }
            caps.add(new OrphanScanResponse.Cap(
                    cha, con, cot, conXoaMem, soDong, voHai, GIAI_THICH.getOrDefault(khoa, null)));
        }

        Integer chenh = truoc < 0 ? null : tong - truoc;
        return new OrphanScanResponse(Instant.now(), tong, chenh, caps);
    }

    /**
     * Tổng số dòng mồ côi (bỏ cặp vô hại) của lần quét TRƯỚC lần vừa ghi.
     *
     * @return -1 nếu chưa từng quét lần nào — khi đó không có gì để so sánh
     */
    private int tongLanQuetTruoc() {
        Object moc = em.createNativeQuery("SELECT MAX(ScanAt) FROM OrphanScan").getSingleResult();
        if (moc == null) {
            return -1;
        }
        Instant luc = moc instanceof Timestamp ts ? ts.toInstant() : null;
        if (luc == null) {
            return -1;
        }
        Object tong = em.createNativeQuery(
                        "SELECT ISNULL(SUM(OrphanCount), 0) FROM OrphanScan "
                                + "WHERE ScanAt = :luc AND ParentTable + '>' + ChildTable NOT IN ('School>Room', 'School>Period')")
                .setParameter("luc", Timestamp.from(luc))
                .getSingleResult();
        return ((Number) tong).intValue();
    }
}
