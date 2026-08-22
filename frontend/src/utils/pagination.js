/**
 * Số dòng trên một trang bảng — MỘT con số cho toàn hệ thống.
 *
 * Trước đây mỗi màn tự khai một hằng số riêng (6, 10, 12, 20, thậm chí 1000) nên hai bảng
 * cạnh nhau cuộn khác nhau, và chỗ để 1000 thì thực chất là "tải hết về rồi cắt bằng
 * JavaScript" — với dữ liệu thật thì đó là kéo cả bảng qua đường truyền.
 *
 * Danh sách dạng THẺ (đánh giá của tôi, ô chọn giáo viên trong modal) không dùng hằng số
 * này: chúng xếp theo lưới nên số phần tử mỗi trang bám theo số cột, không phải số dòng.
 */
export const PAGE_SIZE = 10
