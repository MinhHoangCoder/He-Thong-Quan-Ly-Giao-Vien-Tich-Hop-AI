package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdc.tsdms.entity.School;
import com.kdc.tsdms.entity.Teacher;
import com.kdc.tsdms.exception.ApiException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * PHÂN CÔNG MỚI CHỈ DÀNH CHO GIÁO VIÊN ĐANG LÀM VIỆC VÀ TRƯỜNG ĐANG HỢP TÁC.
 *
 * <p>Cả hai bảng đều giữ lại bản ghi đã ngừng hoạt động thay vì xóa — hồ sơ giáo viên nghỉ
 * hưu còn gắn chấm công và phiếu lương, trường hết hạn còn gắn hợp đồng dịch vụ. Nghĩa là
 * {@code deleted = false} KHÔNG đủ để kết luận "dùng được", mà đó lại đúng là điều kiện duy
 * nhất mà luồng tạo phiếu từng kiểm tra.
 *
 * <p>Trên dữ liệu thật: 100 hồ sơ giáo viên nhưng 7 RETIRED + 3 SUSPENDED, và 30 trường nhưng
 * 4 EXPIRED + 8 INACTIVE. Không lọc thì người xếp lịch chọn nhầm mà không có gì cản: phiếu lưu
 * thành công, lời mời gửi đi, lịch dạy sinh đủ — chỉ có điều sẽ không có ai tới lớp.
 */
class AssignmentActiveOnlyTest {

    private static void checkTeacher(String status) {
        Teacher t = new Teacher();
        t.setId(1);
        t.setLastName("Nguyễn Văn");
        t.setFirstName("An");
        t.setStatus(status);
        invoke("assertTeacherAvailable", Teacher.class, t);
    }

    private static void checkSchool(String status) {
        School s = new School();
        s.setId(1);
        s.setName("TH Cát Bi");
        s.setStatus(status);
        invoke("assertSchoolActive", School.class, s);
    }

    private static void invoke(String name, Class<?> paramType, Object arg) {
        try {
            Method m = AssignmentService.class.getDeclaredMethod(name, paramType);
            m.setAccessible(true);
            m.invoke(null, arg);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void giao_vien_dang_lam_viec_thi_qua() {
        assertThatCode(() -> checkTeacher("ACTIVE")).doesNotThrowAnyException();
    }

    @Test
    void chan_giao_vien_da_nghi_viec() {
        assertThatThrownBy(() -> checkTeacher("RETIRED"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Nguyễn Văn An")
                .hasMessageContaining("đã nghỉ việc")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void chan_giao_vien_dang_dinh_chi() {
        assertThatThrownBy(() -> checkTeacher("SUSPENDED")).hasMessageContaining("tạm đình chỉ");
    }

    @Test
    void truong_dang_hop_tac_thi_qua() {
        assertThatCode(() -> checkSchool("ACTIVE")).doesNotThrowAnyException();
    }

    @Test
    void chan_truong_het_han_hop_dong() {
        assertThatThrownBy(() -> checkSchool("EXPIRED"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("TH Cát Bi")
                .hasMessageContaining("hết hạn hợp đồng");
    }

    @Test
    void chan_truong_ngung_hop_tac() {
        assertThatThrownBy(() -> checkSchool("INACTIVE")).hasMessageContaining("ngừng hợp tác");
    }

    @Test
    void trang_thai_trong_cung_bi_chan() {
        // null không phải "an toàn": dữ liệu thiếu trạng thái thì không có cơ sở nói là dùng được.
        assertThatThrownBy(() -> checkTeacher(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> checkSchool(null)).isInstanceOf(ApiException.class);
    }
}
