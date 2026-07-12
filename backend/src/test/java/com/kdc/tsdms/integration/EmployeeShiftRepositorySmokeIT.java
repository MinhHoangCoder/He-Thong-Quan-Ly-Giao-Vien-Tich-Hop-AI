package com.kdc.tsdms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Branch;
import com.kdc.tsdms.entity.Employee;
import com.kdc.tsdms.entity.EmployeeSchedule;
import com.kdc.tsdms.entity.PartTimeShiftRequest;
import com.kdc.tsdms.repository.EmployeeScheduleRepository;
import com.kdc.tsdms.repository.PartTimeShiftRequestRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Smoke test cho 2 repository CA LÀM nhân viên (V10: PartTimeShiftRequest, EmployeeSchedule)
 * trên SQL Server thật — gồm cả CHECK constraint "SourceRequestId chỉ đi với FROM_REQUEST".
 */
class EmployeeShiftRepositorySmokeIT extends AbstractJpaSliceIT {

    /** Tuần làm việc cố định cho dữ liệu test: T2 2026-07-06 → CN 2026-07-12. */
    private static final LocalDate THU_HAI = LocalDate.of(2026, 7, 6);

    private static final LocalDate CHU_NHAT = THU_HAI.plusDays(6);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PartTimeShiftRequestRepository requestRepository;

    @Autowired
    private EmployeeScheduleRepository scheduleRepository;

    private Integer employeeId;

    @BeforeEach
    void dungNhanVienPartTime() {
        Branch branch = new Branch();
        branch.setName("CN Smoke");
        em.persist(branch);

        AppUser user = new AppUser();
        user.setUsername("nv.smoke");
        user.setPasswordHash("bcrypt-gia-lap");
        user.setEmail("nv.smoke@test.local");
        em.persist(user);

        Employee employee = new Employee();
        employee.setAppUserId(user.getId());
        employee.setBranchId(branch.getId());
        employee.setFirstName("B");
        employee.setLastName("Trần Thị");
        employee.setEmploymentType("PART_TIME");
        em.persist(employee);

        em.flush();
        employeeId = employee.getId();
    }

    private PartTimeShiftRequest taoDangKy(LocalDate ngay, String ca, String trangThai, boolean daXoa) {
        PartTimeShiftRequest dangKy = new PartTimeShiftRequest();
        dangKy.setEmployeeId(employeeId);
        dangKy.setWorkDate(ngay);
        dangKy.setShiftType(ca);
        dangKy.setStatus(trangThai);
        dangKy.setDeleted(daXoa);
        em.persist(dangKy);
        return dangKy;
    }

    private EmployeeSchedule taoLich(LocalDate ngay, String ca, String nguon, Integer tuDangKyId) {
        EmployeeSchedule lich = new EmployeeSchedule();
        lich.setEmployeeId(employeeId);
        lich.setWorkDate(ngay);
        lich.setShiftType(ca);
        lich.setStartTime("MORNING".equals(ca) ? LocalTime.of(8, 0) : LocalTime.of(14, 0));
        lich.setEndTime("MORNING".equals(ca) ? LocalTime.of(11, 0) : LocalTime.of(17, 0));
        lich.setSource(nguon);
        lich.setSourceRequestId(tuDangKyId);
        em.persist(lich);
        return lich;
    }

    @Test
    void requestRepository_locTheoKhoangNgay_vaTheoTrangThai() {
        taoDangKy(THU_HAI, "MORNING", "PENDING", false);
        taoDangKy(THU_HAI.plusDays(1), "AFTERNOON", "APPROVED", false);
        taoDangKy(THU_HAI.plusDays(7), "MORNING", "PENDING", false); // tuần SAU → ngoài khoảng
        taoDangKy(THU_HAI.plusDays(2), "MORNING", "PENDING", true); // đã xóa mềm
        em.flush();

        assertThat(requestRepository.findByEmployeeIdAndWorkDateBetweenAndDeletedFalse(employeeId, THU_HAI, CHU_NHAT))
                .hasSize(2);
        assertThat(requestRepository.findByStatusAndDeletedFalse("PENDING"))
                .allSatisfy(dangKy -> assertThat(dangKy.getStatus()).isEqualTo("PENDING"));
    }

    @Test
    void scheduleRepository_locTheoKhoangNgay_duCa2NguonFixedVaFromRequest() {
        // FROM_REQUEST bắt buộc kèm SourceRequestId (CHECK CK_EmpSchedule_SourceLink của V10)
        PartTimeShiftRequest dangKyDaDuyet = taoDangKy(THU_HAI.plusDays(1), "AFTERNOON", "APPROVED", false);
        em.flush();

        taoLich(THU_HAI, "MORNING", "FIXED", null);
        taoLich(THU_HAI.plusDays(1), "AFTERNOON", "FROM_REQUEST", dangKyDaDuyet.getId());
        taoLich(THU_HAI.plusDays(14), "MORNING", "FIXED", null); // ngoài khoảng
        em.flush();

        assertThat(scheduleRepository.findByEmployeeIdAndWorkDateBetweenAndDeletedFalse(employeeId, THU_HAI, CHU_NHAT))
                .extracting(EmployeeSchedule::getSource)
                .containsExactlyInAnyOrder("FIXED", "FROM_REQUEST");
    }
}
