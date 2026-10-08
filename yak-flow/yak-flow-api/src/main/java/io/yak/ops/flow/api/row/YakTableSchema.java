package io.yak.ops.flow.api.row;

import java.util.List;
import java.util.Objects;

/**
 * 描述 Source 与 Sink 之间共享的逻辑表结构，主键名称保持数据库原始大小写语义。
 *
 * @param columns 按行字段顺序排列的列定义
 * @param primaryKeys 主键列名称；无主键时为空列表
 * @author weifuwan
 * @since 2026-09-27
 */
public record YakTableSchema(List<YakColumn> columns, List<String> primaryKeys) {

    public YakTableSchema {
        Objects.requireNonNull(columns, "columns must not be null");
        Objects.requireNonNull(primaryKeys, "primaryKeys must not be null");
        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
    }

    /**
     * 返回行协议的字段数量。
     *
     * @return 字段数量
     */
    public int columnCount() {
        return columns.size();
    }

    /**
     * 按 YakRow 中的字段下标读取列定义。
     *
     * @param index 字段下标
     * @return 列定义
     */
    public YakColumn column(int index) {
        return columns.get(index);
    }
}
