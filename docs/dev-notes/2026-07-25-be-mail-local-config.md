# BE: Cấu hình gửi mail — secret local qua `mail-local.properties` (2026-07-25)

Ghi lại cho commit `e4c2ff7` "Config/mail from default". Nối tiếp
[2026-07-21-be-deploy-config-env.md](2026-07-21-be-deploy-config-env.md) (phần biến môi trường khi deploy).

## Vấn đề

Luồng "quên mật khẩu" gửi mail qua Gmail SMTP, cần `MAIL_USERNAME` + `MAIL_PASSWORD`
(App Password). Trước commit này chỉ có 2 đường:

- **Đặt biến môi trường mỗi phiên** — mở PowerShell mới là mất, phải `$env:MAIL_PASSWORD=...`
  lại trước mỗi lần `mvnw spring-boot:run`. Quên = mail không gửi, luồng reset im lặng hỏng.
- **Ghi thẳng vào `application.yaml`** — App Password vào git, lộ vĩnh viễn trong lịch sử
  commit (xóa ở commit sau cũng vẫn còn ở commit cũ).

Thêm một bẫy riêng của Gmail: địa chỉ hiển thị người gửi mặc định là `no-reply@tsdms.local`,
nhưng **Gmail chỉ cho gửi dưới danh nghĩa chính tài khoản đã xác thực** → SMTP trả lỗi
`553 Mail from must equal authorized user` (hoặc lặng lẽ đổi lại người gửi), tùy tài khoản.

## Cách làm

**1. Nạp file secret nằm ngoài git** — `application.yaml`:

```yaml
spring:
  config:
    import: optional:classpath:mail-local.properties
```

- `optional:` = **thiếu file cũng khởi động bình thường**. Đây là điểm mấu chốt: thành viên
  khác clone về không có file, app vẫn chạy — không ai bị chặn vì thiếu secret của mình.
  Bỏ chữ `optional:` thì thiếu file = app **không boot được** (`ConfigDataLocationNotFoundException`).
- `classpath:` → file phải đặt tại `backend/src/main/resources/mail-local.properties`
  (cùng chỗ `application.yaml`).

**2. Chặn file khỏi git** — `.gitignore` thêm dòng `mail-local.properties`. Pattern không có
dấu `/` nên khớp ở mọi thư mục, không phụ thuộc chỗ đặt file.

**3. Mặc định `from` khớp tài khoản gửi** — `application.yaml`:

```yaml
tsdms:
  mail:
    from: ${MAIL_FROM:KDC EduOps <kdceduopsai@gmail.com>}
```

Phần trong `<...>` **phải trùng `MAIL_USERNAME`**, nếu không dính đúng bẫy Gmail ở trên.
Đổi sang tài khoản khác thì phải đổi **cả hai** (`MAIL_USERNAME` và `MAIL_FROM`), không chỉ một.

## Máy mới cần làm gì

Tạo `backend/src/main/resources/mail-local.properties` (file này KHÔNG có trong repo):

```properties
MAIL_USERNAME=<gmail-phu-cua-ban>@gmail.com
MAIL_PASSWORD=<16-ky-tu-app-password-khong-co-dau-cach>
```

App Password lấy tại: Google Account → Security → 2-Step Verification (phải bật trước) →
App passwords. Đặt 1 lần, không cần set env mỗi phiên nữa.

**Không tạo file cũng chạy được**: `EmailService.sendPasswordReset()` bọc `mailSender.send()`
trong try/catch, gửi lỗi chỉ log `WARN` **kèm nguyên link reset** — dev vẫn test được luồng
đặt lại mật khẩu bằng cách copy link từ console, và API `/forgot-password` không trả 500.

## Thứ tự ưu tiên khi deploy (đừng nhầm)

Biến môi trường của OS **thắng** file config được import. Nên trên Railway cứ đặt
`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM` ở dashboard như cũ — kể cả nếu file
`mail-local.properties` có lọt vào ảnh build thì env vẫn đè lên. File chỉ là tiện ích
cho máy dev, không phải kênh cấu hình production.

## Kiểm chứng

`mvnw spotless:apply compile` PASS. Máy không có `mail-local.properties` khởi động bình
thường (nhờ `optional:`) — đúng kịch bản của thành viên khác trong nhóm và của CI.
