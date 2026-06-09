# Dev Note — Bảo mật: IDOR, Rate limit & Row-Level Security

> Ngày 2026-06-09 · Nhánh `feature/auth-jwt`.
> Trả lời câu hỏi: *"Đồ án có nên làm rate limit / RLS / chống IDOR ngay không, hay để khâu phát triển thật?"*

## 0. TL;DR — quyết định & lý do

Ba thứ này **không cùng một loại**, nên cách xử lý cũng khác nhau:

| Hạng mục | Bản chất | Quyết định | Trạng thái |
|---|---|---|---|
| **IDOR / phân quyền theo sở hữu** | Tính **đúng đắn** của phân quyền (OWASP #1) | Làm nền móng **ngay** | ✅ Đã thêm hạ tầng + mẫu chuẩn |
| **Rate limit (login, forgot-password)** | Gia cố chống brute-force | Làm **bản tối thiểu** ngay | ✅ Đã thêm filter token-bucket |
| **Rate limit toàn hệ thống / DDoS** | Hạ tầng | Để sau | ⏳ Hướng phát triển |
| **Row-Level Security (RLS)** | Phòng thủ nhiều lớp ở tầng DB | Để sau | ⏳ Hướng phát triển (đã giải thích) |

Nguyên tắc chung: **thứ gì là "phân quyền/đúng đắn" → làm ngay; thứ gì là "gia cố hạ tầng / phòng thủ trùng lặp" → bản tối thiểu hoặc ghi hướng phát triển.**

---

## 1. IDOR — Insecure Direct Object Reference

### 1.1. Là gì?
Người dùng đổi `id` trên URL để truy cập dữ liệu **không thuộc về mình**. Ví dụ giáo viên
A gọi `GET /api/v1/teachers/5` và xem được hồ sơ của giáo viên khác. Đây là lỗi **OWASP #1
— Broken Access Control**, phổ biến nhất và **dễ bị soi nhất** khi phản biện (chỉ cần sửa số trên URL).

Điểm cốt lõi: **chống IDOR chính là "phân quyền của bạn chạy đúng"** — nó là tính đúng đắn,
không phải lớp bảo mật phụ. Chỉ kiểm tra "đã đăng nhập chưa" (`authenticated`) là **chưa đủ**;
phải kiểm tra thêm "người này có **quyền trên đúng bản ghi đó** không".

### 1.2. Hiện trạng trong code (quan trọng)
Tại thời điểm note này, hệ thống **chưa có endpoint nào bị IDOR**, vì chưa có endpoint nào
nhận `id` rồi trả về dữ liệu sở hữu riêng. Mới chỉ có:
- `/api/v1/auth/*` — xác thực, không lấy theo id.
- `/api/v1/branches` (GET) — danh sách **dùng chung**, mọi user đăng nhập đều được xem để dựng
  dropdown → **không phải IDOR**.

Vậy nên ở bước này **không "vá" lỗi không tồn tại**, mà dựng **nền móng phòng ngừa** để các
endpoint `/teachers/{id}`, `/schools/{id}`, báo cáo… sắp tới **an toàn ngay từ đầu**.

### 1.3. Nền móng đã thêm
JWT vốn đã mang sẵn claim `uid` (id của `AppUser`), nhưng filter trước đây **không lấy ra dùng**.
Đã sửa để đưa `userId` vào `SecurityContext`:

- [`security/AuthPrincipal.java`](../../backend/src/main/java/com/kdc/tsdms/security/AuthPrincipal.java)
  — `record(userId, username)` làm "principal". `getName()` vẫn trả `username` nên các chỗ cũ
  (tham số `Principal` ở controller, `authentication.getName()`) **không đổi**.
- [`security/JwtAuthenticationFilter.java`](../../backend/src/main/java/com/kdc/tsdms/security/JwtAuthenticationFilter.java)
  — đọc claim `uid` → đặt `AuthPrincipal` làm principal.
- [`security/SecurityUtils.java`](../../backend/src/main/java/com/kdc/tsdms/security/SecurityUtils.java)
  — helper `currentUserId()`, `currentUsername()`, `hasRole("ADMIN")` lấy từ `SecurityContext`,
  **không phải hỏi DB**.

### 1.4. MẪU CHUẨN khi viết endpoint lấy theo id (áp dụng từ giờ)
```java
// GET /api/v1/teachers/{id}
Teacher t = teacherRepo.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy"));

boolean isOwner = t.getAppUserId().equals(SecurityUtils.currentUserId());
boolean isStaff = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("EMPLOYEE");
if (!isOwner && !isStaff) {
    throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem hồ sơ này");
}
```
Hai cách phân quyền bổ trợ nhau, **nên dùng đồng thời**:
1. **Tầng URL** (`SecurityConfig`): chặn theo vai trò thô (vd `/admin/**` chỉ ADMIN).
2. **Tầng nghiệp vụ** (service, như trên): chặn theo **sở hữu** từng bản ghi → đây mới là phần chống IDOR.

> Mẹo phụ: với GV/trường, ưu tiên endpoint kiểu `/teachers/me` (suy ra id từ token) thay vì
> `/teachers/{id}` — không lộ id ra ngoài thì không có gì để "đoán".

---

## 2. Rate limit (đã làm bản tối thiểu)

### 2.1. Mục tiêu
Chống **dò mật khẩu (brute-force)** ở `/login` và **spam gửi mail / dò email** ở
`/forgot-password`. Đây là điểm yếu kinh điển của mọi hệ thống có đăng nhập.

### 2.2. Đã thêm gì
Tự viết **token-bucket in-memory** (không thêm thư viện ngoài → build chắc chắn chạy, lại dễ
giải thích trong báo cáo):

- [`security/TokenBucket.java`](../../backend/src/main/java/com/kdc/tsdms/security/TokenBucket.java)
  — mỗi khoá giữ một "xô" token, token tự hồi dần; mỗi request tốn 1 token, hết → chặn.
- [`security/RateLimitingFilter.java`](../../backend/src/main/java/com/kdc/tsdms/security/RateLimitingFilter.java)
  — `OncePerRequestFilter`, khoá = `path + IP`, chỉ áp dụng cho **POST** tới `/login` &
  `/forgot-password`. Vượt ngưỡng → **HTTP 429** kèm header `Retry-After` và JSON đúng dạng
  `ErrorResponse {status, error, message}`.
- [`security/RateLimitProperties.java`](../../backend/src/main/java/com/kdc/tsdms/security/RateLimitProperties.java)
  + `application.yaml` (`tsdms.rate-limit.*`):
  ```yaml
  tsdms:
    rate-limit:
      enabled: true
      capacity: 5          # tối đa 5 lần thử dồn dập / IP / endpoint
      refill-period: 1m    # hồi đầy lại sau 1 phút → ~5 request/phút/IP
  ```
- [`security/SecurityConfig.java`](../../backend/src/main/java/com/kdc/tsdms/security/SecurityConfig.java)
  — cắm filter **trước** `JwtAuthenticationFilter` để chặn ngay từ sớm.

### 2.3. Vì sao token-bucket, không phải "đếm cửa sổ cố định"?
Đếm cửa sổ cố định (vd "tối đa 5 request mỗi phút") có **lỗ hổng biên**: 5 request lúc 0:59 +
5 request lúc 1:00 = 10 request trong 2 giây mà vẫn "hợp lệ". Token-bucket hồi token liên tục
nên mượt hơn, vẫn cho phép burst nhỏ nhưng chặn được spam kéo dài.

### 2.4. Hạn chế (đã biết — để nâng cấp sau)
- Trạng thái nằm trong **RAM của 1 node** → chạy nhiều node thì mỗi node đếm riêng.
  Production nên dùng **Redis** (hoặc **Bucket4j + Redis**) để chia sẻ.
- IP lấy từ `X-Forwarded-For` **có thể bị giả mạo** nếu app không đứng sau reverse-proxy tin cậy.
  Khi deploy thật (sau Nginx/Cloudflare), chỉ tin header này từ proxy của mình.

---

## 3. Row-Level Security (RLS) — vì sao ĐỂ SAU

### 3.1. Là gì?
RLS là cơ chế ở **tầng database** (SQL Server hỗ trợ qua `CREATE SECURITY POLICY`): DB tự động
lọc dòng theo người dùng, kể cả khi câu SQL không có `WHERE`. Là một dạng **phòng thủ nhiều lớp**
(defense-in-depth).

### 3.2. Vì sao chưa làm trong đồ án
- Nếu đã phân quyền đúng ở **tầng service** (mục 1.4 — xử lý IDOR), thì RLS phần lớn **trùng lặp**.
- RLS tỏa sáng ở hệ **multi-tenant SaaS** muốn chặn rò rỉ ngay cả khi tầng app có bug; đổi lại
  **tăng độ phức tạp** đáng kể: policy nằm ở DB, khó debug, khó test, dễ "vô tình lọc mất dữ liệu".
- App này dùng **một tài khoản DB chung** (`sa`/connection pool), không truyền danh tính người
  dùng xuống session DB → muốn RLS phải đặt thêm `SESSION_CONTEXT` mỗi request: công sức lớn,
  lợi ích thấp ở mức đồ án.

### 3.3. Hướng phát triển (ghi vào báo cáo)
Nếu sau này lên multi-tenant thật, cân nhắc RLS như lớp phòng thủ cuối: set `SESSION_CONTEXT`
(branchId / userId) ở đầu mỗi transaction, rồi `CREATE SECURITY POLICY` lọc theo `BranchId`.
Đây là điểm tốt để nêu trong mục **"Hướng phát triển"** — cho thấy biết kỹ thuật tồn tại và biết
**khi nào** mới cần.

---

## 4. Cách kiểm thử nhanh

```powershell
# Chạy backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DB_PASSWORD='<mật khẩu sa>'
cd backend; .\mvnw.cmd spring-boot:run
```

**Rate limit** — gọi /login sai mật khẩu 6 lần liên tiếp, lần thứ 6 phải nhận **429**:
```powershell
1..6 | ForEach-Object {
  $r = try { Invoke-WebRequest -Uri http://localhost:8080/api/v1/auth/login `
        -Method POST -ContentType 'application/json' `
        -Body '{"username":"admin","password":"sai"}' -SkipHttpErrorCheck } catch { $_.Exception.Response }
  "Lần $_ → $($r.StatusCode)"
}
# Kỳ vọng: 5 lần đầu 401 (sai mật khẩu), lần 6 → 429 Too Many Requests
```
> Lưu ý: ngưỡng tính theo **IP**, nên test từ cùng một máy mới thấy bị chặn. Muốn thử lại từ
> đầu thì đợi `refill-period` (1 phút) hoặc tạm `enabled: false`.

**IDOR** — chưa có endpoint by-id để test; nền móng sẽ được kiểm chứng khi viết
`/teachers/{id}` theo mẫu ở mục 1.4 (đăng nhập tài khoản GV này, thử xem hồ sơ GV khác → phải 403).

---

## 5. Tệp đã thêm/sửa

**Thêm mới:** `AuthPrincipal`, `SecurityUtils`, `TokenBucket`, `RateLimitingFilter`, `RateLimitProperties`.
**Sửa:** `JwtAuthenticationFilter` (nạp `uid`), `SecurityConfig` (cắm rate-limit filter), `application.yaml` (`tsdms.rate-limit.*`).

> Biên dịch sạch (`mvnw compile`) và `AuthServiceTest` xanh sau thay đổi.
> Lưu ý workflow: dự án bật **Spotless** (định dạng + CRLF) ở `mvnw compile` — nếu báo lỗi format,
> chạy `.\mvnw.cmd spotless:apply` rồi compile lại.

## 6. Còn lại (PR sau)
- Endpoint hồ sơ GV/trường theo mẫu chống IDOR ở mục 1.4 (ưu tiên kiểu `/me`).
- Cân nhắc thêm rate limit cho `/reset-password` (chống dò token) và `/register`.
- Khi deploy thật: chuyển rate-limit sang Redis; đặt app sau reverse-proxy để lấy IP thật.
