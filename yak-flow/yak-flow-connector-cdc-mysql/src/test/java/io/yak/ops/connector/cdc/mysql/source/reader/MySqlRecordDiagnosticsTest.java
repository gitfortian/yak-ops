package io.yak.ops.connector.cdc.mysql.source.reader;

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
    void hybridDiagnosticsRedactRowsAndSnapshotCursor() {
        MySqlFetchedRecord record =
                new MySqlFetchedRecord(List.of(tableRecord()), 987654321L, null, false);
        String diagnostic = record.toString();

        assertTrue(diagnostic.contains("rowCount=1"));
        assertFalse(diagnostic.contains("secret-order-data"));
        assertFalse(diagnostic.contains("987654321"));
    }

    private static TableRecord tableRecord() {
        return new TableRecord(
                new TableId(null, "public", "orders"),
                RowKind.INSERT,
                GenericRowData.of("secret-order-data"));
    }
}
