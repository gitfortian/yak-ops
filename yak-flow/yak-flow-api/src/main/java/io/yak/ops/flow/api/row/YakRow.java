package io.yak.ops.flow.api.row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * YakFlow 批流统一的行数据载体，通过 RowKind 表达 INSERT、UPDATE 和 DELETE 语义。
 *
 * @param rowKind 当前行的变更语义
 * @param values 按 YakTableSchema 列顺序排列的字段值，允许字段值为 null
 * @author weifuwan
 * @since 2026-09-27
 */
public record YakRow(RowKind rowKind, List<Object> values) {

    public YakRow {
        Objects.requireNonNull(rowKind, "rowKind must not be null");
        Objects.requireNonNull(values, "values must not be null");
        values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * 返回当前行的字段数量。
     *
     * @return 字段数量
     */
    public int arity() {
        return values.size();
    }

    /**
     * 按字段下标读取值。
     *
     * @param index 字段下标
     * @return 字段值，可为 null
     */
    public Object value(int index) {
        return values.get(index);
    }
}
