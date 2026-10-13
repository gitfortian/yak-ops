package io.yak.ops.core.data;

import java.util.Objects;

/**
 * A table-identified row transported between source operators and downstream consumers.
 *
 * <p>One source can emit rows from arbitrarily many tables without changing its SourceReader
 * contract. Each table has its own column order, provided by the source's frozen table plan.
 */
public record TableRecord(TableId tableId, RowKind rowKind, RowData row) {

    public TableRecord {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(rowKind, "rowKind");
        Objects.requireNonNull(row, "row");
    }

    /** Excludes arbitrary RowData contents from diagnostics. */
    @Override
    public String toString() {
        return "TableRecord{tableId=" + tableId + ", rowKind=" + rowKind + ", row=redacted}";
    }
}
