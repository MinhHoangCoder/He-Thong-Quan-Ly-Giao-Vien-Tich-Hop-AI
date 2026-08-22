package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Payroll;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng Payroll — mỗi GV mỗi tháng đúng 1 dòng (UNIQUE Teacher+Year+Month). */
public interface PayrollRepository extends JpaRepository<Payroll, Integer> {

    /** Bảng lương của một kỳ (tháng/năm). */
    List<Payroll> findByPeriodYearAndPeriodMonthOrderByTeacherId(Short periodYear, Short periodMonth);

    /** Dòng lương của một GV trong một kỳ (upsert khi generate). */
    Optional<Payroll> findByTeacherIdAndPeriodYearAndPeriodMonth(
            Integer teacherId, Short periodYear, Short periodMonth);

    /**
     * Phiếu lương của một GV còn ở trạng thái CHƯA CHI (nháp hoặc đã chốt nhưng chưa trả tiền).
     *
     * <p>Chặn xóa hồ sơ giáo viên khi trung tâm còn nợ họ tiền: xóa xong thì phiếu vẫn nằm đó
     * nhưng hồ sơ đứng sau nó đã biến khỏi mọi danh sách, và người cầm tiền không còn đường nào
     * tra ra phải trả cho ai.
     */
    long countByTeacherIdAndStatusIn(Integer teacherId, java.util.Collection<String> statuses);

    /** Các phiếu lương của một GV trong một năm (mới nhất trước) — cho trang "Phiếu lương của tôi". */
    List<Payroll> findByTeacherIdAndPeriodYearOrderByPeriodMonthDesc(Integer teacherId, Short periodYear);

    /**
     * Các kỳ lương ĐÃ CHỐT/ĐÃ TRẢ mà chấm công của một phân công đang nằm trong đó, trả về dạng
     * {@code "8/2026"} để ghép thẳng vào thông báo lỗi.
     *
     * <p>Vì sao cần: {@code AssignmentService.purge} xóa CỨNG bảng {@code Attendance} — mà chấm
     * công chính là bằng chứng gốc để ra con số trên phiếu lương. Xóa phân công của tháng đã trả
     * lương là xóa mất phần đối chiếu của một khoản tiền đã đi khỏi tài khoản: bảng lương vẫn ghi
     * 40 tiết nhưng không còn dòng nào chứng minh 40 tiết đó có thật.
     *
     * <p>Phải viết bằng SQL thuần: các entity ở đây nối nhau bằng cột khóa trần (Long/Integer)
     * chứ không phải {@code @ManyToOne}, nên JPQL không join được. Nối kỳ lương với chấm công
     * theo đúng cách {@code PayrollService.generate} gom số: cùng giáo viên + cùng tháng/năm của
     * {@code WorkDate}.
     */
    @Query(value = """
                    SELECT CONCAT(p.PeriodMonth, '/', p.PeriodYear)
                    FROM Payroll p
                    JOIN Attendance a ON a.TeacherId = p.TeacherId
                                     AND YEAR(a.WorkDate) = p.PeriodYear
                                     AND MONTH(a.WorkDate) = p.PeriodMonth
                    JOIN Schedule s ON s.Id = a.ScheduleId
                    WHERE s.AssignmentId = :assignmentId
                      AND p.Status IN ('FINALIZED', 'PAID')
                    GROUP BY p.PeriodYear, p.PeriodMonth
                    ORDER BY p.PeriodYear, p.PeriodMonth
                    """, nativeQuery = true)
    List<String> findKyLuongDaChotTheoPhanCong(@Param("assignmentId") Integer assignmentId);
}
