package com.kdc.tsdms.service;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.repository.EmployeeRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import org.springframework.stereotype.Component;

/**
 * Ghép TÊN HIỂN THỊ cho một tài khoản từ hồ sơ tác nhân (Teacher/Employee), vì
 * {@link AppUser} không còn lưu họ tên.
 *
 * <ul>
 *   <li>Người (GV/NV): "Họ và tên đệm" + "Tên" = LastName + FirstName (thứ tự đọc tiếng Việt).</li>
 *   <li>Tài khoản hệ thống không có hồ sơ (vd admin): hiển thị theo username.</li>
 * </ul>
 *
 * <p>Không còn nhánh TRƯỜNG: trường không phải người dùng của hệ thống (Flyway V31).
 */
@Component
public class DisplayNameResolver {

    private final TeacherRepository teacherRepo;
    private final EmployeeRepository employeeRepo;

    public DisplayNameResolver(TeacherRepository teacherRepo, EmployeeRepository employeeRepo) {
        this.teacherRepo = teacherRepo;
        this.employeeRepo = employeeRepo;
    }

    /** Tên hiển thị của tài khoản (không bao giờ null — tệ nhất trả về username). */
    public String resolve(AppUser user) {
        var teacher = teacherRepo.findByAppUserIdAndDeletedFalse(user.getId());
        if (teacher.isPresent()) {
            return person(teacher.get().getLastName(), teacher.get().getFirstName(), user.getUsername());
        }
        var employee = employeeRepo.findByAppUserIdAndDeletedFalse(user.getId());
        if (employee.isPresent()) {
            return person(employee.get().getLastName(), employee.get().getFirstName(), user.getUsername());
        }
        return user.getUsername(); // admin / tài khoản không gắn hồ sơ
    }

    /** "Họ và tên đệm" + "Tên"; nếu rỗng thì fallback về username. */
    private static String person(String lastName, String firstName, String fallback) {
        String full = ((lastName == null ? "" : lastName) + " " + (firstName == null ? "" : firstName)).trim();
        return full.isEmpty() ? fallback : full;
    }
}
