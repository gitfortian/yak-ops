package io.yak.ops.business.modeling.publication;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Compares an existing physical table with the contract a model declares.
 *
 * <p>Used by create-or-verify publishing and by reconciliation. Verification never modifies the
 * table: it returns the first difference so the publication can be recorded as failed with a reason
 * the user can act on.
 *
 * <p>Comparison is intentionally structural rather than textual. Engine type names differ
 * ({@code varchar} vs {@code varchar2}, {@code bigint} vs {@code number(19)}), so types are compared
 * through {@link PhysicalTypes} canonical shapes, and a driver that reports no size for a string
 * column is compared on family alone instead of producing a false mismatch.
 */
public final class PhysicalTableVerifier {

    private PhysicalTableVerifier() {}

    /**
     * Compares one model contract with the observed columns.
     *
     * @param dialect dialect of the datasource the table was read through
     * @param expected ordered columns the model declares
     * @param observed columns reported by the engine, in ordinal order
     * @return the first difference, or empty when the table already matches
     */
    public static Optional<String> verify(
            PhysicalDialect dialect, List<PhysicalColumnSpec> expected, List<DataSourceCatalogColumnVO> observed) {
        if (expected.size() != observed.size()) {
            return Optional.of("物理表有 " + observed.size() + " 个字段，模型有 " + expected.size() + " 个字段");
        }
        for (var index = 0; index < expected.size(); index++) {
            var mismatch = compareColumn(dialect, expected.get(index), observed.get(index));
            if (mismatch.isPresent()) return mismatch;
        }
        return Optional.empty();
    }

    private static Optional<String> compareColumn(
            PhysicalDialect dialect, PhysicalColumnSpec expected, DataSourceCatalogColumnVO observed) {
        var label = "字段 " + expected.name();
        if (observed.getName() == null
                || !observed.getName()
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .equals(expected.name().trim().toLowerCase(Locale.ROOT))) {
            return Optional.of(label + "：物理列名为 " + observed.getName());
        }
        var expectedType = PhysicalTypes.of(dialect, expected.dataType(), expected.lengthPrecision());
        var observedType = PhysicalTypes.observed(observed);
        if (!expectedType.family().equals(observedType.family())) {
            return Optional.of(label + "：类型为 " + observed.getTypeName() + "，模型要求 " + expectedType.family());
        }
        if (expectedType.precision() != null
                && observedType.precision() != null
                && !expectedType.precision().equals(observedType.precision())) {
            return Optional.of(label + "：精度为 " + observedType.precision() + "，模型要求 " + expectedType.precision());
        }
        if (expectedType.scale() != null
                && observedType.scale() != null
                && !expectedType.scale().equals(observedType.scale())) {
            return Optional.of(label + "：小数位为 " + observedType.scale() + "，模型要求 " + expectedType.scale());
        }
        var expectedNotNull = expected.notNull() || expected.keyPosition() != null;
        if (expectedNotNull && Boolean.TRUE.equals(observed.getNullable())) {
            return Optional.of(label + "：允许为空，模型要求非空");
        }
        if (!expectedNotNull && Boolean.FALSE.equals(observed.getNullable())) {
            return Optional.of(label + "：不允许为空，模型未要求非空");
        }
        if (!Objects.equals(expected.keyPosition(), observed.getPrimaryKeyPosition())) {
            return Optional.of(
                    label + "：主键位置为 " + observed.getPrimaryKeyPosition() + "，模型要求 " + expected.keyPosition());
        }
        var expectedComment =
                expected.comment() == null ? "" : expected.comment().trim();
        var observedComment =
                observed.getRemarks() == null ? "" : observed.getRemarks().trim();
        if (!expectedComment.equals(observedComment)) {
            return Optional.of(label + "：注释为「" + observedComment + "」，模型为「" + expectedComment + "」");
        }
        return Optional.empty();
    }
}
