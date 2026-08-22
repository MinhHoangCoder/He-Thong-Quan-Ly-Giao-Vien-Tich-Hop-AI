package com.kdc.tsdms.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Chia một danh sách khóa thành nhiều LÔ trước khi ném vào câu {@code WHERE Id IN (...)}.
 *
 * <p>SQL Server chỉ nhận tối đa <b>2.100 tham số</b> cho một câu lệnh. {@code findAllById} của
 * Spring Data dựng đúng một dấu hỏi cho mỗi khóa, nên gọi nó với danh sách dài hơn ngần ấy sẽ
 * ném {@code SQLGrammarException: The incoming request has too many parameters}.
 *
 * <p>Đây không phải lỗi lý thuyết: màn Lịch dạy gom ô lịch của mọi buổi trong khoảng đang xem;
 * chọn một khoảng vài tháng là chạm 4.749 ô và request nổ 500. Nó cũng không phải thứ lộ ra khi
 * chạy với dữ liệu nhỏ — 1.000 dòng thì vẫn xanh, 3.000 dòng mới hỏng.
 *
 * <p>Trần đặt ở 1.000 chứ không phải 2.100: câu query còn có thể mang thêm điều kiện khác, và
 * chừa khoảng an toàn rẻ hơn nhiều so với việc đi dò lại sau này.
 */
public final class BatchIn {

    /** Số khóa tối đa cho một lô. */
    public static final int LO_TOI_DA = 1000;

    private BatchIn() {}

    /**
     * Gọi {@code truyVan} theo từng lô rồi gộp kết quả.
     *
     * <pre>{@code
     * List<AssignmentSlot> slots = BatchIn.theoLo(slotIds, slotRepo::findAllById);
     * }</pre>
     */
    public static <K, V> List<V> theoLo(Collection<K> keys, Function<List<K>, ? extends Iterable<V>> truyVan) {
        List<V> out = new ArrayList<>();
        if (keys == null || keys.isEmpty()) {
            return out;
        }
        List<K> all = new ArrayList<>(keys);
        for (int i = 0; i < all.size(); i += LO_TOI_DA) {
            List<K> lo = all.subList(i, Math.min(i + LO_TOI_DA, all.size()));
            truyVan.apply(lo).forEach(out::add);
        }
        return out;
    }
}
