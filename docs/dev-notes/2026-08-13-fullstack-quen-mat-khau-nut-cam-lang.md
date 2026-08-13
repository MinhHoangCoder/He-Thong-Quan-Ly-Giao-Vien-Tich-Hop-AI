# Fullstack: màn Đặt lại mật khẩu bấm không ra gì + IT đi vòng qua Security (2026-08-13)

## Triệu chứng người dùng báo

Giáo viên có thật trong hệ thống, bấm link trong email, gõ mật khẩu mới, bấm nút — **không có
gì xảy ra**. Không request lên backend, không thông báo lỗi, không validate. Dùng lại đúng link
đó lần hai thì mới có thông báo, mà là thông báo token đã dùng.

Kết luận ban đầu rất hợp lý: "backend chưa được nối". Nhưng sai.

## Nguyên nhân: nút bị disable đúng vào lúc cần nó báo lỗi

`ResetPasswordPage.vue`:

```js
const canSubmit = computed(
  () => isStrongPassword(newPassword.value) && newPassword.value === confirm.value,
)
```
```html
<button type="submit" :disabled="loading || !canSubmit">
```

Trong `onSubmit` có sẵn hai câu báo lỗi tử tế — "Mật khẩu chưa đủ mạnh: …" và "Mật khẩu nhập
lại không khớp." Nhưng `onSubmit` **chỉ chạy khi nút bấm được**, mà nút lại bị khóa **chính xác
trong hai trường hợp đó**. Hai câu báo lỗi là code chết đúng vào tình huống chúng sinh ra để xử lý.

Người dùng thấy một cái nút xám câm lặng và không có cách nào biết mình sai ở đâu.

**Bài học chung, không riêng trang này:** khóa nút submit theo kết quả validate thì phải có phản
hồi tức thì thay thế (lỗi inline cạnh ô nhập, hiện ngay khi gõ). Nếu thông báo chỉ nằm trong
handler của submit thì ĐỪNG khóa nút — để nó bấm được rồi báo lỗi, còn hơn im lặng. Ở đây chọn
cách thứ hai vì các câu báo lỗi đã có sẵn.

Backend thì không thiếu gì cả: `/api/v1/auth/reset-password` có `permitAll`, DTO
`ResetPasswordRequest` có `@Pattern` đủ mạnh, controller gọi service, service đổi hash + thu hồi
mọi refresh token. Request chỉ đơn giản là chưa bao giờ rời khỏi trình duyệt.

## Sửa kèm: tách "đã dùng" khỏi "hết hạn"

Trước đây gộp một câu: *"Token đã được dùng hoặc đã hết hạn"*. Hai tình huống này cần **hai hành
động ngược nhau**:

| Tình huống | Ý nghĩa thật | Người dùng nên làm gì |
|---|---|---|
| `usedAt != null` | Token CHỈ bị đánh dấu đã dùng khi đổi mật khẩu **thành công** → mật khẩu đã đổi rồi | Đăng nhập bằng mật khẩu MỚI |
| hết hạn | Hết 30 phút, HOẶC bị link mới hơn đẩy chết (`expireActiveTokens` khi bấm Quên mật khẩu lần hai) | Xin lại link, dùng email mới nhất |

Gộp một câu thì không ai biết mình đang ở ô nào. Tách ra không lộ thêm thông tin: người đang cầm
token thì đã biết token đó có thật.

## Bẫy khi viết integration test: MockMvc đi VÒNG qua Spring Security

Bản đầu của `PasswordResetFlowIT` dựng MockMvc thế này:

```java
MockMvcBuilders.webAppContextSetup(context).build();   // SAI
```

Cách này chỉ dựng tầng MVC. **Chuỗi filter bảo mật không được gắn vào**, nên request không đi qua
`SecurityConfig` chút nào. Đo thực tế: gỡ hẳn `/api/v1/auth/reset-password` khỏi danh sách
`permitAll` rồi chạy lại — **cả 4 test vẫn xanh**. Test viết ra để chứng minh endpoint gọi được
khi chưa đăng nhập, mà lại là thứ duy nhất nó không kiểm được.

Cách đúng, không cần thêm `spring-security-test` vào pom dùng chung:

```java
@Autowired private Filter springSecurityFilterChain;   // bean FilterChainProxy có sẵn

MockMvcBuilders.webAppContextSetup(context)
        .addFilters(springSecurityFilterChain)
        .build();
```

Đo lại sau khi sửa: gỡ `permitAll` ra thì cả 4 test đỏ với **401** — đúng như mong đợi.

### Hệ quả: RateLimitingFilter nằm trong chuỗi đó

`SecurityConfig` gắn nó bằng `addFilterBefore`, giới hạn 5 lượt/phút/IP cho **mỗi đường dẫn**, mà
cả lớp test gọi vừa đúng 5 lượt vào `/reset-password` — để chung IP là lượt cuối ăn 429 và test đỏ
vô cớ.

Đừng "giải quyết" bằng cách `@MockitoBean` bộ lọc đó: mock của một `Filter` **không gọi
`chain.doFilter`**, nên nó chặn đứng mọi request thay vì cho qua. Cách đúng là giả IP riêng cho
từng lượt:

```java
private static RequestPostProcessor fromIp(String ip) {
    return req -> { req.setRemoteAddr(ip); return req; };
}
```

## Bốn thứ IT này khóa lại

1. Endpoint gọi được khi CHƯA đăng nhập (`permitAll` thật sự có hiệu lực).
2. Mật khẩu mới **thực sự xuống đĩa** — service không gọi `save()`, dựa vào dirty checking của
   JPA, sai transaction là âm thầm không lưu gì mà vẫn trả 200.
3. Validator của DTO thật sự bắn với mật khẩu yếu, **và phiếu không bị tiêu mất** khi gõ hụt.
4. Dùng lại link cũ bị chặn, đúng thông báo.

## Kiểm chứng

`mvnw verify` với Docker: **13/13 integration + 171 unit pass** trên SQL Server thật.
Mọi kết luận ở trên đều đo bằng cách phá rồi chạy lại, không phải đọc code rồi suy.
