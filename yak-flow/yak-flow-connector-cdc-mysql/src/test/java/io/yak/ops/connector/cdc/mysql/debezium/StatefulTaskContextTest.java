/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.connector.cdc.mysql.debezium;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.debezium.task.context.StatefulTaskContext;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatefulTaskContextTest {

    @Test
    void contextRestoresOnlyCheckpointProgressNotQueuedPrefetch() throws Exception {
        StatefulTaskContext context = new StatefulTaskContext(config(), 5400);
        DebeziumOffset restored = offset(4L);
        byte[] history = new byte[] {1, 2, 3};
        context.configure(new MySqlBinlogSplit(restored, history));

        history[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, context.restoredSchemaHistory());

        context.offer(new BinlogEvent(List.of(), offset(8L)));
        assertEquals(restored, context.restoredOffset());
        assertEquals(offset(8L), context.poll().offset());
        assertEquals(restored, context.restoredOffset());
    }

    @Test
    void contextRejectsMissingSchemaHistoryAndRepeatedConfiguration() {
        StatefulTaskContext context = new StatefulTaskContext(config(), 5400);
        assertThrows(IllegalArgumentException.class,
                () -> context.configure(new MySqlBinlogSplit(offset(4L), new byte[0])));
        StatefulTaskContext fresh = new StatefulTaskContext(config(), 5400);
        fresh.configure(new MySqlBinlogSplit(null, new byte[0]));
        assertThrows(IllegalStateException.class,
                () -> fresh.configure(new MySqlBinlogSplit(null, new byte[0])));
    }

    @Test
    void producerFailureIsVisibleBeforePolling() {
        StatefulTaskContext context = new StatefulTaskContext(config(), 5400);
        IllegalStateException failure = new IllegalStateException("producer died");
        context.recordFailure(failure);
        IllegalStateException reported = assertThrows(IllegalStateException.class, context::poll);
        assertSame(failure, reported.getCause());
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "yakflow"),
                Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static MySqlSourceConfig config() {
        TableId tableId = new TableId("shop", null, "orders");
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                List.of("id"));
        return new MySqlSourceConfig(
                "localhost", 3306, "cdc", "secret", 5400, "yakflow-cdc", Map.of(tableId, schema), 4);
    }
}
