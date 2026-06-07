package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

    // Chỉ tìm trong tài khoản CHƯA xóa mềm (deleted = false).
    Optional<AppUser> findByUsernameAndDeletedFalse(String username);

    Optional<AppUser> findByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalse(String email);
}
