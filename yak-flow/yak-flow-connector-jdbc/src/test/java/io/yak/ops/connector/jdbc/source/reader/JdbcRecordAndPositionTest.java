package io.yak.ops.connector.jdbc.source.reader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import org.junit.jupiter.api.Test;

class JdbcRecordAndPositionTest {

    @Test
    void diagnosticsDoNotIncludeRowsOrBusinessKeyCursors() {
        TableRecord row = new TableRecord(
                new TableId(null, "public", "orders"),
                RowKind.INSERT,
                GenericRowData.of("secret-order-data"));
        JdbcRecordAndPosition fetched = new JdbcRecordAndPosition(row, 987654321L);
        String diagnostic = fetched.toString();

        assertFalse(diagnostic.contains("secret-order-data"));
        assertFalse(diagnostic.contains("987654321"));
        assertTrue(diagnostic.contains("record=redacted"));
        assertTrue(diagnostic.contains("lastKey=redacted"));
    }
}
