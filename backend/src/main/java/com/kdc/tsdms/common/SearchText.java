package com.kdc.tsdms.common;

import java.text.Normalizer;

/** Chuẩn hóa chuỗi người dùng gõ vào ô tìm kiếm trước khi ném xuống câu LIKE. */
public final class SearchText {

    private SearchText() {}

    /** Chuỗi rỗng/toàn khoảng trắng -> null, để câu query hiểu là "bỏ qua điều kiện này". */
    public static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Thoát các ký tự đại diện của LIKE bằng ký tự thoát '!' (câu query phải khai {@code ESCAPE
     * '!'}). Không thoát thì người dùng gõ '%' sẽ khớp toàn bộ bảng, gõ '_' khớp mọi ký tự.
     *
     * <p>Thoát dấu '!' TRƯỚC, nếu không thì '!' do chính hàm này chèn vào lại bị thoát lần nữa.
     */
    public static String escapeLike(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
    }
    /**
     * Bỏ dấu tiếng Việt, hạ chữ thường, cắt khoảng trắng — dùng để SO KHỚP trong bộ nhớ.
     *
     * <p>Người dùng gõ "nguyen van an" phải ra "Nguyễn Văn An", gõ "DU HANG" phải ra "Dư Hàng".
     * Không bỏ dấu thì ô tìm kiếm chỉ dùng được cho ai gõ đủ dấu và đúng hoa thường.
     *
     * <p>Khác {@link #escapeLike}: hàm kia chuẩn bị chuỗi cho câu SQL LIKE, hàm này so khớp
     * ngay trong Java khi dữ liệu đã nằm sẵn trong bộ nhớ (Phân công, Lịch dạy).
     *
     * <p>Chữ "đ" phải xử lý riêng: trong Unicode nó KHÔNG phải "d" + dấu phụ nên
     * {@link Normalizer} không tách ra được.
     */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String noMark = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMark.replace('đ', 'd').replace('Đ', 'D').toLowerCase().trim();
    }

    /**
     * {@code normalizedKeyword} có nằm trong bất kỳ mảnh nào không.
     *
     * @param normalizedKeyword từ khóa ĐÃ qua {@link #normalize} — chuẩn hóa sẵn một lần ở
     *     ngoài vòng lặp thay vì lặp lại cho từng dòng
     */
    public static boolean matchesAny(String normalizedKeyword, String... parts) {
        if (normalizedKeyword.isEmpty()) {
            return true;
        }
        for (String p : parts) {
            if (normalize(p).contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }
}
