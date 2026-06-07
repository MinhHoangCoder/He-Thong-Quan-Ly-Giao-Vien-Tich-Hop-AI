package com.kdc.tsdms.service;

import com.kdc.tsdms.dto.RegisterRequest;
import com.kdc.tsdms.dto.UserInfo;
import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Role;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.entity.UserRole;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.AppUserRepository;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.RoleRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import com.kdc.tsdms.repository.UserRoleRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tài khoản cho GIÁO VIÊN và TRƯỜNG. Chỉ ADMIN/EMPLOYEE được gọi (chặn ở SecurityConfig).
 * Một thao tác tạo gồm: AppUser + UserRole + hồ sơ (Teacher hoặc School) -> bọc trong 1 transaction.
 */
@Service
public class RegistrationService {

    private final AppUserRepository appUserRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final BranchRepository branchRepo;
    private final TeacherRepository teacherRepo;
    private final SchoolRepository schoolRepo;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            AppUserRepository appUserRepo,
            RoleRepository roleRepo,
            UserRoleRepository userRoleRepo,
            BranchRepository branchRepo,
            TeacherRepository teacherRepo,
            SchoolRepository schoolRepo,
            PasswordEncoder passwordEncoder) {
        this.appUserRepo = appUserRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.branchRepo = branchRepo;
        this.teacherRepo = teacherRepo;
        this.schoolRepo = schoolRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserInfo register(RegisterRequest req) {
        String role = req.role().trim().toUpperCase();
        if (!role.equals("TEACHER") && !role.equals("SCHOOL")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ được tạo tài khoản vai trò TEACHER hoặc SCHOOL");
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
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus("ACTIVE");
        appUserRepo.save(user);

        // 2) Gán vai trò
        userRoleRepo.save(new UserRole(user.getId(), roleEntity.getId()));

        // 3) Tạo hồ sơ tương ứng
        if (role.equals("TEACHER")) {
            Teacher t = new Teacher();
            t.setAppUserId(user.getId());
            t.setBranchId(req.branchId());
            t.setFullName(req.fullName());
            t.setStatus("ACTIVE");
            teacherRepo.save(t);
        } else {
            School s = new School();
            s.setBranchId(req.branchId());
            s.setName(req.fullName()); // dùng fullName làm tên trường (đơn giản hóa bản đầu)
            s.setAppUserId(user.getId());
            s.setContactPerson(req.fullName());
            s.setStatus("ACTIVE");
            schoolRepo.save(s);
        }

        return new UserInfo(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), List.of(role));
    }
}
