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

> Mức độ giải thích: luồng quan trọng → giải thích thật kỹ; phần dễ/lặp lại → ghi ngắn gọn.
