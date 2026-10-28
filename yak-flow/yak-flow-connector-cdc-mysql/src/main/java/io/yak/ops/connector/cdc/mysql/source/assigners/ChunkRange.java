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

package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * An immutable, half-open chunk range.
 *
 * <p>Ported from Flink CDC ChunkRange. YakFlow stores typed table/column metadata with each
 * boundary because its current JDBC snapshot and checkpoint format identify ranges directly;
 * MySQL ordering rather than Java string comparison determines VARCHAR boundary order.
 */
public final class ChunkRange {
    private final TableId tableId;
    private final String column;
    private final LogicalTypeRoot type;
    private final Object start;
    private final Object end;

    public ChunkRange(TableId tableId, String column, LogicalTypeRoot type, Object start, Object end) {
        this.tableId = Objects.requireNonNull(tableId, "tableId");
        this.type = Objects.requireNonNull(type, "type");
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Snapshot chunk column must not be blank");
        }
        switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> {
                require(start, Long.class);
                require(end, Long.class);
                if (start != null && end != null && ((Long) start) >= ((Long) end)) {
                    throw new IllegalArgumentException("Numeric snapshot chunk boundaries must advance");
                }
            }
            case DECIMAL -> {
                require(start, BigDecimal.class);
                require(end, BigDecimal.class);
                if (start != null && end != null && ((BigDecimal) start).compareTo((BigDecimal) end) >= 0) {
                    throw new IllegalArgumentException("Decimal snapshot chunk boundaries must advance");
                }
            }
            case CHAR, VARCHAR -> {
                require(start, String.class);
                require(end, String.class);
            }
            default -> throw new IllegalArgumentException("Unsupported MySQL snapshot chunk type: " + type);
        }
        this.column = column;
        this.start = start;
        this.end = end;
    }

    private static void require(Object value, Class<?> type) {
        if (value != null && !type.isInstance(value)) {
            throw new IllegalArgumentException("Snapshot chunk boundary has incompatible scalar type");
        }
    }

    public TableId tableId() {
        return tableId;
    }

    public String column() {
        return column;
    }

    public LogicalTypeRoot type() {
        return type;
    }

    public Object start() {
        return start;
    }

    public Object end() {
        return end;
    }

    public boolean last() {
        return end == null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChunkRange that)) return false;
        return Objects.equals(tableId, that.tableId)
                && Objects.equals(column, that.column)
                && type == that.type
                && Objects.equals(start, that.start)
                && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, column, type, start, end);
    }

    @Override
    public String toString() {
        return "ChunkRange{table=" + tableId + ", column=" + column + ", type=" + type + ", values=redacted}";
    }
}
