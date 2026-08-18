package com.kdc.tsdms.common;

import com.kdc.tsdms.exception.ApiException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Chốt chặn TOÀN VẸN THAM CHIẾU KHI XÓA — luật RESTRICT: còn dữ liệu con đang sống thì cấm
 * xóa bản ghi cha.
 *
 * <p>Vì sao phải viết tay thay vì để khóa ngoại lo: schema có 43 khóa ngoại và không cái nào
 * khai {@code ON DELETE}, nên SQL Server mặc định NO ACTION (= RESTRICT) — nghe thì tưởng đã
 * an toàn. Nhưng dự án xóa MỀM là chính (22/38 bảng có {@code IsDeleted}), mà xóa mềm chỉ là
 * một câu {@code UPDATE}. <b>Khóa ngoại không bao giờ nhìn thấy một câu UPDATE.</b> Nói cách
 * khác, khóa ngoại đang bảo vệ đúng thứ dự án hiếm khi làm, và mù với thứ dự án làm hằng ngày.
 *
 * <p>Cách dùng:
 *
 * <pre>{@code
 * DeleteGuard.of("trường " + s.getName())
 *         .blockIf(classRepo.countBySchoolIdAndDeletedFalse(id), "lớp học")
 *         .blockIf(assignmentRepo.countLiveBySchoolId(id), "phân công đang chạy")
 *         .check();
 * }</pre>
 *
 * <p><b>Gom HẾT lý do rồi mới báo một lần.</b> Kiểu cũ (ném lỗi ngay ở rào đầu tiên) bắt người
 * dùng sửa một thứ, bấm xóa lại, gặp rào tiếp theo, lặp tới khi hết — mỗi vòng một lần chờ mà
 * vẫn không biết còn bao nhiêu rào phía sau. Ở đây kể hết trong một câu để họ tự quyết định
 * còn nên xóa nữa hay không.
 */
public final class DeleteGuard {

    private final String subject;
    private final List<String> blockers = new ArrayList<>();
    private String huongDanRieng;

    private DeleteGuard(String subject) {
        this.subject = subject;
    }

    /**
     * @param subject tên bản ghi đang định xóa, dạng người đọc được (vd {@code "trường THCS Ba Đình"})
     */
    public static DeleteGuard of(String subject) {
        return new DeleteGuard(subject);
    }

    /**
     * Ghi nhận một rào chắn nếu {@code count > 0}.
     *
     * @param noun tên loại dữ liệu con, số NHIỀU, không kèm số lượng (vd {@code "lớp học"}) —
     *     số lượng do hàm này tự ghép vào để mọi thông báo trong dự án cùng một dạng
     */
    public DeleteGuard blockIf(long count, String noun) {
        if (count > 0) {
            blockers.add(count + " " + noun);
        }
        return this;
    }

    /** Rào chắn không đếm được (vd "kỳ lương đã chốt") — chỉ có hoặc không. */
    public DeleteGuard blockWhen(boolean condition, String reason) {
        if (condition) {
            blockers.add(reason);
        }
        return this;
    }

    /**
     * Nhận cả một mớ rào chắn đã thành câu sẵn — dùng khi một câu SQL gom hết dữ liệu con của
     * nhiều bảng trong một lượt (xem {@code TeacherRepository.countChildRowsByTeacherId}) thay
     * vì tiêm chục repository vào constructor chỉ để đếm.
     */
    public DeleteGuard blockAll(Collection<String> reasons) {
        if (reasons != null) {
            reasons.stream().filter(r -> r != null && !r.isBlank()).forEach(blockers::add);
        }
        return this;
    }

    /**
     * Thay câu hướng dẫn mặc định. Cần khi rào chắn là thứ người dùng KHÔNG có cách nào tự gỡ
     * (kỳ lương đã chốt, hồ sơ pháp lý phải lưu) — câu mặc định "vui lòng xử lý trước khi xóa"
     * lúc đó thành lời hứa suông, người dùng đi tìm nút gỡ không bao giờ có.
     */
    public DeleteGuard huongDan(String huongDan) {
        this.huongDanRieng = huongDan;
        return this;
    }

    /** Có rào nào không — dùng khi bên gọi muốn tự xử lý thay vì ném lỗi. */
    public boolean blocked() {
        return !blockers.isEmpty();
    }

    /** Ném 409 kèm ĐẦY ĐỦ lý do nếu có rào; không có rào thì trả về im lặng. */
    public void check() {
        if (blockers.isEmpty()) {
            return;
        }
        throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa " + subject + ": còn " + join() + ". " + huong());
    }

    /** "3 lớp học, 2 phân công đang chạy và 1 hợp đồng dịch vụ" — dấu phẩy, cuối cùng là "và". */
    private String join() {
        if (blockers.size() == 1) {
            return blockers.get(0);
        }
        String head = String.join(", ", blockers.subList(0, blockers.size() - 1));
        return head + " và " + blockers.get(blockers.size() - 1);
    }

    private String huong() {
        if (huongDanRieng != null) {
            return huongDanRieng;
        }
        return blockers.size() == 1
                ? "Vui lòng xử lý mục này trước khi xóa."
                : "Vui lòng xử lý các mục trên trước khi xóa.";
    }
}
