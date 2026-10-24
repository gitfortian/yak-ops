package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/**
 * Checks the provisional full-row matching contract for a keyless JDBC target.
 *
 * <p>This is metadata only. Physical unique indexes, collation, triggers and
 * transactional replay still require database verification before activation.
 */
public final class JdbcKeylessRetractionContract {
    private JdbcKeylessRetractionContract() {}

    public static void validate(TableSchema source, TableSchema target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!source.primaryKeys().isEmpty() || !target.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("JDBC RETRACT requires keyless source and target tables");
        }
        if (source.columnCount() == 0 || source.columnCount() != target.columnCount()) {
            throw new IllegalArgumentException("JDBC RETRACT requires a complete full-row field mapping");
        }
        for (int index = 0; index < source.columnCount(); index++) {
            Column from = source.column(index);
            Column to = target.column(index);
            if (!from.name().equals(to.name())
                    || !from.dataType().isResolved()
                    || !to.dataType().isResolved()
                    || !from.dataType().equals(to.dataType())
                    || (from.nullable() && !to.nullable())) {
                throw new IllegalArgumentException(
                        "JDBC RETRACT requires equal source and target column definitions: " + to.name());
            }
            LogicalTypeRoot type = from.dataType().getTypeRoot();
            if (type != LogicalTypeRoot.BOOLEAN
                    && type != LogicalTypeRoot.TINYINT
                    && type != LogicalTypeRoot.SMALLINT
                    && type != LogicalTypeRoot.INTEGER
                    && type != LogicalTypeRoot.BIGINT
                    && type != LogicalTypeRoot.DECIMAL
                    && type != LogicalTypeRoot.BINARY
                    && type != LogicalTypeRoot.VARBINARY) {
                throw new IllegalArgumentException(
                        "JDBC RETRACT full-row comparison requires verified database semantics for: " + to.name());
            }
        }
    }
}
