package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.AppUser;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

    // Chỉ tìm trong tài khoản CHƯA xóa mềm (deleted = false).
    Optional<AppUser> findByUsernameAndDeletedFalse(String username);

    Optional<AppUser> findByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalse(String email);

    // Dùng khi SỬA: kiểm tra trùng nhưng loại trừ chính tài khoản đang sửa.
    boolean existsByUsernameAndDeletedFalseAndIdNot(String username, Integer id);

    boolean existsByEmailAndDeletedFalseAndIdNot(String email, Integer id);

    // Danh sách tài khoản (trang quản trị) — phân trang, chưa xóa mềm.
    Page<AppUser> findByDeletedFalse(Pageable pageable);

    // Tìm tài khoản theo username/email chứa từ khóa (trang quản trị).
    @Query("SELECT u FROM AppUser u WHERE u.deleted = false "
            + "AND (u.username LIKE CONCAT('%', :kw, '%') OR u.email LIKE CONCAT('%', :kw, '%'))")
    Page<AppUser> searchByKeyword(@Param("kw") String keyword, Pageable pageable);
}
