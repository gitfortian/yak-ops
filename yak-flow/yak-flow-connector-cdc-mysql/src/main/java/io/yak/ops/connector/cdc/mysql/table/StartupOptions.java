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

package io.yak.ops.connector.cdc.mysql.table;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.io.Serializable;
import java.util.Objects;

/** Debezium startup options. */
public final class StartupOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    public final StartupMode startupMode;
    public final BinlogOffset binlogOffset;

    /**
     * Performs an initial snapshot on the monitored database tables upon first startup, and
     * continue to read the latest binlog.
     */
    public static StartupOptions initial() {
        return new StartupOptions(StartupMode.INITIAL, null);
    }

    /**
     * Performs an initial snapshot on the monitored database tables upon first startup, and not
     * read the binlog anymore .
     */
    public static StartupOptions snapshot() {
        return new StartupOptions(StartupMode.SNAPSHOT, null);
    }

    /**
     * Never to perform snapshot on the monitored database tables upon first startup, just read from
     * the beginning of the binlog. This should be used with care, as it is only valid when the
     * binlog is guaranteed to contain the entire history of the database.
     */
    public static StartupOptions earliest() {
        return new StartupOptions(StartupMode.EARLIEST_OFFSET, BinlogOffset.ofEarliest());
    }

    /**
     * Never to perform snapshot on the monitored database tables upon first startup, just read from
     * the end of the binlog which means only have the changes since the connector was started.
     */
    public static StartupOptions latest() {
        return new StartupOptions(StartupMode.LATEST_OFFSET, BinlogOffset.ofLatest());
    }

    /**
     * Never to perform snapshot on the monitored database tables upon first startup, and directly
     * read binlog from the specified offset.
     */
    public static StartupOptions specificOffset(String specificOffsetFile, long specificOffsetPos) {
        return new StartupOptions(
                StartupMode.SPECIFIC_OFFSETS, BinlogOffset.ofBinlogFilePosition(specificOffsetFile, specificOffsetPos));
    }

    public static StartupOptions specificOffset(String gtidSet) {
        return new StartupOptions(StartupMode.SPECIFIC_OFFSETS, BinlogOffset.ofGtidSet(gtidSet));
    }

    public static StartupOptions specificOffset(BinlogOffset binlogOffset) {
        return new StartupOptions(StartupMode.SPECIFIC_OFFSETS, binlogOffset);
    }

    /**
     * Never to perform snapshot on the monitored database tables upon first startup, and directly
     * read binlog from the specified timestamp.
     *
     * <p>The consumer will traverse the binlog from the beginning and ignore change events whose
     * timestamp is smaller than the specified timestamp.
     *
     * @param startupTimestampMillis timestamp for the startup offsets, as milliseconds from epoch.
     */
    public static StartupOptions timestamp(long startupTimestampMillis) {
        return new StartupOptions(StartupMode.TIMESTAMP, BinlogOffset.ofTimestampSec(startupTimestampMillis / 1000));
    }

    private StartupOptions(StartupMode startupMode, BinlogOffset binlogOffset) {
        this.startupMode = startupMode;
        this.binlogOffset = binlogOffset;
        if (isStreamOnly()) {
            Objects.requireNonNull(binlogOffset, "Binlog offset is required if startup mode is " + startupMode);
        }
    }

    public boolean isStreamOnly() {
        return startupMode == StartupMode.EARLIEST_OFFSET
                || startupMode == StartupMode.LATEST_OFFSET
                || startupMode == StartupMode.SPECIFIC_OFFSETS
                || startupMode == StartupMode.TIMESTAMP;
    }

    public boolean isSnapshotOnly() {
        return startupMode == StartupMode.SNAPSHOT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StartupOptions that = (StartupOptions) o;
        return startupMode == that.startupMode && Objects.equals(binlogOffset, that.binlogOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startupMode, binlogOffset);
    }

    /** Existing YakFlow job configuration values, kept at the public configuration boundary. */
    public static StartupOptions from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("MySQL CDC startup mode must not be null");
        }
        return switch (value) {
            case "initial" -> initial();
            case "earliest-offset" -> earliest();
            case "latest-offset" -> latest();
            case "snapshot" -> snapshot();
            case "specific-offset", "timestamp" ->
                throw new UnsupportedOperationException(
                        "MySQL CDC startup mode requires an explicit start position: " + value);
            default -> throw new IllegalArgumentException("Unsupported MySQL CDC startup mode: " + value);
        };
    }

    public static StartupOptions of(StartupMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case INITIAL -> initial();
            case LATEST_OFFSET -> latest();
            case EARLIEST_OFFSET -> earliest();
            case SNAPSHOT -> snapshot();
            case SPECIFIC_OFFSETS, TIMESTAMP ->
                throw new UnsupportedOperationException(
                        "MySQL CDC startup mode requires an explicit start position: " + mode);
        };
    }

    public StartupMode startupMode() {
        return startupMode;
    }

    public String asModeString() {
        return switch (startupMode) {
            case INITIAL -> "initial";
            case EARLIEST_OFFSET -> "earliest-offset";
            case LATEST_OFFSET -> "latest-offset";
            case SPECIFIC_OFFSETS -> "specific-offset";
            case TIMESTAMP -> "timestamp";
            case SNAPSHOT -> "snapshot";
        };
    }

    /** Validates the complete Flink CDC startup contract before execution. */
    public void requireSupported() {
        if (startupMode == StartupMode.SPECIFIC_OFFSETS
                && (binlogOffset == null
                        || ((binlogOffset.getFilename() == null || binlogOffset.getFilename().isBlank())
                                && (binlogOffset.getGtidSet() == null || binlogOffset.getGtidSet().isBlank())))) {
            throw new IllegalArgumentException("Specific-offset startup requires a binlog file or GTID set");
        }
        if (startupMode == StartupMode.TIMESTAMP
                && (binlogOffset == null || binlogOffset.getTimestampSec() <= 0)) {
            throw new IllegalArgumentException("Timestamp startup requires a positive epoch timestamp");
        }
    }
}
