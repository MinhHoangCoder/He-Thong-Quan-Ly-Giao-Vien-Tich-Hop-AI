## Mô tả

<!-- Thay đổi này làm gì? Liên quan issue nào? -->

## Loại thay đổi
- [ ] Feature
- [ ] Bugfix
- [ ] Refactor
- [ ] Test
- [ ] Docs / chore

## Phạm vi
- [ ] backend
- [ ] frontend
- [ ] database (migration / seed / schema)

## Checklist

### Chung
- [ ] Build & test pass ở local (`mvn test` / `npm run build`)
- [ ] Đã format code (`spotless:apply` cho backend) — không lẫn thay đổi format vô nghĩa
- [ ] PR nhỏ, tập trung 1 mục đích; diff không kèm file thừa (`target/`, `node_modules/`, `.env`)
- [ ] Đã merge/rebase `master` mới nhất, không còn conflict
- [ ] Không commit secret/khóa/mật khẩu thật

### Backend / quy ước
- [ ] DTO cho request/response (không trả Entity trực tiếp)
- [ ] Không hard-code role/status (dùng Enum/hằng số)
- [ ] Endpoint mới có kiểm tra quyền (`@PreAuthorize`) + chống IDOR (chỉ truy cập dữ liệu thuộc quyền)
- [ ] Có unit test cho luật nghiệp vụ mới (nếu là logic)

### Database (nếu có đổi schema)
- [ ] Migration mới đặt tên `V<n>__<mô_tả>.sql` (không sửa migration đã chạy)
- [ ] Đã "xí số" version migration với nhóm (chống trùng)
- [ ] Cập nhật cả `database/schema/TSDMS_Schema.sql` (bản thiết kế) cho khớp migration
- [ ] Seed demo (`TSDMS_Seed_Demo.sql`) vẫn chạy được sau thay đổi

### Tài liệu
- [ ] Viết dev-note vào `docs/dev-notes/` nếu là tính năng khó/quan trọng
