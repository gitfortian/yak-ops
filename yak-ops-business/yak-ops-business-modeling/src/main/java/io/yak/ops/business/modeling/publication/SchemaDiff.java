package io.yak.ops.business.modeling.publication;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One difference between the current physical table and the logical model contract.
 *
 * @param kind ADD, CHANGE or DROP
 * @param column affected column name as the model spells it
 * @param fromType observed physical type, null for an added column
 * @param toType physical type the model requires, null for a dropped column
 * @param detail human-readable summary rendered in the publish dialog
 */
public record SchemaDiff(Kind kind, String column, String fromType, String toType, String detail) {

    public enum Kind {
        ADD,
        CHANGE,
        DROP
    }

    /**
     * Computes the diff between the model contract and the observed table.
     *
     * <p>Comparison is structural and keyed by case-insensitive column name, matching how the
     * engines resolve unquoted identifiers. Type drift covers the family and the precision pair;
     * comment-only differences are not changes worth an {@code ALTER}. Added columns follow the
     * model order, dropped columns follow the table order, so the statement list is deterministic.
     *
     * @param dialect dialect the observed table was read through
     * @param expected ordered columns the model declares
     * @param observed columns reported by the engine, in ordinal order
     * @return ordered diff, add first then change then drop; empty when the table matches
     */
    public static List<SchemaDiff> diff(
            PhysicalDialect dialect, List<PhysicalColumnSpec> expected, List<DataSourceCatalogColumnVO> observed) {
        Map<String, DataSourceCatalogColumnVO> present = new LinkedHashMap<>();
        for (var column : observed) {
            if (column.getName() != null) {
                present.put(key(column.getName()), column);
            }
        }
        var changes = new ArrayList<SchemaDiff>();
        for (var column : expected) {
            var observedColumn = present.get(key(column.name()));
            if (observedColumn == null) {
                changes.add(new SchemaDiff(Kind.ADD, column.name(), null, null, "新增列 " + column.name()));
                continue;
            }
            var expectedType = PhysicalTypes.of(dialect, column.dataType(), column.lengthPrecision());
            var observedType = PhysicalTypes.observed(observedColumn);
            if (!expectedType.family().equals(observedType.family())
                    || !Objects.equals(expectedType.precision(), observedType.precision())
                    || !Objects.equals(expectedType.scale(), observedType.scale())) {
                changes.add(new SchemaDiff(
                        Kind.CHANGE,
                        column.name(),
                        describe(observedColumn, observedType),
                        expectedType.sqlType(),
                        "列 " + column.name() + " 类型由 " + describe(observedColumn, observedType) + " 变更为 "
                                + expectedType.sqlType()));
            }
        }
        for (var column : observed) {
            var name = column.getName() == null ? null : key(column.getName());
            if (name == null) continue;
            var declared = expected.stream().anyMatch(item -> key(item.name()).equals(name));
            if (!declared) {
                changes.add(new SchemaDiff(
                        Kind.DROP,
                        column.getName(),
                        describe(column, PhysicalTypes.observed(column)),
                        null,
                        "删除列 " + column.getName() + "（数据将丢失）"));
            }
        }
        return List.copyOf(changes);
    }

    /** Whether any change drops data; publishing such a plan needs the explicit acknowledgement. */
    public static boolean dropsData(List<SchemaDiff> changes) {
        return changes.stream().anyMatch(change -> change.kind() == Kind.DROP);
    }

    private static String describe(DataSourceCatalogColumnVO column, PhysicalTypeSpec type) {
        var size = type.precision() == null
                ? column.getTypeName()
                : column.getTypeName() + "(" + type.precision() + (type.scale() == null ? "" : "," + type.scale())
                        + ")";
        return size;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
