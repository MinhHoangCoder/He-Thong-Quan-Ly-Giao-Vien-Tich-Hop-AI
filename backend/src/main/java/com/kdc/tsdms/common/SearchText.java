package com.kdc.tsdms.common;

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
}
