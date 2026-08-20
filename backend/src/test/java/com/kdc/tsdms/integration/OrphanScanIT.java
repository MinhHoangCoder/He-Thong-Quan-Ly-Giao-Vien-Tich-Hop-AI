package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.SchoolClass;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ĐỢT 4 — thủ tục rà soát dữ liệu mồ côi (V35) phải chạy đúng trên SQL Server thật.
 *
 * <p>Thủ tục này viết bằng SQL ĐỘNG dựng câu đếm từ {@code sys.foreign_keys} lúc chạy, nên
 * không có bước biên dịch nào bắt lỗi giúp: gõ sai tên cột hệ thống, quên {@code QUOTENAME},
 * hay sai kiểu tham số OUTPUT thì tất cả đều im lặng cho tới lúc ai đó gọi thật. Tệ hơn, một
 * lỗi ở mệnh đề lọc sẽ không làm nó nổ mà chỉ làm nó trả về 0 — tức là báo "sạch" trong khi
 * dữ liệu đang mục. Bộ test này trồng sẵn mồ côi rồi bắt thủ tục phải tìm ra.
 */
class OrphanScanIT extends AbstractJpaSliceIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    /** Trường đã xóa mềm + lớp học tùy chọn còn sống/đã xóa. Trả về id của trường. */
    private Integer trongMoCoi(boolean lopConSong) {
        Branch b = new Branch();
        b.setName("CN Mo Coi");
        em.persist(b);

        School s = new School();
        s.setBranchId(b.getId());
        s.setName("THCS Mo Coi");
        s.setDeleted(true); // CHA đã bị xóa mềm
        em.persist(s);

        SchoolClass c = new SchoolClass();
        c.setSchoolId(s.getId());
        c.setName("6A");
        c.setSchoolYear("2026-2027");
        c.setDeleted(!lopConSong);
        em.persist(c);

        em.flush();
        return s.getId();
    }

    private List<Map<String, Object>> quet() {
        return jdbc.queryForList("EXEC usp_ScanOrphanRows @GhiNhatKy = 0");
    }

    private long soMoCoi(List<Map<String, Object>> ketQua, String cha, String con) {
        return ketQua.stream()
                .filter(r -> cha.equals(r.get("ParentTable")) && con.equals(r.get("ChildTable")))
                .mapToLong(r -> ((Number) r.get("OrphanCount")).longValue())
                .sum();
    }

    @Test
    void conDangSongTroVaoChaDaXoa_thiBiPhatHien() {
        trongMoCoi(true);

        assertThat(soMoCoi(quet(), "School", "SchoolClass"))
                .as("lớp còn sống trỏ vào trường đã xóa mềm")
                .isEqualTo(1);
    }

    @Test
    void conCungDaXoa_thiKHONGtinhLaMoCoi() {
        // Cha xóa, con cũng xóa = trạng thái nhất quán, không phải rác. Đếm cả dòng này thì
        // mọi lần xóa đúng luật cũng bị báo động, và báo động lúc nào cũng kêu là hết tác dụng.
        trongMoCoi(false);

        assertThat(soMoCoi(quet(), "School", "SchoolClass")).isZero();
    }

    @Test
    void quetPhaiPhuNhieuCapChaCon_chuKhongChiMotBang() {
        // Chốt tính chất "quét ĐỘNG": thủ tục tự dựng câu đếm cho MỌI khóa ngoại có cha xóa
        // mềm. Nếu ai đó thay bằng danh sách chép tay thì con số này tụt xuống và test đỏ.
        Integer soCap = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys.foreign_keys fk
                WHERE EXISTS (SELECT 1 FROM sys.columns p
                              WHERE p.object_id = fk.referenced_object_id AND p.name = 'IsDeleted')
                  AND fk.parent_object_id <> fk.referenced_object_id
                """, Integer.class);

        assertThat(soCap).as("số cặp cha-con mà thủ tục phải quét").isGreaterThanOrEqualTo(20);
    }

    @Test
    void ghiNhatKy_luuLaiAnhChupDeSoSanhVeSau() {
        trongMoCoi(true);
        Integer truoc = jdbc.queryForObject("SELECT COUNT(*) FROM OrphanScan", Integer.class);

        jdbc.execute("EXEC usp_ScanOrphanRows @GhiNhatKy = 1");

        Integer sau = jdbc.queryForObject("SELECT COUNT(*) FROM OrphanScan", Integer.class);
        assertThat(sau).as("bật ghi nhật ký thì phải có thêm dòng").isGreaterThan(truoc);

        Integer lopMoCoi = jdbc.queryForObject(
                "SELECT TOP 1 OrphanCount FROM OrphanScan WHERE ParentTable = 'School'"
                        + " AND ChildTable = 'SchoolClass' ORDER BY Id DESC",
                Integer.class);
        assertThat(lopMoCoi).isEqualTo(1);
    }

    @Test
    void khongBatGhiNhatKy_thiKhongDeLaiDauVet() {
        trongMoCoi(true);
        Integer truoc = jdbc.queryForObject("SELECT COUNT(*) FROM OrphanScan", Integer.class);

        quet();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM OrphanScan", Integer.class))
                .as("chế độ chỉ-xem không được ghi gì")
                .isEqualTo(truoc);
    }
}
