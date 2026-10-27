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

package io.yak.ops.connector.cdc.mysql.source.utils;

import io.debezium.data.Envelope;
import io.debezium.relational.TableId;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.debezium.connector.AbstractSourceInfo.DATABASE_NAME_KEY;
import static io.debezium.connector.AbstractSourceInfo.TABLE_NAME_KEY;

/** Utilities for interpreting Debezium records, aligned with Flink CDC RecordUtils. */
public final class RecordUtils {

    public static final String SCHEMA_CHANGE_EVENT_KEY_NAME =
            "io.debezium.connector.mysql.SchemaChangeKey";
    public static final String SCHEMA_HEARTBEAT_EVENT_KEY_NAME =
            "io.debezium.connector.common.Heartbeat";
    public static final String SCHEMA_TRANSACTION_METADATA_EVENT_KEY_NAME =
            "io.debezium.connector.common.TransactionMetadataKey";

    private RecordUtils() {}

    public static Object[] rowToArray(ResultSet rs, int size) throws SQLException {
            final Object[] row = new Object[size];
            for (int i = 0; i < size; i++) {
                row[i] = rs.getObject(i + 1);
            }
            return row;
        }

    public static Struct getStructContainsChunkKey(SourceRecord record) {
            // Use chunk key in the after struct for insert or
            // the before struct for delete/update
            Envelope.Operation op = Envelope.operationFor(record);
            Struct value = (Struct) record.value();
            if (op == Envelope.Operation.CREATE || op == Envelope.Operation.READ) {
                return value.getStruct(Envelope.FieldName.AFTER);
            } else {
                return value.getStruct(Envelope.FieldName.BEFORE);
            }
        }

    public static Long getMessageTimestamp(SourceRecord record) {
            Schema schema = record.valueSchema();
            Struct value = (Struct) record.value();
            if (schema.field(Envelope.FieldName.SOURCE) == null) {
                return null;
            }
    
            Struct source = value.getStruct(Envelope.FieldName.SOURCE);
            if (source.schema().field(Envelope.FieldName.TIMESTAMP) == null) {
                return null;
            }
    
            return source.getInt64(Envelope.FieldName.TIMESTAMP);
        }

    public static Long getFetchTimestamp(SourceRecord record) {
            Schema schema = record.valueSchema();
            Struct value = (Struct) record.value();
            if (schema.field(Envelope.FieldName.TIMESTAMP) == null) {
                return null;
            }
            return value.getInt64(Envelope.FieldName.TIMESTAMP);
        }

    public static boolean isSchemaChangeEvent(SourceRecord sourceRecord) {
            Schema keySchema = sourceRecord.keySchema();
            if (keySchema != null && SCHEMA_CHANGE_EVENT_KEY_NAME.equalsIgnoreCase(keySchema.name())) {
                return true;
            }
            return false;
        }

    public static boolean isHeartbeatEvent(SourceRecord record) {
            Schema valueSchema = record.valueSchema();
            return valueSchema != null
                    && SCHEMA_HEARTBEAT_EVENT_KEY_NAME.equalsIgnoreCase(valueSchema.name())
                    || (record.topic() != null && record.topic().startsWith("__debezium-heartbeat."));
        }

    public static boolean isTransactionMetadataEvent(SourceRecord record) {
            Schema keySchema = record.keySchema();
            return keySchema != null
                    && SCHEMA_TRANSACTION_METADATA_EVENT_KEY_NAME.equalsIgnoreCase(keySchema.name());
        }

    public static boolean isDataChangeRecord(SourceRecord record) {
            Schema valueSchema = record.valueSchema();
            Struct value = (Struct) record.value();
            return valueSchema.field(Envelope.FieldName.OPERATION) != null
                    && value.getString(Envelope.FieldName.OPERATION) != null;
        }

    public static TableId getTableId(SourceRecord dataRecord) {
            Struct value = (Struct) dataRecord.value();
            Struct source = value.getStruct(Envelope.FieldName.SOURCE);
            String dbName = source.getString(DATABASE_NAME_KEY);
            String tableName = source.getString(TABLE_NAME_KEY);
            return new TableId(dbName, null, tableName);
        }

    public static boolean isTableChangeRecord(SourceRecord dataRecord) {
            Struct value = (Struct) dataRecord.value();
            Struct source = value.getStruct(Envelope.FieldName.SOURCE);
            String tableName = source.getString(TABLE_NAME_KEY);
            return tableName != null && !tableName.isBlank();
        }

    public static BinlogOffset getBinlogPosition(SourceRecord dataRecord) {
            return getBinlogPosition(dataRecord.sourceOffset());
        }

    public static BinlogOffset getBinlogPosition(Map<String, ?> offset) {
            Map<String, String> offsetStrMap = new HashMap<>();
            for (Map.Entry<String, ?> entry : offset.entrySet()) {
                offsetStrMap.put(
                        entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
            }
            return BinlogOffset.builder().setOffsetMap(offsetStrMap).build();
        }

    /**
     * Returns the earliest completed snapshot high watermark.
     * Flink CDC's BinlogOffset helper is adapted to the DebeziumOffset
     * carried by YakFlow's currently durable handoff protocol.
     */
    public static DebeziumOffset getStartingOffsetOfBinlogSplit(
            List<FinishedSnapshotSplitInfo> finishedSnapshotSplits) {
        return finishedSnapshotSplits.stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .min(DebeziumOffset::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("Missing finished MySQL snapshot splits"));
    }
}
