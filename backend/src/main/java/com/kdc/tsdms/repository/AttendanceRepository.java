package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Attendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng Attendance (khóa BIGINT → Long). */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** Bảng chấm công theo khoảng ngày (mới nhất trước). */
    List<Attendance> findByWorkDateBetweenOrderByWorkDateDescIdDesc(LocalDate from, LocalDate to);

    /** Lọc thêm theo giáo viên. */
    List<Attendance> findByTeacherIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
            Integer teacherId, LocalDate from, LocalDate to);

    /** Tránh sinh trùng khi generate chấm công từ lịch dạy. */
    boolean existsByScheduleId(Long scheduleId);

    /** Dòng chấm công của một buổi dạy (lấy dòng đầu — bảng không có unique constraint). */
    java.util.Optional<Attendance> findFirstByScheduleIdOrderByIdAsc(Long scheduleId);

    /**
     * Các dòng VẮNG do JOB NỀN tự ghi trong khoảng ngày ({@code checkInMethod = 'SYSTEM'}).
     *
     * <p>Lọc theo nguồn chứ không lấy mọi dòng Vắng là CỐ Ý: dòng kế toán ghi tay là một phán
     * quyết có người chịu trách nhiệm (giáo viên vẫn phải dạy bù hôm đó mà bỏ), còn dòng
     * SYSTEM chỉ là hệ quả máy móc của việc "hết buổi không ai check-in". Chỉ dòng thứ hai mới
     * có thể là nạn nhân của buổi "ma" ngày lễ.
     */
    @Query("""
            SELECT a FROM Attendance a
            WHERE a.status = 'ABSENT'
              AND a.checkInMethod = 'SYSTEM'
              AND a.workDate BETWEEN :from AND :to
            ORDER BY a.workDate ASC, a.teacherId ASC
            """)
    List<Attendance> findSystemAbsencesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Số dòng đã chuyển sang NGHỈ PHÉP trong khoảng — hậu quả đã ghi của một kỳ nghỉ. */
    long countByStatusAndWorkDateBetween(String status, LocalDate from, LocalDate to);

    /**
     * MỌI dòng chấm công gắn với các buổi của một phân công, không lọc trạng thái gì cả.
     *
     * <p>Dùng làm rào chắn cho {@code AssignmentService.update} — luồng đó XÓA CỨNG toàn bộ
     * Schedule của phiếu rồi sinh lại. Hôm nay việc đó an toàn vì chỉ phiếu chưa xác nhận mới
     * sửa được, mà buổi của phiếu chưa xác nhận thì chưa APPROVED nên cả ba đường sinh chấm
     * công (tự check-in, job quét, kỳ nghỉ) đều không chạm tới. Nhưng hàng rào đó là TRẠNG
     * THÁI PHIẾU chứ không phải DỮ LIỆU THẬT: thêm một trạng thái vào danh sách sửa được, hoặc
     * thêm một đường ghi Attendance mới, là câu DELETE đâm thẳng vào khóa ngoại
     * Attendance.ScheduleId và bung ra lỗi 500 SQL thô thay vì một câu tiếng Việt.
     */
    @Query(
            value = "SELECT COUNT(*) FROM Attendance a JOIN Schedule s ON s.Id = a.ScheduleId"
                    + " WHERE s.AssignmentId = :assignmentId",
            nativeQuery = true)
    long demChamCongTheoPhanCong(@Param("assignmentId") Integer assignmentId);

    /** Như trên nhưng theo GIÁO VIÊN — chặn xóa hồ sơ của người đang đứng lớp. */
    @Query("""
            SELECT COUNT(a) FROM Attendance a
            WHERE a.teacherId = :teacherId
              AND a.checkIn IS NOT NULL
              AND a.checkOut IS NULL
            """)
    long countDangDayDoTheoGiaoVien(@Param("teacherId") Integer teacherId);

    /**
     * Bảng chấm công có PHÂN TRANG cho màn quản lý.
     *
     * <p>Vì sao phân trang ở DB chứ không cắt trang ở trình duyệt như trước: một tháng của
     * 101 giáo viên là hơn nghìn dòng, tải hết về rồi mới hiện 10 dòng đầu là chở cả tấn hàng
     * để lấy một hộp.
     *
     * <p>Từ khóa tìm theo TÊN GIÁO VIÊN, join thẳng sang bảng Teacher. Cố ý không tìm theo tên
     * trường/lớp: hai thứ đó phải đi vòng Attendance → Schedule → AssignmentSlot →
     * SchoolClass, một câu join bốn tầng chỉ để phục vụ ô tìm — trong khi trường và lớp đã có
     * dropdown lọc riêng.
     *
     * <p>Keyword do service escape sẵn ({@code SearchText.escapeLike}) nên câu này khai
     * {@code ESCAPE '!'}.
     */
    @Query(value = """
            SELECT a FROM Attendance a
            WHERE a.workDate BETWEEN :from AND :to
              AND (:teacherId IS NULL OR a.teacherId = :teacherId)
              AND (:status IS NULL OR a.status = :status)
              AND (:keyword IS NULL OR EXISTS (
                    SELECT 1 FROM Teacher t
                    WHERE t.id = a.teacherId
                      AND LOWER(CONCAT(t.lastName, ' ', t.firstName)) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
              ))
            ORDER BY a.workDate DESC, a.id DESC
            """, countQuery = """
            SELECT COUNT(a) FROM Attendance a
            WHERE a.workDate BETWEEN :from AND :to
              AND (:teacherId IS NULL OR a.teacherId = :teacherId)
              AND (:status IS NULL OR a.status = :status)
              AND (:keyword IS NULL OR EXISTS (
                    SELECT 1 FROM Teacher t
                    WHERE t.id = a.teacherId
                      AND LOWER(CONCAT(t.lastName, ' ', t.firstName)) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
              ))
            """)
    Page<Attendance> search(
            @Param("teacherId") Integer teacherId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Ba con số tổng của TOÀN BỘ kết quả lọc, không chỉ trang đang xem.
     *
     * <p>Cần một câu riêng vì {@link #search} nay trả về từng trang: cộng dồn ở trình duyệt
     * chỉ ra tổng của 10 dòng đang hiện, mà thẻ "Tổng giờ dạy" phải là tổng của cả kỳ.
     *
     * <p>Phải viết SQL THUẦN chứ không JPQL: số giờ không có trong bảng, nó được tính từ
     * CheckIn/CheckOut ({@code AttendanceResponse.computeHours}), mà JPQL không có hàm trừ
     * hai mốc giờ. Công thức dưới đây khớp đúng hàm đó — chia 60.0 để ra số thập phân, và
     * bỏ qua dòng thiếu giờ hoặc giờ ra không sau giờ vào.
     *
     * <p>Điều kiện WHERE chép đúng {@link #search}: hai câu nói khác nhau thì bảng và thẻ
     * tổng sẽ mâu thuẫn ngay trên cùng một màn hình.
     *
     * @return một dòng {tổng dòng, số buổi có công, tổng giờ}
     */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) AS TongDong,
                   SUM(CASE WHEN a.Status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END) AS CoCong,
                   COALESCE(SUM(CASE WHEN a.CheckIn IS NOT NULL AND a.CheckOut > a.CheckIn
                                     THEN DATEDIFF(MINUTE, a.CheckIn, a.CheckOut) ELSE 0 END), 0) / 60.0 AS TongGio
            FROM Attendance a
            WHERE a.WorkDate BETWEEN :from AND :to
              AND (:teacherId IS NULL OR a.TeacherId = :teacherId)
              AND (:status IS NULL OR a.Status = :status)
              AND (:keyword IS NULL OR EXISTS (
                    SELECT 1 FROM Teacher t
                    WHERE t.Id = a.TeacherId
                      AND LOWER(CONCAT(t.LastName, ' ', t.FirstName)) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
              ))
            """)
    Object[] summarize(
            @Param("teacherId") Integer teacherId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") String status,
            @Param("keyword") String keyword);

    /**
     * Nguyên liệu tính lương một kỳ: mỗi dòng chấm công CÓ CÔNG kèm sẵn khối lớp của buổi đó.
     *
     * <p>Vì sao gộp thành MỘT câu: bản cũ duyệt từng dòng chấm công rồi hỏi DB bốn lần
     * (Schedule → Assignment → AssignmentSlot → SchoolClass) để tra khối. Có cache nhưng cache
     * đánh theo scheduleId, mà mỗi dòng chấm công có scheduleId RIÊNG — nên nó không bao giờ
     * trúng. Một tháng ~750 dòng thành ~3.000 câu SQL cho một lần bấm "Tính lương".
     *
     * <p>Khối lấy theo lớp của Ô THỜI KHÓA BIỂU sinh ra buổi ({@code SourceSlotId}), lùi về
     * lớp cấp phiếu cho dữ liệu cũ chưa có slot — giống hệt luật ở {@code
     * ScheduleService.classIdOf}. Từ V16 một phiếu trải nhiều lớp, mà lớp 5 và lớp 6 khác đơn
     * giá, nên đọc lớp cấp phiếu là tính sai tiền.
     *
     * <p>Trả về khối dưới dạng CHUỖI (cột GradeLevel và tên lớp) để tầng service dùng lại
     * đúng hàm bóc số đang có, thay vì viết luật bóc số lần thứ hai trong SQL.
     *
     * <p>{@code RateAmount} là đơn giá ĐÃ ĐÓNG BĂNG lúc chấm công (V40). Lấy kèm ở đây để
     * service không phải hỏi lại từng dòng: có số thì dùng thẳng, NULL (dòng cũ trước V40) mới
     * đi tra bảng {@code PayRate} theo khối và ngày dạy như trước.
     *
     * @return các dòng {teacherId, workDate, status, gradeLevel, className, rateAmount}
     */
    @Query(nativeQuery = true, value = """
            SELECT a.TeacherId, a.WorkDate, a.Status, c.GradeLevel, c.Name AS ClassName, a.RateAmount
            FROM Attendance a
            JOIN Schedule s ON s.Id = a.ScheduleId
            LEFT JOIN Assignment asg ON asg.Id = s.AssignmentId
            LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
            LEFT JOIN SchoolClass c ON c.Id = COALESCE(sl.ClassId, asg.ClassId)
            WHERE a.WorkDate BETWEEN :from AND :to
              AND a.Status IN ('PRESENT', 'LATE')
            """)
    List<Object[]> findPayableWithGrade(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Số buổi ĐI MUỘN của từng giáo viên trong khoảng — một câu cho cả bảng lương.
     *
     * @return các dòng {teacherId, số buổi muộn}
     */
    @Query("""
            SELECT a.teacherId, COUNT(a)
            FROM Attendance a
            WHERE a.workDate BETWEEN :from AND :to
              AND a.status = 'LATE'
            GROUP BY a.teacherId
            """)
    List<Object[]> countLateByTeacher(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
