package com.kdc.tsdms.dto;

/**
 * Trả lời câu hỏi "lớp này chuyển sang Ngừng được chưa" cho form sửa lớp.
 *
 * <p>Vì sao có DTO riêng thay vì để form tự đếm buổi dạy: luật chặn nằm ở service (và trả 409
 * khi bị gọi thẳng), nên nếu form tự dựng lại câu chữ thì hai nơi sẽ nói hai kiểu về cùng một
 * lý do. Ở đây service soạn sẵn {@code lyDo} — form chỉ việc in ra ngay dưới ô Trạng thái, và
 * chữ hiện trên màn hình luôn khớp chữ backend trả về khi chặn thật.
 *
 * @param soBuoiSapToi số buổi dạy còn hiệu lực từ hôm nay trở đi
 * @param chuyenDuoc {@code false} = còn buổi dạy, form phải khóa nút Lưu
 * @param lyDo câu tiếng Việt hiện dưới ô Trạng thái; rỗng khi chuyển được
 */
public record SchoolClassDeactivateCheck(long soBuoiSapToi, boolean chuyenDuoc, String lyDo) {}
