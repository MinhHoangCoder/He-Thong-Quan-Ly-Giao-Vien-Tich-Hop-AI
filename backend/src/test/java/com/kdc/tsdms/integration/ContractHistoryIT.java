package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Contract;
import com.kdc.tsdms.entity.Teacher;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V37 — chỉ mục unique CÓ LỌC trên số hợp đồng.
 *
 * <p>Cả cách sửa "đóng bản cũ, mở bản mới" đứng hay đổ là ở đúng chỗ này: nếu ràng buộc UNIQUE
 * vẫn tính trên toàn bảng thì bản mới sẽ đụng ngay vào bản cũ vừa đóng, đúng trong trường hợp
 * phổ biến nhất — sửa lương mà giữ nguyên số hợp đồng. Và không có gì trong Java bắt được
 * chuyện đó: unit test mock repository thì lưu kiểu gì cũng "thành công".
 */
class ContractHistoryIT extends AbstractJpaSliceIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private Integer teacherId;

    @BeforeEach
    void dungGiaoVien() {
        Branch b = new Branch();
        b.setName("CN Hop Dong");
        em.persist(b);

        AppUser u = new AppUser();
        u.setUsername("gv.hopdong");
        u.setPasswordHash("bcrypt-gia-lap");
        u.setEmail("gv.hopdong@test.local");
        em.persist(u);

        Teacher t = new Teacher();
        t.setAppUserId(u.getId());
        t.setBranchId(b.getId());
        t.setLastName("Nguyễn Văn");
        t.setFirstName("Hợp");
        em.persist(t);
        em.flush();
        teacherId = t.getId();
    }

    private Contract hopDong(String so, String luong, boolean daThayThe) {
        Contract c = new Contract();
        c.setTeacherId(teacherId);
        c.setContractNo(so);
        c.setStartDate(LocalDate.of(2026, 1, 1));
        c.setBaseSalary(new BigDecimal(luong));
        c.setStatus(daThayThe ? "TERMINATED" : "ACTIVE");
        c.setDeleted(daThayThe);
        em.persist(c);
        return c;
    }

    @Test
    void banDaThayThe_vaBanMoi_DUOC_trungSoHopDong() {
        // Đây là lý do V37 tồn tại. Sửa lương mà giữ nguyên số HĐ là ca phổ biến nhất — với
        // ràng buộc UNIQUE toàn bảng của V1 thì thao tác này nổ.
        hopDong("HD-001", "8000000", true);
        em.flush();

        assertThatCode(() -> {
                    hopDong("HD-001", "9500000", false);
                    em.flush();
                })
                .doesNotThrowAnyException();

        Integer soBan = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Contract WHERE TeacherId = ? AND ContractNo = 'HD-001'",
                Integer.class,
                teacherId);
        assertThat(soBan).as("giữ được cả hai phiên bản").isEqualTo(2);
    }

    @Test
    void haiHopDongCONHIEULUC_trungSo_thiVANbiChan() {
        // Nới lỏng không được phép nới quá tay: hai hợp đồng đang sống vẫn không được trùng số.
        hopDong("HD-002", "8000000", false);
        em.flush();

        assertThatThrownBy(() -> {
                    hopDong("HD-002", "9000000", false);
                    em.flush();
                })
                .hasMessageContaining("UX_Contract_No_Active");
    }

    @Test
    void rangBuocUNIQUEtoanBang_daBiGoHan() {
        // Còn sót ràng buộc cũ thì test trên vẫn xanh mà luồng thật vẫn nổ — vì ràng buộc cũ
        // và chỉ mục mới cùng tồn tại, cái cũ chặn trước.
        Integer soRangBuocCu = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys.indexes i
                JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE i.object_id = OBJECT_ID('dbo.Contract')
                  AND c.name = 'ContractNo'
                  AND i.is_unique_constraint = 1
                """, Integer.class);
        assertThat(soRangBuocCu).isZero();
    }

    @Test
    void chiMucMoiPhaiCoDIEUKIENloc() {
        // Thiếu mệnh đề WHERE thì nó thành unique toàn bảng y như cũ, chỉ khác cái tên.
        String dieuKien = jdbc.queryForObject(
                "SELECT filter_definition FROM sys.indexes"
                        + " WHERE name = 'UX_Contract_No_Active' AND object_id = OBJECT_ID('dbo.Contract')",
                String.class);
        assertThat(dieuKien).isNotNull().contains("IsDeleted");
    }
}
