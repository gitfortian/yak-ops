package io.yak.ops.business.modeling.publication;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.enums.modeling.standard.DataType;
import java.util.Locale;

/**
 * Logical to physical type translation and the canonical shape used to compare a model with an
 * existing physical table.
 *
 * <p>Every rejection here is a refusal to guess. A {@code STRING} without a length, a {@code DECIMAL}
 * without a complete precision pair, or a type this build cannot express becomes an error the user
 * sees before anything reaches the engine, never a silently defaulted column.
 */
public final class PhysicalTypes {

    /** Maximum precision shared by the three supported engines. */
    public static final int MAX_PRECISION = 38;

    private PhysicalTypes() {}

    /**
     * Renders the physical type of one logical field.
     *
     * @param dialect target dialect
     * @param dataType logical type
     * @param lengthPrecision declared length or {@code precision,scale}; null when the type needs none
     * @return canonical physical shape
     * @throws IllegalArgumentException when the logical type cannot be published as declared
     */
    public static PhysicalTypeSpec of(PhysicalDialect dialect, DataType dataType, String lengthPrecision) {
        if (dataType == null) throw new IllegalArgumentException("字段缺少逻辑类型");
        return switch (dataType) {
            case STRING -> stringType(dialect, lengthPrecision);
            case BIGINT -> bigintType(dialect);
            case DECIMAL -> decimalType(dialect, lengthPrecision);
            case DATE -> new PhysicalTypeSpec(PhysicalTypeSpec.DATE, "date", null, null);
            case TIMESTAMP ->
                new PhysicalTypeSpec(
                        PhysicalTypeSpec.TIMESTAMP,
                        dialect == PhysicalDialect.MYSQL || dialect == PhysicalDialect.DORIS
                                ? "datetime(3)"
                                : "timestamp",
                        null,
                        null);
        };
    }

    /**
     * Canonicalizes an observed physical column.
     *
     * <p>No dialect is passed on purpose: an observed type is compared against the shape the same
     * dialect would generate, and Oracle cannot distinguish {@code BIGINT} from {@code DECIMAL(19,0)}
     * once created. Both therefore canonicalize to the numeric shape and compare equal.
     *
     * @param column observed column metadata
     * @return canonical shape, with {@code OTHER:<type>} when the type is outside the five families
     */
    public static PhysicalTypeSpec observed(DataSourceCatalogColumnVO column) {
        var typeName =
                column.getTypeName() == null ? "" : column.getTypeName().trim().toLowerCase(Locale.ROOT);
        var size = column.getSize();
        var scale = column.getScale();
        if (typeName.contains("timestamp") || typeName.contains("datetime")) {
            return new PhysicalTypeSpec(PhysicalTypeSpec.TIMESTAMP, typeName, null, null);
        }
        if (typeName.equals("date")) {
            return new PhysicalTypeSpec(PhysicalTypeSpec.DATE, typeName, null, null);
        }
        if (typeName.equals("bigint") || typeName.equals("int8") || typeName.endsWith("bigint")) {
            return new PhysicalTypeSpec(PhysicalTypeSpec.BIGINT, typeName, null, null);
        }
        if (typeName.contains("char") || typeName.contains("text") || typeName.contains("clob")) {
            return new PhysicalTypeSpec(PhysicalTypeSpec.STRING, typeName, size, null);
        }
        if (typeName.contains("number") || typeName.contains("numeric") || typeName.contains("decimal")) {
            return new PhysicalTypeSpec(PhysicalTypeSpec.DECIMAL, typeName, size, scale);
        }
        return new PhysicalTypeSpec("OTHER:" + typeName, typeName, size, scale);
    }

    private static PhysicalTypeSpec stringType(PhysicalDialect dialect, String lengthPrecision) {
        var length = parsePositive(lengthPrecision, "STRING 字段必须填写长度");
        if (length > dialect.maxStringLength()) {
            throw new IllegalArgumentException(
                    "STRING 长度 " + length + " 超出 " + dialect + " 的上限 " + dialect.maxStringLength());
        }
        var sqlType = dialect == PhysicalDialect.ORACLE ? "varchar2(" + length + ")" : "varchar(" + length + ")";
        return new PhysicalTypeSpec(PhysicalTypeSpec.STRING, sqlType, length, null);
    }

    private static PhysicalTypeSpec bigintType(PhysicalDialect dialect) {
        // Oracle has no BIGINT; NUMBER(19) is the exact 64-bit integer range.
        return dialect == PhysicalDialect.ORACLE
                ? new PhysicalTypeSpec(PhysicalTypeSpec.DECIMAL, "number(19)", 19, 0)
                : new PhysicalTypeSpec(PhysicalTypeSpec.BIGINT, "bigint", null, null);
    }

    private static PhysicalTypeSpec decimalType(PhysicalDialect dialect, String lengthPrecision) {
        var parts = splitPrecision(lengthPrecision);
        if (parts == null) {
            throw new IllegalArgumentException("DECIMAL 字段必须填写完整的精度与小数位，例如 18,2");
        }
        var precision = parts[0];
        var scale = parts[1];
        if (precision < 1 || precision > MAX_PRECISION) {
            throw new IllegalArgumentException("DECIMAL 精度须在 1 到 " + MAX_PRECISION + " 之间");
        }
        if (scale < 0 || scale > precision) {
            throw new IllegalArgumentException("DECIMAL 小数位须在 0 到精度之间");
        }
        var sqlType = (dialect == PhysicalDialect.ORACLE ? "number(" : "decimal(") + precision + "," + scale + ")";
        return new PhysicalTypeSpec(PhysicalTypeSpec.DECIMAL, sqlType, precision, scale);
    }

    private static int parsePositive(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        try {
            var parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) throw new IllegalArgumentException(message);
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private static int[] splitPrecision(String value) {
        if (value == null || value.isBlank()) return null;
        var parts = value.trim().replace('.', ',').split(",");
        if (parts.length != 2) return null;
        try {
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
