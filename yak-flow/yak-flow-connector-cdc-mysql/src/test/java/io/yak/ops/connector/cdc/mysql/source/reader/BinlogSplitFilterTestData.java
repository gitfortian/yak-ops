package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Map;

/** Shared Binlog fixture; filtering expectations stay in the keyed and typed scenario tests. */
final class BinlogSplitFilterTestData {
    static final TableId TABLE = new TableId("shop", null, "orders");

    private BinlogSplitFilterTestData() {}

    static TableRecord row(RowKind kind, long key, String name) {
        return new TableRecord(TABLE, kind, GenericRowData.of(key, name));
    }

    static BinlogEvent event(long position, TableRecord... records) {
        return new BinlogEvent(List.of(records), offset(position));
    }

    static DebeziumOffset offset(long position) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
