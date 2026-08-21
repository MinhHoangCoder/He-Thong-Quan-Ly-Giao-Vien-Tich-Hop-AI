package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository bảng Room — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface RoomRepository extends JpaRepository<Room, Integer> {

    /** Phòng còn sống — dùng cho luồng xóa có kiểm soát ({@code RoomService.delete}). */
    Optional<Room> findByIdAndDeletedFalse(Integer id);

    /**
     * Xóa CỨNG mọi phòng học của một trường — chỉ gọi khi xóa vĩnh viễn trường khỏi thùng rác.
     * Phòng học là cấu hình riêng của trường, không phải dữ liệu nghiệp vụ độc lập.
     */
    @Modifying
    @Query("DELETE FROM Room r WHERE r.schoolId = :schoolId")
    int xoaCungTheoTruong(@Param("schoolId") Integer schoolId);
}
