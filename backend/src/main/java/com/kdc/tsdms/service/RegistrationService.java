package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.RegisterRequest;
import com.kdc.tsdms.dto.UserInfo;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Role;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.RoleRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import com.kdc.tsdms.security.SecurityUtils;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tài khoản GIÁO VIÊN. Một thao tác tạo gồm: AppUser + UserRole + hồ sơ Teacher,
 * bọc trong một transaction. Chặn theo QUYỀN chứ không theo tên role: cần
 * {@code TEACHER_MANAGE} (phòng Nhân sự), ADMIN đi tắt.
 *
 * <p>Từ 2026-08-19 KHÔNG còn tạo tài khoản cho trường: trường khách hàng là DỮ LIỆU
 * của hệ thống chứ không phải người dùng (Flyway V31 đã bỏ role SCHOOL và cột
 * School.AppUserId). Hồ sơ trường tạo ở màn "Trường khách hàng" của nhân viên trung tâm.
 */
@Service
public class RegistrationService {

    private final AppUserRepository appUserRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final BranchRepository branchRepo;
    private final TeacherRepository teacherRepo;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            AppUserRepository appUserRepo,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo,
            BranchRepository branchRepo,
            TeacherRepository teacherRepo,
            PasswordEncoder passwordEncoder) {
        this.appUserRepo = appUserRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.branchRepo = branchRepo;
        this.teacherRepo = teacherRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserInfo register(RegisterRequest req) {
        String role = req.role().trim().toUpperCase();
        if (!role.equals("TEACHER")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ được tạo tài khoản vai trò TEACHER");
        }

        if (isBlank(req.firstName()) || isBlank(req.lastName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu tên gọi hoặc họ và tên đệm của giáo viên");
        }

        // Chặn theo QUYỀN chứ không theo tên role: cần TEACHER_MANAGE (phòng Nhân sự), ADMIN
        // đi tắt. @PreAuthorize ở controller chỉ lọc thô, chốt chặn thật nằm ở đây.
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasAuthority("TEACHER_MANAGE")) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền tạo tài khoản giáo viên (cần TEACHER_MANAGE)");
        }
        if (appUserRepo.existsByUsernameAndDeletedFalse(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username đã tồn tại");
        }
        if (appUserRepo.existsByEmailAndDeletedFalse(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email đã tồn tại");
        }
        if (!branchRepo.existsById(req.branchId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chi nhánh không tồn tại");
        }
        Role roleEntity = roleRepo.findByName(role)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Thiếu vai trò " + role));

        // 1) Tạo tài khoản đăng nhập (mật khẩu băm BCrypt)
        AppUser user = new AppUser();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus("ACTIVE");
        appUserRepo.save(user);

        // 2) Gán vai trò
        userRoleRepo.save(new UserRole(user.getId(), roleEntity.getId()));

        // 3) Tạo hồ sơ giáo viên + tên hiển thị trả về
        Teacher t = new Teacher();
        t.setAppUserId(user.getId());
        t.setBranchId(req.branchId());
        t.setFirstName(req.firstName().trim());
        t.setLastName(req.lastName().trim());
        t.setPhone(req.phone());
        t.setStatus("ACTIVE");
        teacherRepo.save(t);
        String displayName = (t.getLastName() + " " + t.getFirstName()).trim();

        // perms để rỗng ở đây — user mới đăng ký sẽ nhận đủ quyền theo role khi đăng nhập.
        return new UserInfo(user.getId(), user.getUsername(), displayName, user.getEmail(), List.of(role), List.of());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
