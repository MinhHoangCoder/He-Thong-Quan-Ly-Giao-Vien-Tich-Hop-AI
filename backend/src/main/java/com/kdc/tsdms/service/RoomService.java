package com.kdc.tsdms.service;

import com.kdc.tsdms.common.BusinessTime;
import com.kdc.tsdms.common.DeleteGuard;
import com.kdc.tsdms.entity.Room;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AssignmentSlotRepository;
import com.kdc.tsdms.repository.RoomRepository;
import com.kdc.tsdms.repository.ScheduleRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Phòng học (của trường khách hàng). Chưa có API xóa — service này chốt sẵn luật:
 * ai làm feature xóa phòng thì gọi {@link #delete}, đừng tự viết.
 *
 * <p>Luật: chỉ chặn theo thứ SẮP DÙNG phòng — buổi dạy tương lai còn hiệu lực và ô thời khóa
 * biểu hằng tuần còn sống. Buổi ĐÃ DẠY trong quá khứ cố tình không chặn: đó là lịch sử, phòng
 * xóa mềm vẫn còn dòng trong DB nên tên phòng trên các buổi cũ vẫn tra ra được.
 */
@Service
public class RoomService {

    /** Buổi dạy còn hiệu lực — cùng bộ trạng thái với chốt xóa giáo viên. */
    private static final List<String> BUOI_CON_HIEU_LUC = List.of("PENDING", "APPROVED");

    private final RoomRepository roomRepo;
    private final ScheduleRepository scheduleRepo;
    private final AssignmentSlotRepository slotRepo;

    public RoomService(RoomRepository roomRepo, ScheduleRepository scheduleRepo, AssignmentSlotRepository slotRepo) {
        this.roomRepo = roomRepo;
        this.scheduleRepo = scheduleRepo;
        this.slotRepo = slotRepo;
    }

    /** Xóa mềm phòng học — chặn khi còn lịch sắp tới hoặc thời khóa biểu đang đặt phòng. */
    @Transactional
    public void delete(Integer id) {
        Room r = roomRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng id=" + id));
        DeleteGuard.of("phòng " + r.getName())
                .blockIf(
                        scheduleRepo.countByRoomIdAndStartTimeAfterAndStatusInAndDeletedFalse(
                                id, BusinessTime.now(), BUOI_CON_HIEU_LUC),
                        "buổi dạy sắp tới đặt phòng này")
                .blockIf(slotRepo.countByRoomIdAndDeletedFalse(id), "ô thời khóa biểu hằng tuần gắn phòng này")
                .check();
        Integer nguoiXoa = SecurityUtils.currentUserId();
        r.setDeleted(true);
        r.setDeletedAt(Instant.now());
        r.setDeletedBy(nguoiXoa);
        r.setUpdatedAt(Instant.now());
        r.setUpdatedBy(nguoiXoa);
        roomRepo.save(r);
    }
}
