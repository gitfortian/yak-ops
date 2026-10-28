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

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.EventData;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.RotateEventData;
import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Utilities related to Debezium. */
public final class DebeziumUtils {

    private DebeziumUtils() {}

    /** Fetch the current Binlog coordinate using Flink CDC's shared lookup algorithm. */
    public static BinlogOffset currentBinlogOffset(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return currentBinlogOffset(statement);
        }
    }

    /**
     * Reuse an existing JDBC statement so Snapshot Task retains timeout and cancellation
     * ownership while the status SQL and GTID parsing remain centralized here.
     */
    public static BinlogOffset currentBinlogOffset(Statement statement) throws SQLException {
        ResultSet rows;
        try {
            rows = statement.executeQuery("SHOW BINARY LOG STATUS");
        } catch (SQLException unsupported) {
            try {
                rows = statement.executeQuery("SHOW MASTER STATUS");
            } catch (SQLException error) {
                error.addSuppressed(unsupported);
                throw error;
            }
        }
        try (ResultSet status = rows) {
            if (!status.next()) {
                throw new SQLException("Cannot read current MySQL binlog position");
            }
            String file = status.getString(1);
            long pos = status.getLong(2);
            if (file == null || file.isBlank() || pos < 1) {
                throw new SQLException("MySQL binary logging is disabled or returned an invalid position");
            }
            String gtids = status.getMetaData().getColumnCount() > 4 ? status.getString(5) : null;
            return BinlogOffset.builder().setBinlogFilePosition(file, pos).setGtidSet(gtids).build();
        }
    }

    /**
     * Port of Flink CDC DebeziumUtils.findBinlogOffset: enumerate binlogs and search for the
     * binlog file containing the target timestamp using the first non-rotate event timestamp.
     */
    public static BinlogOffset findBinlogOffset(long targetMs, Connection connection, MySqlSourceConfig config) {
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
            return BinlogOffset.ofBinlogFilePosition(endIdx < 0 ? binlogFiles.get(0) : binlogFiles.get(endIdx), 0);
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

    /** Creates a new {@link BinaryLogClient} for consuming MySQL binlog. */
    public static BinaryLogClient createBinaryClient(Configuration dbzConfiguration) {
        final MySqlConnectorConfig connectorConfig =
                new MySqlConnectorConfig(Objects.requireNonNull(dbzConfiguration, "dbzConfiguration"));
        return new BinaryLogClient(
                connectorConfig.getHostName(),
                connectorConfig.getPort(),
                connectorConfig.getUserName(),
                connectorConfig.getPassword());
    }

    /** Creates the one-shot discovery client using YakFlow's resolved connection settings. */
    public static BinaryLogClient createBinaryClient(MySqlSourceConfig sourceConfig) {
        Objects.requireNonNull(sourceConfig, "sourceConfig");
        return new BinaryLogClient(
                sourceConfig.getHostname(),
                sourceConfig.getPort(),
                sourceConfig.getUsername(),
                sourceConfig.getPassword());
    }
}
