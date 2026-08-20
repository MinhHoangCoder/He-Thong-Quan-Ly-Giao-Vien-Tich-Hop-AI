package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng Room — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface RoomRepository extends JpaRepository<Room, Integer> {}
