# dev-notes — Ghi chú giải thích tính năng

Mỗi khi Claude làm một tính năng **khó / quan trọng**, sẽ viết một file ghi chú ở đây,
giải thích kỹ luồng hoạt động để bạn (lần đầu dùng VueJS) hiểu và tự code tiếp được.
Có cả ghi chú cho **backend** khi bắt đầu code phần đó.

Quy ước đặt tên: `YYYY-MM-DD-<phần>-<chủ-đề>.md`
(phần = `frontend` | `backend` | `database` | `fullstack`).

## Danh sách ghi chú
| Ngày | Phần | Chủ đề | File |
|---|---|---|---|
| 2026-06-06 | frontend | Hệ thống layout + Dashboard quản trị | [2026-06-06-frontend-dashboard.md](2026-06-06-frontend-dashboard.md) |
| 2026-06-06 | frontend | Đổi theme (xanh ngọc + xanh lá) & hiệu ứng hover/animation | [2026-06-06-frontend-theme-animation.md](2026-06-06-frontend-theme-animation.md) |
| 2026-06-06 | fullstack | Bỏ bảng TeacherRequest — trung tâm toàn quyền phân công | [2026-06-06-fullstack-bo-teacherrequest.md](2026-06-06-fullstack-bo-teacherrequest.md) |
| 2026-06-07 | fullstack | Đổi tên dự án TSMS → TSDMS | [2026-06-07-rename-tsms-tsdms.md](2026-06-07-rename-tsms-tsdms.md) |
| 2026-06-07 | fullstack | Đăng nhập/Đăng ký/Đăng xuất/Quên mật khẩu (JWT + Spring Security) | [2026-06-07-auth-jwt-spring-security.md](2026-06-07-auth-jwt-spring-security.md) |
| 2026-06-08 | frontend | Portal Giáo viên & Trường | [2026-06-08-portal-teacher-school.md](2026-06-08-portal-teacher-school.md) |
| 2026-06-09 | backend | Bảo mật: IDOR, Rate limit & Row-Level Security | [2026-06-09-backend-security-idor-ratelimit-rls.md](2026-06-09-backend-security-idor-ratelimit-rls.md) |
| 2026-06-09 | frontend | Rebrand "KDC EduOps AI" + theme Trắng/Cam/Xanh lam + login hero | [2026-06-09-frontend-rebrand-kdc-eduops-ai.md](2026-06-09-frontend-rebrand-kdc-eduops-ai.md) |
| 2026-06-10 | fullstack | Gia cố bảo mật auth (bản vá tạm trước Cookie + Bucket4j/Redis) | [2026-06-10-fullstack-auth-security-hardening.md](2026-06-10-fullstack-auth-security-hardening.md) |
| 2026-06-12 | backend | Đóng băng tầng Entity: map FULL 28 bảng (chống conflict nhóm) | [2026-06-12-backend-entity-layer-full-mapping.md](2026-06-12-backend-entity-layer-full-mapping.md) |
| 2026-06-12 | fullstack | ⭐ Quy ước làm việc nhóm — chống merge conflict (gửi mọi thành viên) | [quy-uoc-lam-viec-nhom.md](quy-uoc-lam-viec-nhom.md) |
| 2026-06-13 | database | Module Bài giảng (Lesson) + Flyway V2 + Seed demo toàn hệ thống | [2026-06-13-database-lesson-module-seed.md](2026-06-13-database-lesson-module-seed.md) |
| 2026-06-14 | backend | ⭐ Phân quyền RBAC theo permission — Ma trận quyền & quy ước nhóm | [2026-06-14-backend-rbac-permission-matrix.md](2026-06-14-backend-rbac-permission-matrix.md) |
| 2026-06-14 | database | Bảng ServiceContract — HĐ dịch vụ trường↔trung tâm (nguồn doanh thu) | [2026-06-14-database-service-contract.md](2026-06-14-database-service-contract.md) |
| 2026-06-16 | frontend | Portal phòng ban (staff) + tách route theo khu vực + menu động theo quyền | [2026-06-16-frontend-portal-phong-ban.md](2026-06-16-frontend-portal-phong-ban.md) |
| 2026-06-17 | backend | Chốt chặn `/register` theo quyền (2 tầng) + tài khoản test `employee` (V5) | [2026-06-17-backend-rbac-register-guard.md](2026-06-17-backend-rbac-register-guard.md) |
| 2026-06-19 | backend | API Bài giảng (Lesson) — CRUD + upload file | [2026-06-19-backend-lesson-api-crud-upload.md](2026-06-19-backend-lesson-api-crud-upload.md) |
| 2026-06-20 | fullstack | ⭐ Áp góp ý GVHD vào DB: tách họ tên, AppUser bỏ trùng, Contract 1-1, PK→`Id` (V6+V7) | [2026-06-20-database-gvhd-3-thay-doi.md](2026-06-20-database-gvhd-3-thay-doi.md) |
| 2026-06-25 | database | Chuẩn hóa nhóm môn: bảng SubjectCategory (V8) | [2026-06-25-database-subject-category-lookup.md](2026-06-25-database-subject-category-lookup.md) |
| 2026-06-27 | backend | Category đọc từ SubjectCategory | [2026-06-27-category-from-subject-category.md](2026-06-27-category-from-subject-category.md) |
| 2026-06-30 | database | Lịch dạy GV (Period/AssignmentSlot, V9) + ca làm NV (V10) | [2026-06-30-database-teacher-timetable-employee-shift.md](2026-06-30-database-teacher-timetable-employee-shift.md) |
| 2026-07-02 | backend | ⭐ Lưới an toàn schema ↔ entity: Testcontainers + Flyway + Hibernate validate | [2026-07-02-backend-integration-test-schema-validate.md](2026-07-02-backend-integration-test-schema-validate.md) |
| 2026-07-06 | fullstack | Tính năng Cài đặt (cá nhân + hệ thống) & các bản vá bảo mật | [2026-07-06-settings-account-sessions.md](2026-07-06-settings-account-sessions.md) |
| 2026-07-06 | database | Bẫy `sp_rename` không sửa thân TRIGGER (V11) | [2026-07-06-v11-trigger-sprename-trap.md](2026-07-06-v11-trigger-sprename-trap.md) |
| 2026-07-07 | fullstack | Account switcher — đăng nhập nhiều tài khoản, chuyển nhanh không cần logout | [2026-07-07-account-switcher-multi-session.md](2026-07-07-account-switcher-multi-session.md) |
| 2026-07-09 | backend | Mở lại `/uploads` (thu hẹp đúng `/uploads/lessons/**`) | [2026-07-09-backend-uploads-public-scope.md](2026-07-09-backend-uploads-public-scope.md) |
| 2026-07-10 | frontend | Cài đặt: bỏ card "Mẹo bảo mật" + fix 2 mũi tên lạ ở thanh tab | [2026-07-10-fe-settings-bo-meo-bao-mat-fix-mui-ten-tab.md](2026-07-10-fe-settings-bo-meo-bao-mat-fix-mui-ten-tab.md) |
| 2026-07-10 | fullstack | Tải file bảo mật + mở link Canva mới nhất + fix ADMIN sửa bài giảng bị đá về dashboard | [2026-07-10-fullstack-secure-download-canva-open-admin-edit-fix.md](2026-07-10-fullstack-secure-download-canva-open-admin-edit-fix.md) |
| 2026-07-11 | backend | Xóa WebConfig (static mount `/uploads/**`) + gitignore thư mục uploads | [2026-07-11-backend-xoa-webconfig-uploads-gitignore.md](2026-07-11-backend-xoa-webconfig-uploads-gitignore.md) |
| 2026-07-11 | database | Seed LessonFile: bỏ file vật lý "ma", thay bằng link Canva demo | [2026-07-11-db-seed-lessonfile-canva.md](2026-07-11-db-seed-lessonfile-canva.md) |
| 2026-07-12 | frontend | Quét lỗi trùng màu dark mode toàn FE + làm lại Dashboard Giáo viên | [2026-07-12-frontend-dark-mode-teacher-dashboard.md](2026-07-12-frontend-dark-mode-teacher-dashboard.md) |
| 2026-07-14 | frontend | Làm lại trang chủ quảng cáo (landing) + tạm ẩn Ma trận quyền | [2026-07-14-frontend-landing-redesign.md](2026-07-14-frontend-landing-redesign.md) |
| 2026-07-15 | fullstack | Trang "Hồ sơ của tôi" tách khỏi Cài đặt + Việt hóa quyền chi tiết | [2026-07-15-fullstack-profile-page.md](2026-07-15-fullstack-profile-page.md) |
| 2026-07-17 | frontend | Landing bớt "AI-generated look" | [2026-07-17-fe-landing-deai.md](2026-07-17-fe-landing-deai.md) |
| 2026-07-17 | frontend | Gộp sửa liên hệ vào trang Hồ sơ, Cài đặt bỏ tab Hồ sơ | [2026-07-17-fe-profile-inline-edit.md](2026-07-17-fe-profile-inline-edit.md) |
| 2026-07-18 | frontend | Bỏ icon emoji, thay bằng chữ ở khu vực Bài giảng & Nhóm môn học | [2026-07-18-frontend-lesson-subject-icon-to-text.md](2026-07-18-frontend-lesson-subject-icon-to-text.md) |
| 2026-07-20 | frontend | Tạm ẩn phần tích hợp AI (chỉ ẩn ở FE, backend nguyên vẹn) | [2026-07-20-fe-hide-ai-integration.md](2026-07-20-fe-hide-ai-integration.md) |
| 2026-07-21 | backend | ⭐ Chuẩn hóa cấu hình deploy — bỏ hard-code DB url & CORS origin | [2026-07-21-be-deploy-config-env.md](2026-07-21-be-deploy-config-env.md) |
| 2026-07-21 | backend | TeacherController — bỏ neo role, chuyển sang phân quyền theo permission | [2026-07-21-be-teacher-controller-rbac.md](2026-07-21-be-teacher-controller-rbac.md) |
| 2026-07-21 | frontend | Route 404 catch-all dùng chung | [2026-07-21-fe-route-404-catchall.md](2026-07-21-fe-route-404-catchall.md) |
| 2026-07-25 | backend | Cấu hình gửi mail — secret local qua `mail-local.properties` | [2026-07-25-be-mail-local-config.md](2026-07-25-be-mail-local-config.md) |
| 2026-07-28 | backend | Chống dội mail ở luồng Quên mật khẩu (throttle theo tài khoản) | [2026-07-28-backend-chong-doi-mail-quen-mat-khau.md](2026-07-28-backend-chong-doi-mail-quen-mat-khau.md) |

| 2026-08-02 | database | Teacher: đổi EmploymentType sang Cơ hữu/Thỉnh giảng + thêm Kinh nghiệm giảng dạy (V20) | [2026-08-02-database-teacher-emptype-experience.md](2026-08-02-database-teacher-emptype-experience.md) |
| 2026-08-09 | backend | ⭐ Bỏ neo TÊN ROLE ở tầng service — `SecurityUtils.isCentreStaff()` | [2026-08-09-be-bo-neo-ten-role-tang-service.md](2026-08-09-be-bo-neo-ten-role-tang-service.md) |
| 2026-08-13 | fullstack | ⭐ Màn Đặt lại mật khẩu bấm không ra gì + bẫy MockMvc đi vòng qua Security | [2026-08-13-fullstack-quen-mat-khau-nut-cam-lang.md](2026-08-13-fullstack-quen-mat-khau-nut-cam-lang.md) |
| 2026-08-14 | domain | ⭐ Bỏ hẳn cấp 3 — chỉ còn khối 1..9 (V26) + vá lỗi tính sai lương khối 10–12 | [2026-08-14-domain-bo-cap-3-khoi-1-9.md](2026-08-14-domain-bo-cap-3-khoi-1-9.md) |
| 2026-08-17 | database | ⭐ Ràng buộc toàn vẹn khi xóa (trọn bộ Đợt 1–4) — RESTRICT, khóa cứng theo kỳ lương, chốt phòng ngừa, rà soát mồ côi | [2026-08-17-database-rang-buoc-toan-ven-khi-xoa.md](2026-08-17-database-rang-buoc-toan-ven-khi-xoa.md) |
| 2026-08-22 | database | ⭐ Hợp đồng GV giữ lịch sử thay vì ghi đè (V36) — chỉ mục unique có lọc | [2026-08-22-database-hop-dong-luu-lich-su.md](2026-08-22-database-hop-dong-luu-lich-su.md) |
| 2026-08-19 | fullstack | ⭐ Bảng lịch nghỉ (V29) — hết buổi dạy "ma" ngày lễ bị trừ lương + seed toàn bộ luồng Phân công→Lịch dạy→Chấm công→Lương | [2026-08-19-fullstack-lich-nghi-va-seed-dieu-phoi.md](2026-08-19-fullstack-lich-nghi-va-seed-dieu-phoi.md) |
| 2026-08-19 | fullstack | ⭐ Seed đánh giá / phòng học / HĐ dịch vụ / 234 bài giảng · màn Lịch nghỉ (V30) · **GỠ TÁC NHÂN NHÀ TRƯỜNG (V31)** + bẫy `mvn test` xanh giả khi server đang chạy | [2026-08-19-fullstack-du-lieu-bo-sung-va-bo-tac-nhan-truong.md](2026-08-19-fullstack-du-lieu-bo-sung-va-bo-tac-nhan-truong.md) |
| 2026-08-21 | fullstack | ⭐ Viết lại Bảng điều khiển — thống kê bằng SQL aggregate, bộ lọc toàn trang, **chi phí phân bổ về từng buổi** (bẫy `TaughtHours` là SỐ TIẾT) + seed năm học 2025–2026 | [2026-08-21-fullstack-bang-dieu-khien-thong-ke.md](2026-08-21-fullstack-bang-dieu-khien-thong-ke.md) |
| 2026-08-22 | fullstack | Quản lý trường — trạng thái hết hạn suy theo NGÀY, chặn mở lớp/phân công ở trường đã ngừng, thùng rác + khôi phục, chỉ mục UNIQUE tên trường theo chi nhánh (V36) | [2026-08-22-fullstack-quan-ly-truong.md](2026-08-22-fullstack-quan-ly-truong.md) |
| 2026-08-22 | fullstack | ⭐ Bỏ TOÀN BỘ xóa cứng (5 chỗ, kể cả nút Xóa chính của Môn học) + ConfirmDialog & toast dùng chung | [2026-08-22-fullstack-bo-xoa-cung.md](2026-08-22-fullstack-bo-xoa-cung.md) |
| 2026-08-22 | fullstack | Lịch dạy: tìm kiếm hai tầng, lọc trạng thái, tô ngày nghỉ, in + xuất CSV · Lịch nghỉ: thùng rác + cảnh báo trước khi xóa | [2026-08-22-fullstack-lich-day-lich-nghi.md](2026-08-22-fullstack-lich-day-lich-nghi.md) |
| 2026-08-22 | fullstack | Chấm công: thêm cột Buổi dạy (admin thấy ít hơn giáo viên), tìm kiếm + phân trang server, duyệt Nghỉ phép | [2026-08-22-fullstack-cham-cong-tim-kiem.md](2026-08-22-fullstack-cham-cong-tim-kiem.md) |
| 2026-08-22 | fullstack | ⭐ Bảng lương: khép vòng đời phiếu (PAID từng là trạng thái chết), đơn giá ra khỏi code có hiệu lực theo ngày, gộp ~3.000 câu SQL thành 1 JOIN (V38) | [2026-08-22-fullstack-bang-luong-don-gia.md](2026-08-22-fullstack-bang-luong-don-gia.md) |
| 2026-08-22 | fullstack | ⭐⭐ **Dữ liệu lớn (86.745 buổi dạy) + quét lỗ hổng toàn hệ thống** — N+1 làm màn Phân công mất 4,8s, trần 2.100 tham số của SQL Server, 674 lớp xóa được dù đang trong thời khóa biểu · 20 kịch bản phá + 20 câu hỏi kèm câu trả lời | [2026-08-22-fullstack-du-lieu-lon-quet-lo-hong.md](2026-08-22-fullstack-du-lieu-lon-quet-lo-hong.md) |

| 2026-08-24 | fullstack | ⭐⭐ **Ràng buộc xóa Đợt 5 — những chỗ còn sót**: xóa nhóm môn học quét sạch kho bài giảng (đi vòng qua DeleteGuard), phân công vào thùng rác kéo theo cả buổi ĐÃ DẠY, thùng rác + khôi phục cho Kho bài giảng, dọn 5 câu xóa cứng nằm chờ | [2026-08-24-fullstack-rang-buoc-xoa-dot-5.md](2026-08-24-fullstack-rang-buoc-xoa-dot-5.md) |

| 2026-09-05 | fullstack | ⭐ **Hủy phân công kể từ MỘT NGÀY** (V39) — vì sao buộc phải thu hẹp `EndDate` (luật chống trùng lịch đọc cột đó), nhãn "Kết thúc sớm" tính tại chỗ nên không nới `CK_Assignment_Status`, bỏ hàng rào 409 "đang đứng lớp" thay bằng mốc cắt, bỏ hủy không hồi sinh buổi ngày nghỉ · Đơn xin nghỉ do GV tự gửi, duyệt ngay trên chuông | [2026-09-05-fullstack-huy-phan-cong-ngay-hieu-luc.md](2026-09-05-fullstack-huy-phan-cong-ngay-hieu-luc.md) |
| 2026-09-06 | fullstack | Lịch nghỉ: nút "Buổi dạy" chỉ hiện ở dòng thật sự còn việc — **bẫy `ARITHABORT` của JDBC** làm câu JOIN 0,5 giây trong sqlcmd chạy mất 147 giây qua ứng dụng, phải đổi sang hai câu quét phẳng rồi đối chiếu bên Java · hỏng thì hiện hết nút chứ không ẩn hết · trang bám `page-common.css` | [2026-09-06-fullstack-lich-nghi-nut-buoi-day-va-page-common.md](2026-09-06-fullstack-lich-nghi-nut-buoi-day-va-page-common.md) |

Ghi chú không gắn ngày (tổng hợp theo chủ đề):
[fe-dark-mode-va-tien-ich-cai-dat.md](fe-dark-mode-va-tien-ich-cai-dat.md) — FE dark mode & bộ tiện ích trang Cài đặt.

> Mức độ giải thích: luồng quan trọng → giải thích thật kỹ; phần dễ/lặp lại → ghi ngắn gọn.