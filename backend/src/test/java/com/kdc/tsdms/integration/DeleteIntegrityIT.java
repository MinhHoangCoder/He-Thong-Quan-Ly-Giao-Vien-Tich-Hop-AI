package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.Attendance;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Certificate;
import com.kdc.tsdms.entity.Payroll;
import com.kdc.tsdms.entity.Schedule;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.repository.PayrollRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ĐỢT 2 — hai câu SQL THUẦN canh cửa xóa vĩnh viễn phải chạy đúng trên SQL Server thật.
 *
 * <p>Unit test bằng Mockito chỉ chứng minh service xử lý ĐÚNG cái mà repository trả về; nó không
 * hề đụng tới nội dung câu SQL. Mà cả hai câu ở đây đều là {@code nativeQuery} — sai tên bảng,
 * sai tên cột, hay dùng hàm SQL Server không có thì Java vẫn biên dịch ngon lành và chỉ nổ đúng
 * lúc người dùng bấm nút xóa. Với một chốt bảo vệ tiền lương thì đó là chỗ tệ nhất để phát hiện.
 */
class DeleteIntegrityIT extends AbstractJpaSliceIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TeacherRepository teacherRepo;

    @Autowired
    private PayrollRepository payrollRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private Teacher teacher;
    private Assignment assignment;

    /** Chuỗi cha tối thiểu thỏa FK: Branch → AppUser → Teacher → School/Subject → Assignment. */
    @BeforeEach
    void dungChuoiCha() {
        Branch branch = new Branch();
        branch.setName("CN Xóa");
        em.persist(branch);

        AppUser user = new AppUser();
        user.setUsername("gv.xoa");
        user.setPasswordHash("bcrypt-gia-lap");
        user.setEmail("gv.xoa@test.local");
        em.persist(user);

        teacher = new Teacher();
        teacher.setAppUserId(user.getId());
        teacher.setBranchId(branch.getId());
        teacher.setLastName("Trần Thị");
        teacher.setFirstName("Bình");
        em.persist(teacher);

        School school = new School();
        school.setBranchId(branch.getId());
        school.setName("THCS Xóa");
        em.persist(school);

        Subject subject = new Subject();
        subject.setCode("XOA01");
        subject.setName("Môn Xóa");
        em.persist(subject);

        assignment = new Assignment();
        assignment.setTeacherId(teacher.getId());
        assignment.setSchoolId(school.getId());
        assignment.setSubjectId(subject.getId());
        assignment.setStartDate(LocalDate.of(2026, 8, 1));
        assignment.setEndDate(LocalDate.of(2026, 8, 31));
        em.persist(assignment);
        em.flush();
    }

    /* ══════════ Câu 1: đếm dữ liệu con của giáo viên ══════════ */

    @Test
    void demDuLieuCon_chayThat_vaChiKeBangNaoConDong() {
        Certificate c = new Certificate();
        c.setTeacherId(teacher.getId());
        c.setName("Cử nhân Sư phạm");
        em.persist(c);
        em.flush();

        Map<String, Long> demDuoc = dem(teacher.getId());

        assertThat(demDuoc).containsEntry("certificate", 1L).containsEntry("assignment", 1L);
        // HAVING lọc sạch: bảng không có dòng nào thì không xuất hiện, không phải trả về 0.
        assertThat(demDuoc).doesNotContainKeys("contract", "payroll", "attendance");
    }

    @Test
    void demDuLieuCon_KHONG_locXoaMem() {
        // Đây là điểm dễ sai nhất và cũng là lý do câu SQL này không dùng ...AndDeletedFalse như
        // mọi query khác: sắp chạy DELETE thật, mà khóa ngoại chặn theo SỰ TỒN TẠI của dòng con.
        // Một chứng chỉ đã "xóa" vẫn làm câu DELETE Teacher nổ — bỏ sót nó là hứa cho xóa rồi 500.
        Certificate c = new Certificate();
        c.setTeacherId(teacher.getId());
        c.setName("Chứng chỉ đã gỡ khỏi hồ sơ");
        c.setDeleted(true);
        em.persist(c);
        em.flush();

        assertThat(dem(teacher.getId())).containsEntry("certificate", 1L);
    }

    @Test
    void moiKhoaNgoaiTroVaoTeacher_phaiCoMatTrongCauSql() {
        // Lưới tự bảo trì: thành viên thêm bảng mới trỏ vào Teacher mà quên khai vào câu UNION
        // thì rào chắn im lặng bỏ sót đúng bảng đó. Đối chiếu thẳng với sys.foreign_keys nên
        // không có cách nào lệch mà vẫn xanh.
        Set<String> trongSql = bangConKhaiTrongSql();
        Set<String> trongSchema = jdbc.queryForList("""
                        SELECT DISTINCT OBJECT_NAME(fk.parent_object_id)
                        FROM sys.foreign_keys fk
                        WHERE OBJECT_NAME(fk.referenced_object_id) = 'Teacher'
                        """, String.class).stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        assertThat(trongSql)
                .as("bảng con khai trong TeacherRepository.countChildRowsByTeacherId")
                .isEqualTo(trongSchema);
    }

    /* ══════════ Câu 2: kỳ lương đã chốt chặn xóa vĩnh viễn phân công ══════════ */

    @Test
    void kyLuongDaChot_batDungKy_boQuaKyConNhap() {
        chamCong(LocalDate.of(2026, 8, 10));
        chamCong(LocalDate.of(2026, 9, 10));
        bangLuong((short) 2026, (short) 8, "PAID");
        bangLuong((short) 2026, (short) 9, "DRAFT"); // nháp thì generate() dựng lại được → không khóa
        em.flush();

        assertThat(payrollRepo.findKyLuongDaChotTheoPhanCong(assignment.getId()))
                .containsExactly("8/2026");
    }

    @Test
    void khongCoKyLuongNaoDaChot_thiKhongChanGiCa() {
        chamCong(LocalDate.of(2026, 8, 10));
        bangLuong((short) 2026, (short) 8, "DRAFT");
        em.flush();

        assertThat(payrollRepo.findKyLuongDaChotTheoPhanCong(assignment.getId()))
                .isEmpty();
    }

    @Test
    void chamCongCuaPhanCongKHAC_khongKeoTheoKyLuongCuaPhanCongNay() {
        // Cùng một giáo viên, cùng tháng, nhưng buổi thuộc phân công khác: xóa phân công này
        // không đụng tới bằng chứng của phân công kia, nên không được lấy đó làm cớ chặn.
        Assignment khac = new Assignment();
        khac.setTeacherId(teacher.getId());
        khac.setSchoolId(assignment.getSchoolId());
        khac.setSubjectId(assignment.getSubjectId());
        khac.setStartDate(LocalDate.of(2026, 8, 1));
        em.persist(khac);
        em.flush();

        chamCong(LocalDate.of(2026, 8, 10)); // thuộc assignment gốc
        bangLuong((short) 2026, (short) 8, "PAID");
        em.flush();

        assertThat(payrollRepo.findKyLuongDaChotTheoPhanCong(khac.getId())).isEmpty();
        assertThat(payrollRepo.findKyLuongDaChotTheoPhanCong(assignment.getId()))
                .containsExactly("8/2026");
    }

    /* ══════════ helper ══════════ */

    private Map<String, Long> dem(Integer teacherId) {
        return teacherRepo.countChildRowsByTeacherId(teacherId).stream()
                .collect(Collectors.toMap(r -> String.valueOf(r[0]), r -> ((Number) r[1]).longValue()));
    }

    /** Đọc thẳng câu SQL trên annotation rồi rút tên bảng — không chép tay danh sách lần thứ hai. */
    private static Set<String> bangConKhaiTrongSql() {
        try {
            String sql = TeacherRepository.class
                    .getMethod("countChildRowsByTeacherId", Integer.class)
                    .getAnnotation(Query.class)
                    .value();
            Matcher m = Pattern.compile("FROM\\s+(\\w+)\\s+WHERE\\s+TeacherId", Pattern.CASE_INSENSITIVE)
                    .matcher(sql);
            Set<String> tables = new HashSet<>();
            while (m.find()) {
                tables.add(m.group(1).toLowerCase(Locale.ROOT));
            }
            assertThat(tables)
                    .as("regex phải rút được tên bảng, đừng để rỗng rồi pass suông")
                    .isNotEmpty();
            return tables;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Đổi tên countChildRowsByTeacherId thì sửa cả test này", e);
        }
    }

    private void chamCong(LocalDate ngay) {
        Schedule s = new Schedule();
        s.setAssignmentId(assignment.getId());
        s.setTeacherId(teacher.getId());
        s.setStartTime(LocalDateTime.of(ngay, LocalTime.of(7, 30)));
        s.setEndTime(LocalDateTime.of(ngay, LocalTime.of(8, 15)));
        em.persist(s);
        em.flush();

        Attendance a = new Attendance();
        a.setTeacherId(teacher.getId());
        a.setScheduleId(s.getId());
        a.setWorkDate(ngay);
        a.setStatus("PRESENT");
        em.persist(a);
    }

    private void bangLuong(short nam, short thang, String trangThai) {
        Payroll p = new Payroll();
        p.setTeacherId(teacher.getId());
        p.setPeriodYear(nam);
        p.setPeriodMonth(thang);
        p.setStatus(trangThai);
        em.persist(p);
    }
}
