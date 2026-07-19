# FE: Làm lại landing page bớt "AI-generated look" (2026-07-17)

## Vấn đề

Trang chủ cũ mang đủ dấu hiệu "trang do AI sinh sẵn": hero toàn chữ trên gradient
(không có hình sản phẩm), MỌI section tiêu đề căn giữa + phụ đề mờ lặp đều nhau,
CTA cuối là khối gradient bo góc "Sẵn sàng trải nghiệm?" — cliché nặng nhất.

## Cách tiếp cận (skill ui-ux-pro-max, mức máy)

Chạy `--design-system` cho "education management B2B SaaS operations" → pattern
khớp là **Real-Time / Operations Landing**: Hero (sản phẩm + xem trước thật) →
Dải số liệu → Cách hoạt động → CTA. Nguyên tắc rút ra: sản phẩm vận hành thuyết
phục bằng NỘI DUNG THẬT của chính nó, không bằng khối marketing.

**Điều kiện đã khóa khi dùng skill** (rule dự án): giữ nguyên token màu
Trắng/Cam/Xanh trong main.css — bảng màu teal skill gợi ý bị BỎ QUA; motion mức
subtle; không đổi font.

## Thay đổi chính (chỉ HomePage.vue)

1. **Hero 2 cột**: trái = chữ (headline mới, bỏ span gradient "tích hợp AI");
   phải = **khung xem trước sản phẩm vẽ bằng CSS** — thời khóa biểu tuần với dữ
   liệu ĐÚNG seed demo (Scratch · Lớp 10A1 · Trường THPT Demo · trạng thái
   Đã duyệt/Chờ duyệt). Khung cố ý giữ nền TRẮNG kể cả dark mode vì nó là "ảnh
   chụp sản phẩm", không phải bề mặt trang.
2. **Dải số liệu** thay hàng chips hero: 4 con số là SỰ THẬT đếm được (4 cổng làm
   việc, 30 quyền RBAC, 5 bước quy trình, 0 lần nhập tay lại) — không số quảng cáo.
3. **Tiêu đề section căn TRÁI + kicker cam** (PHÂN HỆ / QUY TRÌNH / CỔNG LÀM VIỆC)
   — phá thế đối xứng căn giữa đều tăm tắp.
4. **CTA cuối**: bỏ khối gradient; thành dải lặng nền surface + viền trái cam,
   nội dung nói đúng sự thật ("trang đăng nhập có sẵn tài khoản demo").
5. Khối master–detail "Tính năng chính" (ý nhóm trưởng) GIỮ NGUYÊN nội dung +
   tương tác; chỉ bỏ 2 hiệu ứng thừa (translateX hover, mũi tên fade) và bỏ
   translateY hover ở card vai trò (đỡ "nhảy nhót").

## Kiểm chứng

ESLint sạch; chụp light 1440 / dark 1440 / mobile 375 — không pageerror, dark mode
nguyên vẹn (mọi màu qua token), mobile không tràn ngang, khung preview responsive.
(Ảnh kiểm tra để ở scratchpad phiên làm việc, không lưu vào claude-context —
muốn xem lại thì chạy vite rồi mở http://localhost:5173/.)

## Bẫy cho người sau

- Dữ liệu trong khung preview hero phải KHỚP seed demo — đổi seed (tên môn/lớp/
  trường) thì sửa `previewRows` theo, đừng để trang chủ chiếu dữ liệu không tồn tại.
- Dải metrics là sự thật hệ thống: thêm/bớt quyền RBAC hay đổi số bước quy trình
  thì cập nhật con số tương ứng.
