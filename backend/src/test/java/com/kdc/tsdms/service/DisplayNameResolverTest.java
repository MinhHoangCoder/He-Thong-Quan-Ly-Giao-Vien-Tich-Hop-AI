package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.entity.AppUser;
import com.kdc.tsdms.entity.Employee;
import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.repository.EmployeeRepository;
import com.kdc.tsdms.repository.SchoolRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test cho DisplayNameResolver — KHÔNG cần DB, repo mock bằng Mockito.
 *
 * <p>Kiểm chứng góp ý GVHD ở V6: AppUser bỏ họ tên, tên hiển thị ghép từ hồ sơ tác nhân
 * theo thứ tự đọc tiếng Việt "Họ và tên đệm" + "Tên" (LastName + FirstName). Cộng thứ tự
 * ưu tiên Teacher → Employee → School → username, và các trường hợp tên rỗng/null.
 */
@ExtendWith(MockitoExtension.class)
class DisplayNameResolverTest {

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private SchoolRepository schoolRepo;

    @InjectMocks
    private DisplayNameResolver resolver;

    private static AppUser user(int id, String username) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private static Teacher teacher(String lastName, String firstName) {
        Teacher t = new Teacher();
        t.setLastName(lastName);
        t.setFirstName(firstName);
        return t;
    }

    private static Employee employee(String lastName, String firstName) {
        Employee e = new Employee();
        e.setLastName(lastName);
        e.setFirstName(firstName);
        return e;
    }

    private static School school(String name) {
        School s = new School();
        s.setName(name);
        return s;
    }

    @Test
    void resolve_teacher_joinsLastNameThenFirstName_vietnameseOrder() {
        // "Nguyễn Văn" (họ và tên đệm) + "An" (tên gọi) = "Nguyễn Văn An".
        when(teacherRepo.findByAppUserIdAndDeletedFalse(1)).thenReturn(Optional.of(teacher("Nguyễn Văn", "An")));

        assertThat(resolver.resolve(user(1, "gv01"))).isEqualTo("Nguyễn Văn An");
    }

    @Test
    void resolve_employee_joinsLastNameThenFirstName() {
        // Không phải GV -> rơi xuống hồ sơ nhân viên.
        when(teacherRepo.findByAppUserIdAndDeletedFalse(2)).thenReturn(Optional.empty());
        when(employeeRepo.findByAppUserIdAndDeletedFalse(2)).thenReturn(Optional.of(employee("Trần Thị", "Bình")));

        assertThat(resolver.resolve(user(2, "nv01"))).isEqualTo("Trần Thị Bình");
    }

    @Test
    void resolve_school_usesSchoolName() {
        when(teacherRepo.findByAppUserIdAndDeletedFalse(3)).thenReturn(Optional.empty());
        when(employeeRepo.findByAppUserIdAndDeletedFalse(3)).thenReturn(Optional.empty());
        when(schoolRepo.findByAppUserIdAndDeletedFalse(3)).thenReturn(Optional.of(school("Trường THCS Demo")));

        assertThat(resolver.resolve(user(3, "sch01"))).isEqualTo("Trường THCS Demo");
    }

    @Test
    void resolve_noProfile_fallsBackToUsername() {
        // Tài khoản admin / không gắn hồ sơ -> hiển thị theo username.
        when(teacherRepo.findByAppUserIdAndDeletedFalse(4)).thenReturn(Optional.empty());
        when(employeeRepo.findByAppUserIdAndDeletedFalse(4)).thenReturn(Optional.empty());
        when(schoolRepo.findByAppUserIdAndDeletedFalse(4)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(user(4, "admin"))).isEqualTo("admin");
    }

    @Test
    void resolve_teacherTakesPriorityOverEmployeeAndSchool() {
        // Có hồ sơ GV thì dừng ngay, không truy vấn employee/school (STRICT_STUBS đảm bảo).
        when(teacherRepo.findByAppUserIdAndDeletedFalse(5)).thenReturn(Optional.of(teacher("Lê", "Cường")));

        assertThat(resolver.resolve(user(5, "gv02"))).isEqualTo("Lê Cường");
    }

    @Test
    void resolve_teacherWithBlankNames_fallsBackToUsername() {
        // Hồ sơ tồn tại nhưng tên rỗng/null -> person() fallback về username, không trả chuỗi rỗng.
        when(teacherRepo.findByAppUserIdAndDeletedFalse(6)).thenReturn(Optional.of(teacher(" ", null)));

        assertThat(resolver.resolve(user(6, "gv03"))).isEqualTo("gv03");
    }

    @Test
    void resolve_teacherWithOnlyLastName_trimsTrailingSpace() {
        // Chỉ có họ, thiếu tên gọi -> không để lại khoảng trắng thừa ở cuối.
        when(teacherRepo.findByAppUserIdAndDeletedFalse(7)).thenReturn(Optional.of(teacher("Phạm Văn", null)));

        assertThat(resolver.resolve(user(7, "gv04"))).isEqualTo("Phạm Văn");
    }

    @Test
    void resolve_schoolWithBlankName_fallsBackToUsername() {
        // Hồ sơ trường tồn tại nhưng tên trống -> bỏ qua, dùng username.
        when(teacherRepo.findByAppUserIdAndDeletedFalse(8)).thenReturn(Optional.empty());
        when(employeeRepo.findByAppUserIdAndDeletedFalse(8)).thenReturn(Optional.empty());
        when(schoolRepo.findByAppUserIdAndDeletedFalse(8)).thenReturn(Optional.of(school("  ")));

        assertThat(resolver.resolve(user(8, "sch02"))).isEqualTo("sch02");
    }
}
