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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.debezium.connector.base.ChangeEventQueue;
import io.debezium.pipeline.DataChangeEvent;
import io.debezium.util.LoggingContext;
import io.yak.ops.connector.cdc.mysql.debezium.dispatcher.SignalEventDispatcher;
import io.yak.ops.connector.cdc.mysql.debezium.dispatcher.SignalEventDispatcher.WatermarkKind;
import io.yak.ops.connector.cdc.mysql.debezium.reader.StoppableChangeEventSourceContext;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;

class DebeziumRuntimeAlignmentTest {

    @Test
    void stoppableContextImplementsDebezium27Lifecycle() {
        StoppableChangeEventSourceContext context = new StoppableChangeEventSourceContext();
        assertTrue(context.isRunning());
        assertFalse(context.isPaused());
        context.waitStreamingPaused();
        context.streamingPaused();
        context.resumeStreaming();
        context.waitSnapshotCompletion();
        context.stopChangeEventSource();
        assertFalse(context.isRunning());
        context.startChangeEventSource();
        assertTrue(context.isRunning());
    }

    @Test
    void watermarkDispatcherRetainsFlinkCdcEventContract() throws InterruptedException {
        ChangeEventQueue<DataChangeEvent> queue = new ChangeEventQueue.Builder<DataChangeEvent>()
                .pollInterval(Duration.ofMillis(10))
                .maxQueueSize(8)
                .maxBatchSize(8)
                .loggingContextSupplier(() -> LoggingContext.forConnector("mysql", "yakflow", "test"))
                .build();
        SignalEventDispatcher dispatcher = new SignalEventDispatcher(Map.of("server", "mysql"), "watermark", queue);
        dispatcher.dispatchWatermarkEvent(
                new MySqlBinlogSplit(null, new byte[0]),
                BinlogOffset.ofBinlogFilePosition("mysql-bin.000003", 42L),
                WatermarkKind.HIGH);
        List<DataChangeEvent> records = queue.poll();
        assertEquals(1, records.size());
        SourceRecord record = records.get(0).getRecord();
        assertEquals("mysql-bin.000003", record.sourceOffset().get("file"));
        assertEquals("HIGH", ((Struct) record.value()).getString("watermark_kind"));
    }
}
