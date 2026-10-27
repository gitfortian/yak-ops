package io.yak.ops.business.datamap.crawl;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.enums.datamap.DataMapTableChangeType;
import io.yak.ops.dao.entity.datamap.DataMapColumnEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Compares one observation with the stored baseline of an asset.
 *
 * <p>Columns are matched by name, so a type change is one {@code COLUMN_TYPE_CHANGED} row rather
 * than an added/removed pair. The comparison must run against the baseline read inside the write
 * transaction, before the columns are replaced.
 */
public final class DataMapChangeDetector {

    /** Exact numeric types whose parenthesised form is {@code (precision,scale)}. */
    private static final Pattern SCALED_TYPES =
            Pattern.compile("^(DECIMAL|NUMERIC|NUMBER|DEC|FIXED)$", Pattern.CASE_INSENSITIVE);

    /** Character and binary types whose parenthesised form is a length. */
    private static final Pattern LENGTH_TYPES =
            Pattern.compile("^(N?CHAR|N?VARCHAR|NCHAR|NVARCHAR|BINARY|VARBINARY|RAW)$", Pattern.CASE_INSENSITIVE);

    private DataMapChangeDetector() {}

    /**
     * Detects the changes between the stored baseline and the new observation.
     *
     * @param existing stored asset holding the previous table comment
     * @param baseline stored columns read before replacement
     * @param observed table metadata just read from the datasource
     * @param columns columns just read from the datasource
     * @return detected changes in a stable order; empty on first observation
     */
    public static List<DetectedChange> detect(
            DataMapTableEntity existing,
            List<DataMapColumnEntity> baseline,
            DataSourceCatalogTableVO observed,
            List<DataSourceCatalogColumnVO> columns) {
        List<DetectedChange> changes = new ArrayList<>();
        if (!Objects.equals(existing.getRemarks(), observed.getRemarks())) {
            changes.add(new DetectedChange(
                    DataMapTableChangeType.TABLE_COMMENT_CHANGED,
                    "表注释由「" + text(existing.getRemarks()) + "」变为「" + text(observed.getRemarks()) + "」"));
        }
        Map<String, DataMapColumnEntity> previous = new LinkedHashMap<>();
        for (DataMapColumnEntity column : baseline) previous.put(column.getName(), column);
        Map<String, DataSourceCatalogColumnVO> current = new LinkedHashMap<>();
        for (DataSourceCatalogColumnVO column : columns) current.put(column.getName(), column);

        for (DataSourceCatalogColumnVO column : columns) {
            DataMapColumnEntity before = previous.get(column.getName());
            if (before == null) {
                changes.add(new DetectedChange(
                        DataMapTableChangeType.COLUMN_ADDED,
                        "新增字段 " + column.getName() + "（" + column.getTypeName() + "）"));
                continue;
            }
            if (!Objects.equals(before.getTypeName(), column.getTypeName())
                    || !Objects.equals(before.getSize(), column.getSize())
                    || !Objects.equals(before.getScale(), column.getScale())) {
                changes.add(new DetectedChange(
                        DataMapTableChangeType.COLUMN_TYPE_CHANGED,
                        "字段 " + column.getName() + " 类型由 "
                                + renderType(before.getTypeName(), before.getSize(), before.getScale()) + " 变为 "
                                + renderType(column.getTypeName(), column.getSize(), column.getScale())));
            }
            if (!Objects.equals(before.getNullable(), column.getNullable())) {
                changes.add(new DetectedChange(
                        DataMapTableChangeType.COLUMN_NULLABILITY_CHANGED,
                        "字段 " + column.getName() + " 可空性由 " + nullability(before.getNullable()) + " 变为 "
                                + nullability(column.getNullable())));
            }
            if (!Objects.equals(before.getPrimaryKey(), column.getPrimaryKey())
                    || !Objects.equals(before.getPrimaryKeyPosition(), column.getPrimaryKeyPosition())) {
                changes.add(new DetectedChange(
                        DataMapTableChangeType.PRIMARY_KEY_CHANGED, "字段 " + column.getName() + " 主键属性发生变化"));
            }
        }
        for (DataMapColumnEntity before : baseline) {
            if (!current.containsKey(before.getName())) {
                changes.add(new DetectedChange(
                        DataMapTableChangeType.COLUMN_REMOVED,
                        "删除字段 " + before.getName() + "（" + before.getTypeName() + "）"));
            }
        }
        return changes;
    }

    private static String text(String value) {
        return value == null ? "无" : value;
    }

    /** Renders a native type the way a DBA writes it, matching the field list's display rules. */
    private static String renderType(String typeName, Integer size, Integer scale) {
        String type = typeName == null ? "未知类型" : typeName;
        if (size == null || size <= 0) return type;
        if (SCALED_TYPES.matcher(type).matches()) {
            return scale == null ? type + "(" + size + ")" : type + "(" + size + "," + scale + ")";
        }
        return LENGTH_TYPES.matcher(type).matches() ? type + "(" + size + ")" : type;
    }

    private static String nullability(Boolean nullable) {
        return Boolean.TRUE.equals(nullable) ? "可空" : "非空";
    }

    /**
     * One detected change before it is persisted.
     *
     * @param type closed change type
     * @param detail human-readable description
     */
    public record DetectedChange(DataMapTableChangeType type, String detail) {}
}
