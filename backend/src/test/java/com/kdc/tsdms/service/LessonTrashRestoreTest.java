package com.kdc.tsdms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdc.tsdms.dto.LessonTrashItem;
import com.kdc.tsdms.entity.Lesson;
import com.kdc.tsdms.entity.LessonFile;
import com.kdc.tsdms.entity.Subject;
import com.kdc.tsdms.entity.SubjectCategory;
import com.kdc.tsdms.exception.ApiException;
import com.kdc.tsdms.repository.BranchRepository;
import com.kdc.tsdms.repository.LessonFileRepository;
import com.kdc.tsdms.repository.LessonRepository;
import com.kdc.tsdms.repository.SubjectCategoryRepository;
import com.kdc.tsdms.repository.SubjectRepository;
import com.kdc.tsdms.repository.TeacherRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * Thùng rác Kho bài giảng — KHÔNG cần DB.
 *
 * <p>Trước Đợt 5, Kho bài giảng xóa MỀM nhưng không có màn hình nào lôi bài đã xóa ra, nên
 * "xóa mềm" ở đây bằng đúng xóa vĩnh viễn từ góc nhìn người dùng. Bài giảng lại là thứ tốn
 * công soạn nhất hệ thống. Bộ test này canh hai chi tiết dễ làm sai nhất của luồng khôi phục.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonTrashRestoreTest {

    private static final int LESSON_ID = 7;
    private static final int SUBJECT_ID = 3;
    private static final Instant XOA_LUC = Instant.parse("2026-08-20T03:00:00Z");
    private static final Instant XOA_RIENG_TRUOC_DO = Instant.parse("2026-07-01T02:00:00Z");

    @Mock
    private LessonRepository lessonRepo;

    @Mock
    private LessonFileRepository lessonFileRepo;

    @Mock
    private BranchRepository branchRepo;

    @Mock
    private TeacherRepository teacherRepo;

    @Mock
    private SubjectRepository subjectRepo;

    @Mock
    private SubjectCategoryRepository subjectCategoryRepo;

    @InjectMocks
    private LessonService service;

    private Lesson baiTrongThungRac() {
        Lesson l = new Lesson();
        l.setId(LESSON_ID);
        l.setSubjectId(SUBJECT_ID);
        l.setTitle("Bài 3 - Vòng lặp trong Scratch");
        l.setGradeLevel("Lớp 6");
        l.setStatus("PUBLISHED");
        l.setDeleted(true);
        l.setDeletedAt(XOA_LUC);
        when(lessonRepo.findByIdAndDeletedTrue(LESSON_ID)).thenReturn(Optional.of(l));
        when(lessonRepo.save(l)).thenReturn(l);
        return l;
    }

    private LessonFile file(int id, Instant xoaLuc) {
        LessonFile f = new LessonFile();
        f.setId(id);
        f.setLessonId(LESSON_ID);
        f.setFileName("tai-lieu-" + id + ".pdf");
        f.setDeleted(true);
        f.setDeletedAt(xoaLuc);
        return f;
    }

    private Subject monConSong() {
        Subject s = new Subject();
        s.setId(SUBJECT_ID);
        s.setName("Lập trình Scratch");
        when(subjectRepo.findById(SUBJECT_ID)).thenReturn(Optional.of(s));
        return s;
    }

    /**
     * Chi tiết tinh tế nhất của luồng này: file bị xóa RIÊNG bằng nút xóa file phải nằm nguyên
     * chỗ đã xóa. Khôi phục bừa mọi file đã xóa của bài là lôi ngược thứ người dùng đã cố ý bỏ.
     * Phân biệt bằng dấu thời gian — delete() gắn CÙNG MỘT deletedAt cho bài và mọi file bị
     * cuốn theo.
     */
    @Test
    void khoiPhuc_chiTraLaiFileBienMatTheoBai_khongDungFileDaXoaRieng() {
        Lesson l = baiTrongThungRac();
        monConSong();
        LessonFile veTheoBai = file(1, XOA_LUC);
        LessonFile xoaRiengTuTruoc = file(2, XOA_RIENG_TRUOC_DO);
        when(lessonFileRepo.findByLessonIdAndDeletedTrue(LESSON_ID)).thenReturn(List.of(veTheoBai, xoaRiengTuTruoc));

        service.restore(LESSON_ID);

        assertThat(l.isDeleted()).isFalse();
        assertThat(l.getDeletedAt()).isNull();
        assertThat(veTheoBai.isDeleted()).isFalse();
        assertThat(xoaRiengTuTruoc.isDeleted())
                .as("file người dùng đã cố ý xóa riêng thì không được lôi về theo")
                .isTrue();
        verify(lessonFileRepo).saveAll(List.of(veTheoBai));
    }

    /**
     * Xóa bài giảng cuối cùng của một môn làm môn đó rỗng, mà môn rỗng thì xóa được (rào chắn
     * chỉ đếm bài giảng CÒN SỐNG). Nên khôi phục bài giảng phải dựng lại cả cái giá đỡ của nó,
     * nếu không thì đẻ ra đúng "môn mồ côi" mà cả dự án đang chống.
     */
    @Test
    void khoiPhuc_dungLaiCaMonHocVaNhomMonDangNamTrongTrangThaiDaXoa() {
        baiTrongThungRac();
        SubjectCategory nhom = new SubjectCategory();
        nhom.setId(9);
        nhom.setName("Tin học");
        nhom.setDeleted(true);
        nhom.setDeletedAt(XOA_LUC);
        Subject mon = new Subject();
        mon.setId(SUBJECT_ID);
        mon.setName("Lập trình Scratch");
        mon.setStatus("DISABLED");
        mon.setCategory(nhom);
        mon.setDeleted(true);
        mon.setDeletedAt(XOA_LUC);
        when(subjectRepo.findById(SUBJECT_ID)).thenReturn(Optional.of(mon));
        when(lessonFileRepo.findByLessonIdAndDeletedTrue(LESSON_ID)).thenReturn(List.of());

        service.restore(LESSON_ID);

        assertThat(mon.isDeleted()).isFalse();
        assertThat(nhom.isDeleted()).isFalse();
        // Trạng thái CỐ Ý giữ nguyên DISABLED — thứ vừa moi khỏi thùng rác chưa chắc đã muốn
        // cho chạy lại ngay, cùng lý lẽ với SchoolService.restore giữ trường ở INACTIVE.
        assertThat(mon.getStatus()).isEqualTo("DISABLED");
        verify(subjectRepo).save(mon);
        verify(subjectCategoryRepo).save(nhom);
    }

    /** Môn còn sống thì không đụng vào — khôi phục bài giảng không phải cớ để sửa môn. */
    @Test
    void khoiPhuc_monConSong_thiKhongDungToiMonHoc() {
        baiTrongThungRac();
        Subject mon = monConSong();
        when(lessonFileRepo.findByLessonIdAndDeletedTrue(LESSON_ID)).thenReturn(List.of());

        service.restore(LESSON_ID);

        assertThat(mon.isDeleted()).isFalse();
        verify(subjectRepo, never()).save(mon);
        verify(subjectCategoryRepo, never()).save(any());
    }

    @Test
    void khoiPhuc_baiKhongNamTrongThungRac_thi404() {
        when(lessonRepo.findByIdAndDeletedTrue(LESSON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(LESSON_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** Danh sách thùng rác đếm ĐÚNG số file sẽ về theo, không đếm file đã xóa riêng. */
    @Test
    void danhSachThungRac_demDungSoFileSeVeTheoBai() {
        Lesson l = new Lesson();
        l.setId(LESSON_ID);
        l.setSubjectId(SUBJECT_ID);
        l.setTitle("Bài 3 - Vòng lặp trong Scratch");
        l.setDeleted(true);
        l.setDeletedAt(XOA_LUC);
        Subject mon = new Subject();
        mon.setId(SUBJECT_ID);
        mon.setName("Lập trình Scratch");
        when(lessonRepo.findByDeletedTrueOrderByDeletedAtDesc()).thenReturn(List.of(l));
        when(subjectRepo.findAllById(List.of(SUBJECT_ID))).thenReturn(List.of(mon));
        when(lessonFileRepo.findByLessonIdAndDeletedTrue(LESSON_ID))
                .thenReturn(List.of(file(1, XOA_LUC), file(2, XOA_LUC), file(3, XOA_RIENG_TRUOC_DO)));

        List<LessonTrashItem> trash = service.listTrash();

        assertThat(trash).hasSize(1);
        assertThat(trash.get(0).soFileKemTheo()).isEqualTo(2);
        assertThat(trash.get(0).subjectName()).isEqualTo("Lập trình Scratch");
        assertThat(trash.get(0).deletedAt()).isEqualTo(XOA_LUC);
    }
}
