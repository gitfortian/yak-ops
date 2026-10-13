package io.yak.ops.core.data;

import java.util.Objects;

/**
 * A table-identified row transported between source operators and downstream consumers.
 *
 * <p>One source can emit rows from arbitrarily many tables without changing its SourceReader
 * contract. Each table has its own column order, provided by the source's frozen table plan.
 *
 * <p>The RowData payload is handed off by reference. Producers must not mutate the payload
 * after handing it to a fetch queue or downstream operator. Consumers retaining a row beyond
 * the current call must detach its mutable fields at their ownership boundary.
 *
 * <p>A TableRecord has execution identity rather than value equality: mutable row contents
 * must not influence equality or hashing while the record is in flight.
 */
public final class TableRecord {

    private final TableId tableId;
    private final RowKind rowKind;
    private final RowData row;

    /**
     * Wraps a row whose ownership is transferred to downstream processing.
     *
     * @param tableId physical table identity
     * @param rowKind row mutation kind
     * @param row caller-owned payload, which must not be mutated after handoff
     */
    public TableRecord(TableId tableId, RowKind rowKind, RowData row) {
        this.tableId = Objects.requireNonNull(tableId, "tableId");
        this.rowKind = Objects.requireNonNull(rowKind, "rowKind");
        this.row = Objects.requireNonNull(row, "row");
    }

    /** Returns the physical identity of the emitted table. */
    public TableId tableId() {
        return tableId;
    }

    /** Returns the insert, update, or delete kind of the row. */
    public RowKind rowKind() {
        return rowKind;
    }

    /** Returns the transferred row payload without copying it. */
    public RowData row() {
        return row;
    }

    /** Excludes arbitrary RowData contents from diagnostics. */
    @Override
    public String toString() {
        return "TableRecord{tableId=" + tableId + ", rowKind=" + rowKind + ", row=redacted}";
    }
}
