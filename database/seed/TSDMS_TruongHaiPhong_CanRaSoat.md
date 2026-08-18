# Trường TH/THCS Hải Phòng — danh sách CẦN RÀ SOÁT

Sinh kèm `TSDMS_Seed_TruongHaiPhong.sql`. Tổng **30 trường** (15 tiểu học, 15 THCS).

## Đọc trước khi dùng

Đây là các trường học **có thật**, không phải dữ liệu bịa như hồ sơ giáo viên.
Danh sách này **không** trích từ danh bạ chính thức của Sở/Phòng GD&ĐT Hải Phòng.
Nó được dựng từ hai nguồn:

1. **Quy ước đặt tên phổ biến nhất ở Việt Nam** — trường mang tên phường nơi nó
   đóng ("Trường Tiểu học Đằng Hải" ở phường Đằng Hải cũ). Đây là các dòng đánh
   dấu `cao`.
2. **Hiểu biết chung về các trường mang tên danh nhân** — các dòng `trung bình`.
   Nhiều khả năng trường có thật ở Hải Phòng, nhưng **không chắc thuộc đúng phường**.

Hai điều danh sách này **không** bảo đảm:

- **Không chắc đã đầy đủ.** Yêu cầu ban đầu là "toàn bộ" trường trong 5 phường;
  đây chỉ là phần dựng được một cách có căn cứ.
- **Có thể sai phường**, hoặc trường đã sáp nhập/đổi tên sau cải cách hành chính
  01/7/2025.

**Việc cần làm:** đối chiếu với danh bạ của Phòng GD&ĐT rồi sửa lại trong file
seed, hoặc sửa thẳng trên giao diện Quản lý Trường.

## Danh sách

| # | Tên trường | Cấp | Phường | Độ tin cậy | Trạng thái HĐ | Số lớp |
|---|---|---|---|---|---|---|
| 1 | TH Quán Toan | TH | Hồng Bàng | cao | EXPIRED | 0 |
| 2 | TH Hùng Vương | TH | Hồng Bàng | cao | INACTIVE | 0 |
| 3 | TH Thượng Lý | TH | Hồng Bàng | cao | INACTIVE | 0 |
| 4 | THCS Quán Toan | THCS | Hồng Bàng | cao | ACTIVE | 8 |
| 5 | THCS Hùng Vương | THCS | Hồng Bàng | cao | ACTIVE | 11 |
| 6 | THCS Thượng Lý | THCS | Hồng Bàng | cao | ACTIVE | 9 |
| 7 | TH Dư Hàng | TH | Lê Chân | cao | ACTIVE | 13 |
| 8 | TH Lê Văn Tám | TH | Lê Chân | cao | EXPIRED | 0 |
| 9 | TH Vĩnh Niệm | TH | Lê Chân | cao | INACTIVE | 0 |
| 10 | THCS Dư Hàng Kênh | THCS | Lê Chân | cao | INACTIVE | 0 |
| 11 | THCS Vĩnh Niệm | THCS | Lê Chân | cao | ACTIVE | 11 |
| 12 | THCS Nghĩa Xá | THCS | Lê Chân | cao | INACTIVE | 0 |
| 13 | TH Lạc Viên | TH | Ngô Quyền | cao | EXPIRED | 0 |
| 14 | TH Đông Khê | TH | Ngô Quyền | cao | ACTIVE | 13 |
| 15 | TH Đằng Giang | TH | Ngô Quyền | cao | ACTIVE | 11 |
| 16 | THCS Chu Văn An | THCS | Ngô Quyền | cao | ACTIVE | 11 |
| 17 | THCS Lạc Viên | THCS | Ngô Quyền | cao | ACTIVE | 9 |
| 18 | THCS Đông Khê | THCS | Ngô Quyền | cao | EXPIRED | 0 |
| 19 | TH Đằng Hải | TH | Hải An | cao | INACTIVE | 0 |
| 20 | TH Cát Bi | TH | Hải An | cao | ACTIVE | 12 |
| 21 | TH Tràng Cát | TH | Hải An | cao | ACTIVE | 12 |
| 22 | THCS Đằng Hải | THCS | Hải An | cao | ACTIVE | 9 |
| 23 | THCS Cát Bi | THCS | Hải An | cao | INACTIVE | 0 |
| 24 | THCS Nam Hải | THCS | Hải An | cao | ACTIVE | 11 |
| 25 | TH Quán Trữ | TH | Kiến An | cao | ACTIVE | 12 |
| 26 | TH Đồng Hòa | TH | Kiến An | cao | ACTIVE | 13 |
| 27 | TH Nguyễn Công Hòa | TH | Kiến An | cao | ACTIVE | 13 |
| 28 | THCS Quán Trữ | THCS | Kiến An | cao | INACTIVE | 0 |
| 29 | THCS Đồng Hòa | THCS | Kiến An | cao | ACTIVE | 9 |
| 30 | THCS Trần Thành Ngọ | THCS | Kiến An | cao | ACTIVE | 11 |

## 0 dòng cần soi kỹ nhất

Các trường đặt theo tên danh nhân — tôi không chắc chúng thuộc đúng phường đã ghi:


