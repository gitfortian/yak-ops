package io.yak.ops.connector.cdc.mysql.source.offset;

import java.util.Objects;

/**
 * Snapshot and streaming offset comparison.
 *
 * <p>BinlogOffset owns the comparison semantics, matching Flink CDC's offset contract.
 * Keep this entry point until the snapshot and reader call sites are consolidated.
 */
public final class BinlogOffsetOrder {

    private BinlogOffsetOrder() {}

    public static int compare(BinlogOffset left, BinlogOffset right) {
        return Objects.requireNonNull(left, "left").compareTo(right);
    }

    /** Returns true when the event is strictly after the completed snapshot watermark. */
    public static boolean isAfter(BinlogOffset event, BinlogOffset high) {
        return compare(event, high) > 0;
    }
}
