package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Assignment;
import com.kdc.tsdms.entity.AssignmentSlot;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Period;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.PeriodRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Smoke test cho 2 repository THỜI KHÓA BIỂU dạy (V9: Period, AssignmentSlot) trên SQL Server
 * thật: derived query đúng cú pháp, lọc xóa mềm hoạt động, và FK/unique index của migration
 * chấp nhận chuỗi insert hợp lệ. Chạy trong transaction rollback nên không để lại dữ liệu.
 */
class TimetableRepositorySmokeIT extends AbstractJpaSliceIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PeriodRepository periodRepository;

    @Autowired
    private AssignmentSlotRepository assignmentSlotRepository;

    private Integer schoolId;
    private Integer teacherId;
    private Integer assignmentId;

    /** Dựng chuỗi cha tối thiểu thỏa FK: Branch → AppUser/Teacher/School/Subject → Assignment. */
    @BeforeEach
    void dungChuoiChaThoaFk() {
        Branch branch = new Branch();
        branch.setName("CN Smoke");
        em.persist(branch);

        AppUser user = new AppUser();
        user.setUsername("gv.smoke");
        user.setPasswordHash("bcrypt-gia-lap");
        user.setEmail("gv.smoke@test.local");
        em.persist(user);

        Teacher teacher = new Teacher();
        teacher.setAppUserId(user.getId());
        teacher.setBranchId(branch.getId());
        teacher.setFirstName("A");
        teacher.setLastName("Nguyễn Văn");
        em.persist(teacher);

        School school = new School();
        school.setBranchId(branch.getId());
        school.setName("THCS Smoke");
        em.persist(school);

        Subject subject = new Subject();
        subject.setCode("SMOKE01");
        subject.setName("STEM Smoke");
        em.persist(subject);

        Assignment assignment = new Assignment();
        assignment.setTeacherId(teacher.getId());
        assignment.setSchoolId(school.getId());
        assignment.setSubjectId(subject.getId());
        assignment.setStartDate(LocalDate.of(2026, 1, 5));
        assignment.setEndDate(LocalDate.of(2026, 5, 29));
        em.persist(assignment);

        em.flush();
        schoolId = school.getId();
        teacherId = teacher.getId();
        assignmentId = assignment.getId();
    }

    private Period taoTiet(int soTiet, String buoi, LocalTime batDau, boolean daXoa) {
        Period tiet = new Period();
        tiet.setSchoolId(schoolId);
        tiet.setPeriodNumber((short) soTiet);
        tiet.setSessionType(buoi);
        tiet.setStartTime(batDau);
        tiet.setEndTime(batDau.plusMinutes(45));
        tiet.setDeleted(daXoa);
        em.persist(tiet);
        return tiet;
    }

    @Test
    void periodRepository_locTheoTruong_sapTheoSoTiet_boQuaXoaMem() {
        // Cố ý insert LỘN thứ tự (2 trước 1) + 1 bản ghi đã xóa mềm
        taoTiet(2, "MORNING", LocalTime.of(8, 0), false);
        taoTiet(1, "MORNING", LocalTime.of(7, 0), false);
        taoTiet(3, "MORNING", LocalTime.of(9, 0), true);
        em.flush();

        assertThat(periodRepository.findBySchoolIdAndDeletedFalseOrderByPeriodNumber(schoolId))
                .extracting(Period::getPeriodNumber)
                .containsExactly((short) 1, (short) 2); // đúng thứ tự, không dính bản ghi đã xóa
    }

    @Test
    void periodRepository_traMotTietCuThe_khongThayTietDaXoa() {
        taoTiet(1, "MORNING", LocalTime.of(7, 0), false);
        taoTiet(2, "MORNING", LocalTime.of(8, 0), true);
        em.flush();

        Optional<Period> tiet1 = periodRepository.findBySchoolIdAndPeriodNumberAndDeletedFalse(schoolId, (short) 1);
        assertThat(tiet1).isPresent();
        assertThat(tiet1.get().getStartTime()).isEqualTo(LocalTime.of(7, 0));

        assertThat(periodRepository.findBySchoolIdAndPeriodNumberAndDeletedFalse(schoolId, (short) 2))
                .isEmpty();
    }

    @Test
    void assignmentSlotRepository_traTheoAssignment_vaDoTrungLichGv() {
        Period tiet1 = taoTiet(1, "MORNING", LocalTime.of(7, 0), false);
        em.flush();

        AssignmentSlot slot = new AssignmentSlot();
        slot.setAssignmentId(assignmentId);
        slot.setTeacherId(teacherId);
        slot.setDayOfWeek("MON");
        slot.setPeriodId(tiet1.getId());
        em.persist(slot);
        em.flush();

        assertThat(assignmentSlotRepository.findByAssignmentIdAndDeletedFalse(assignmentId))
                .hasSize(1);
        // Dò trùng lịch: cùng GV + thứ + tiết → thấy; khác thứ → không
        assertThat(assignmentSlotRepository.findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
                        teacherId, "MON", tiet1.getId()))
                .hasSize(1);
        assertThat(assignmentSlotRepository.findByTeacherIdAndDayOfWeekAndPeriodIdAndDeletedFalse(
                        teacherId, "TUE", tiet1.getId()))
                .isEmpty();
    }
}
