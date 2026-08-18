package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Room — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface RoomRepository extends JpaRepository<Room, Integer> {

    /** Phòng còn sống — dùng cho luồng xóa có kiểm soát ({@code RoomService.delete}). */
    Optional<Room> findByIdAndDeletedFalse(Integer id);
}
