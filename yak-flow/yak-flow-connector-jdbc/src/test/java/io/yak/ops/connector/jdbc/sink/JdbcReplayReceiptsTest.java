package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.connector.jdbc.internal.executor.JdbcReplayReceipts;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcReplayReceiptsTest {
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("value", LogicalTypes.BIGINT)), List.of());
    private static final TableId SOURCE = new TableId("source", null, "rows");
    private static final TableId TARGET = new TableId("target", null, "rows");

    @Test
    void replayKeyIsStableForUpdateImagesAndChangesForDistinctRows() {
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, SCHEMA, JdbcWriteMode.RETRACT);
        ChangeEventId before = new ChangeEventId("definition", "binlog:123", 0, 2);
        ChangeEventId after = new ChangeEventId("definition", "binlog:123", 1, 2);
        assertArrayEquals(JdbcReplayReceipts.receiptKey(before, plan),
                JdbcReplayReceipts.receiptKey(after, plan));
        assertFalse(Arrays.equals(JdbcReplayReceipts.receiptKey(before, plan),
                JdbcReplayReceipts.receiptKey(new ChangeEventId("definition", "binlog:124", 0, 1), plan)));
    }

    @Test
    void sourceTargetAndDefinitionArePartOfTheReceiptNamespace() {
        var plan = new JdbcTableWritePlan(SOURCE, TARGET, SCHEMA, JdbcWriteMode.RETRACT);
        var event = new ChangeEventId("definition", "event", 0, 1);
        byte[] original = JdbcReplayReceipts.receiptKey(event, plan);
        var differentTarget = new JdbcTableWritePlan(
                SOURCE, new TableId("target", null, "different"), SCHEMA, JdbcWriteMode.RETRACT);
        var differentSource = new JdbcTableWritePlan(
                new TableId("source", null, "different"), TARGET, SCHEMA, JdbcWriteMode.RETRACT);
        assertFalse(Arrays.equals(original, JdbcReplayReceipts.receiptKey(event, differentTarget)));
        assertFalse(Arrays.equals(original, JdbcReplayReceipts.receiptKey(event, differentSource)));
        assertFalse(Arrays.equals(original, JdbcReplayReceipts.receiptKey(
                new ChangeEventId("new-definition", "event", 0, 1), plan)));
        // Separate source identities and tables cannot share the same receipt namespace.
    }
}
