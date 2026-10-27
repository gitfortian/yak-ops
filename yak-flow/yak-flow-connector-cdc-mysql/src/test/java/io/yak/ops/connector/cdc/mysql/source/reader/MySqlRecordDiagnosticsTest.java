package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MySqlRecordDiagnosticsTest {

    @Test
    void binlogDiagnosticsRedactRowsAndOffsets() {
        TableRecord row = tableRecord();
        DebeziumOffset offset = new DebeziumOffset(
                Map.of("server", "internal-server"),
                Map.of("file", "sensitive-binlog-file", "position", 7654321L));
        BinlogEvent event = new BinlogEvent(List.of(row), offset);
        String diagnostic = event.toString();

        assertTrue(diagnostic.contains("recordCount=1"));
        assertFalse(diagnostic.contains("secret-order-data"));
        assertFalse(diagnostic.contains("sensitive-binlog-file"));
    }

    @Test
    void snapshotBatchDiagnosticsRedactRowContents() {
        MySqlFetchedRecord record =
                MySqlFetchedRecord.prepared(
                        new MySqlSnapshotSplit("chunk", tableRecord().tableId(), null, null, null)
                                .withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                                        List.of(tableRecord()))));
        String diagnostic = record.toString();

        assertTrue(diagnostic.contains("rowCount=1"));
        assertFalse(diagnostic.contains("secret-order-data"));
        assertFalse(diagnostic.contains("secret-order-data"));
    }

    private static DebeziumOffset offset(long pos) {
        return new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.1", "pos", pos));
    }

    private static TableRecord tableRecord() {
        return new TableRecord(
                new TableId(null, "public", "orders"),
                RowKind.INSERT,
                GenericRowData.of("secret-order-data"));
    }
}
