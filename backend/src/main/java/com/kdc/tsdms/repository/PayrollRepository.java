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

    /* ═══════════════ KIỂM TRA SỨC KHỎE TRƯỚC KHI CHỐT LƯƠNG ═══════════════
    Sáu câu đếm dưới đây trả lời sáu câu hỏi khác nhau nhưng cùng một dạng:
    "còn bao nhiêu dòng dữ liệu sẽ làm số tiền sai mà không ai biết".

    Viết SQL thuần vì các bảng ở đây nối nhau bằng cột khóa trần
    (Integer/Long) chứ không phải @ManyToOne — JPQL không join được. */

    /**
     * Buổi dạy ĐÃ QUA nhưng KHÔNG có bản ghi chấm công nào.
     *
     * <p>Giáo viên đã tới trường dạy, nhưng vì không có dòng chấm công nên tiết đó không vào
     * bảng lương. Chốt kỳ là chốt luôn phần tiền bị thiếu ấy.
     */
    @Query(value = """
                    SELECT COUNT(*)
                    FROM Schedule s
                    WHERE s.IsDeleted = 0
                      AND s.Status = 'APPROVED'
                      AND s.StartTime < SYSDATETIME()
                      AND YEAR(s.StartTime) = :nam AND MONTH(s.StartTime) = :thang
                      AND NOT EXISTS (SELECT 1 FROM Attendance a WHERE a.ScheduleId = s.Id)
                    """, nativeQuery = true)
    int demBuoiChuaChamCong(@Param("nam") short nam, @Param("thang") short thang);

    /**
     * Bản ghi chấm công đã điểm danh VÀO mà chưa điểm danh RA.
     *
     * <p>Buổi chưa khép thì số giờ công của nó chưa chốt. Khóa kỳ lúc này là khóa một dòng
     * còn dở dang, và giáo viên hết đường bấm điểm danh ra.
     */
    @Query(value = """
                    SELECT COUNT(*)
                    FROM Attendance a
                    WHERE YEAR(a.WorkDate) = :nam AND MONTH(a.WorkDate) = :thang
                      AND a.CheckIn IS NOT NULL AND a.CheckOut IS NULL
                    """, nativeQuery = true)
    int demBuoiDangDo(@Param("nam") short nam, @Param("thang") short thang);

    /**
     * Tiết CÓ CÔNG nhưng KHÔNG tra được đơn giá.
     *
     * <p>Đây là vấn đề im lặng nhất trong cả module lương: {@code PayrollService.resolveRate}
     * trả {@code null}, {@code generate} ghi một dòng cảnh báo vào log rồi <b>bỏ qua tiết
     * đó</b>. Phiếu lương vẫn sinh ra bình thường, chỉ là thiếu tiền.
     *
     * <p>Hai nguyên nhân: lớp không suy được khối (dữ liệu cũ, khối để trống), hoặc bảng
     * {@code PayRate} có LỖ THỦNG ở ngày dạy đó.
     */
    @Query(value = """
                    SELECT COUNT(*)
                    FROM Attendance a
                    JOIN Schedule s ON s.Id = a.ScheduleId
                    LEFT JOIN Assignment asg ON asg.Id = s.AssignmentId
                    LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
                    LEFT JOIN SchoolClass c ON c.Id = COALESCE(sl.ClassId, asg.ClassId)
                    WHERE YEAR(a.WorkDate) = :nam AND MONTH(a.WorkDate) = :thang
                      AND a.Status IN ('PRESENT', 'LATE')
                      AND NOT EXISTS (
                            SELECT 1 FROM PayRate pr
                             WHERE TRY_CAST(c.GradeLevel AS INT) BETWEEN pr.GradeFrom AND pr.GradeTo
                               AND a.WorkDate >= pr.EffectiveFrom
                               AND (pr.EffectiveTo IS NULL OR a.WorkDate <= pr.EffectiveTo))
                    """, nativeQuery = true)
    int demTietKhongTraDuocDonGia(@Param("nam") short nam, @Param("thang") short thang);

    /**
     * Giáo viên CÓ tiết dạy có công trong kỳ nhưng CHƯA có phiếu lương.
     *
     * <p>Nghĩa là chưa bấm "Tính lương", hoặc bấm rồi nhưng chấm công phát sinh thêm sau đó.
     */
    @Query(value = """
                    SELECT COUNT(DISTINCT a.TeacherId)
                    FROM Attendance a
                    WHERE YEAR(a.WorkDate) = :nam AND MONTH(a.WorkDate) = :thang
                      AND a.Status IN ('PRESENT', 'LATE')
                      AND NOT EXISTS (
                            SELECT 1 FROM Payroll p
                             WHERE p.TeacherId = a.TeacherId
                               AND p.PeriodYear = :nam AND p.PeriodMonth = :thang)
                    """, nativeQuery = true)
    int demGiaoVienChuaCoPhieu(@Param("nam") short nam, @Param("thang") short thang);

    /**
     * Phiếu lương NHÁP có số tiết lệch so với chấm công hiện tại.
     *
     * <p>Đã bấm "Tính lương" rồi nhưng sau đó chấm công thay đổi (bổ sung công, sửa vắng
     * thành có mặt). Con số trên phiếu là ảnh chụp của quá khứ — chốt luôn là chốt số cũ.
     * Chỉ soi phiếu NHÁP: phiếu đã chốt/đã trả cố ý giữ nguyên số của lúc chốt.
     */
    @Query(value = """
                    SELECT COUNT(*)
                    FROM Payroll p
                    WHERE p.PeriodYear = :nam AND p.PeriodMonth = :thang
                      AND p.Status = 'DRAFT'
                      AND p.TaughtHours <> (
                            SELECT COUNT(*) FROM Attendance a
                             WHERE a.TeacherId = p.TeacherId
                               AND a.Status IN ('PRESENT', 'LATE')
                               AND YEAR(a.WorkDate) = :nam AND MONTH(a.WorkDate) = :thang)
                    """, nativeQuery = true)
    int demPhieuLechSoTiet(@Param("nam") short nam, @Param("thang") short thang);

    /**
     * Bản ghi chấm công MỒ CÔI — buổi dạy sinh ra nó đã bị xóa mềm.
     *
     * <p>Dòng vẫn được đếm vào lương nhưng không còn buổi dạy nào chứng minh nó có thật.
     */
    @Query(value = """
                    SELECT COUNT(*)
                    FROM Attendance a
                    JOIN Schedule s ON s.Id = a.ScheduleId
                    WHERE YEAR(a.WorkDate) = :nam AND MONTH(a.WorkDate) = :thang
                      AND s.IsDeleted = 1
                    """, nativeQuery = true)
    int demChamCongMoCoi(@Param("nam") short nam, @Param("thang") short thang);
}
