package com.kdc.tsdms.dto;

import java.time.Instant;
import java.util.List;

/**
 * KẾT QUẢ RÀ SOÁT DỮ LIỆU MỒ CÔI (Flyway V35).
 *
 * <p>"Mồ côi" ở đây có nghĩa hẹp và chính xác: dòng CON đang sống nhưng trỏ vào dòng CHA đã bị
 * xóa mềm. Con đã xóa trỏ vào cha đã xóa thì KHÔNG tính — đó là trạng thái nhất quán, không
 * phải rác.
 *
 * <p>Vì sao chúng vô hình: không câu query nghiệp vụ nào lọc theo cờ {@code IsDeleted} của bảng
 * CHA. Một lớp học trỏ vào trường đã xóa vẫn hiện đầy đủ ở mọi màn hình — chỉ là trỏ vào một
 * cái tên đã biến mất.
 *
 * <p>Màn hình CHỈ BÁO CÁO, không có nút dọn. Mỗi cặp mồ côi có hai cách xử lý trái ngược nhau
 * và chỉ con người mới chọn được: một trường bị xóa nhầm mà còn 12 lớp đang học thì việc đúng
 * là KHÔI PHỤC trường, không phải xóa nốt 12 lớp. Nút "dọn tự động" sẽ chọn phương án hủy diệt
 * trong cả hai trường hợp.
 */
public record OrphanScanResponse(
        /** Lần quét gần nhất — null nếu chưa quét lần nào. */
        Instant quetLuc,
        int tongSoDongMoCoi,
        /** So với lần quét TRƯỚC: dương = đang tăng, tức là còn đường sinh mồ côi lọt qua chốt. */
        Integer chenhLechSoVoiLanTruoc,
        List<Cap> cacCap) {

    /**
     * @param voHai cặp đã biết là mồ côi lành tính (phòng học / khung tiết của trường đã đóng)
     *     — vẫn kể ra để không ai tưởng hệ thống giấu, nhưng không tính vào con số cần xử lý
     */
    public record Cap(
            String bangCha,
            String bangCon,
            String cotKhoaNgoai,
            boolean conCoXoaMem,
            int soDong,
            boolean voHai,
            String giaiThich) {}
}
