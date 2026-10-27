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

package io.yak.ops.connector.cdc.mysql.source.offset;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.EventData;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.RotateEventData;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlCdcSourceConfig;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Utils for handling {@link BinlogOffset}.
 *
 * <p>Ported from Flink CDC's BinlogOffsetUtils and DebeziumUtils offset initialization,
 * adapting the database connection to YakFlow's JDBC Source configuration. No offset
 * initialization is executed for unsupported startup modes.
 */
public class BinlogOffsetUtils {

    /**
     * Initialize the binlog offset according to the kind of binlog offset, so that the debezium
     * reader could interpret it and seek the reader to the offset.
     *
     * <p>The binlog offset kind will be overridden to {@link BinlogOffsetKind#SPECIFIC} after the
     * initialization, as the initialized effective offset describes a specific position in binlog.
     *
     * <p>Initialization strategy:
     *
     * <ul>
     *   <li>EARLIEST: binlog filename = "", position = 0
     *   <li>TIMESTAMP: find the first binlog file at or before the specified timestamp
     *   <li>LATEST: fetch the current binlog by JDBC
     * </ul>
     */
    public static BinlogOffset initializeEffectiveOffset(
            BinlogOffset offset, MySqlCdcSourceConfig mySqlSourceConfig) {
        try (Connection connection = mySqlSourceConfig.openSnapshotConnection()) {
            return switch (offset.getOffsetKind()) {
                case EARLIEST -> BinlogOffset.ofBinlogFilePosition("", 0);
                case TIMESTAMP -> findBinlogOffset(
                        offset.getTimestampSec() * 1000, connection, mySqlSourceConfig);
                case LATEST -> currentBinlogOffset(connection);
                default -> offset;
            };
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot resolve MySQL binlog offset", e);
        }
    }

    /** Fetch current binlog offsets in MySQL Server. */
    private static BinlogOffset currentBinlogOffset(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rows;
            try {
                rows = statement.executeQuery("SHOW BINARY LOG STATUS");
            } catch (SQLException unsupported) {
                rows = statement.executeQuery("SHOW MASTER STATUS");
            }
            try (ResultSet status = rows) {
                if (!status.next()) {
                    throw new SQLException("Cannot read current MySQL binlog position");
                }
                return BinlogOffset.builder()
                        .setBinlogFilePosition(status.getString(1), status.getLong(2))
                        .build();
            }
        }
    }

    /**
     * Port of Flink CDC DebeziumUtils.findBinlogOffset: enumerate binlogs and search for the
     * binlog file containing the target timestamp using the first non-rotate event timestamp.
     */
    private static BinlogOffset findBinlogOffset(
            long targetMs, Connection connection, MySqlCdcSourceConfig config) {
        List<String> binlogFiles = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SHOW BINARY LOGS")) {
            while (rs.next()) {
                if (rs.getLong(2) > 0) {
                    binlogFiles.add(rs.getString(1));
                }
            }
            if (binlogFiles.isEmpty()) {
                return BinlogOffset.ofBinlogFilePosition("", 0);
            }
            BinaryLogClient client = config.newBinlogLookupClient();
            client.setServerId(config.serverId());
            int startIdx = 0;
            int endIdx = binlogFiles.size() - 1;
            while (startIdx <= endIdx) {
                int mid = startIdx + (endIdx - startIdx) / 2;
                long midTs = getBinlogTimestamp(client, binlogFiles.get(mid));
                if (midTs < targetMs) {
                    startIdx = mid + 1;
                } else if (targetMs < midTs) {
                    endIdx = mid - 1;
                } else {
                    return BinlogOffset.ofBinlogFilePosition(binlogFiles.get(mid), 0);
                }
            }
            return BinlogOffset.ofBinlogFilePosition(
                    endIdx < 0 ? binlogFiles.get(0) : binlogFiles.get(endIdx), 0);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot find MySQL binlog offset for timestamp", e);
        }
    }

    private static long getBinlogTimestamp(BinaryLogClient client, String binlogFile)
            throws IOException, InterruptedException {
        ArrayBlockingQueue<Long> binlogTimestamps = new ArrayBlockingQueue<>(1);
        BinaryLogClient.EventListener listener = event -> {
            EventData data = event.getData();
            if (data instanceof RotateEventData) {
                return;
            }
            EventHeaderV4 header = event.getHeader();
            long timestamp = header.getTimestamp();
            if (timestamp > 0) {
                binlogTimestamps.offer(timestamp);
                try {
                    client.disconnect();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot close binlog lookup client", e);
                }
            }
        };
        try {
            client.registerEventListener(listener);
            client.setBinlogFilename(binlogFile);
            client.setBinlogPosition(0);
            client.connect();
        } finally {
            client.unregisterEventListener(listener);
        }
        Long timestamp = binlogTimestamps.poll(10, TimeUnit.SECONDS);
        if (timestamp == null) {
            throw new IOException("Cannot read timestamp from binlog " + binlogFile);
        }
        return timestamp;
    }

    public static boolean isNonStoppingOffset(BinlogOffset binlogOffset) {
        return BinlogOffsetKind.NON_STOPPING.equals(binlogOffset.getOffsetKind());
    }
}
