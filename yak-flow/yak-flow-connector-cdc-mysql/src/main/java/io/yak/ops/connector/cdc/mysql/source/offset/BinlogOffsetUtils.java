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

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumUtils;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Utils for handling {@link BinlogOffset}.
 *
 * <p>Effective offset resolution delegates to Flink CDC's DebeziumUtils utility responsibility,
 * keeping JDBC and Binlog lookup together under the Debezium 2.7.3 adaptation.
 */
public class BinlogOffsetUtils {

    /**
     * Resolve an offset kind to the effective MySQL binlog location.
     *
     * <p>The caller keeps its original logical offset if it is already specific.
     */
    public static BinlogOffset initializeEffectiveOffset(BinlogOffset offset, MySqlSourceConfig mySqlSourceConfig) {
        switch (offset.getOffsetKind()) {
            case EARLIEST:
                return BinlogOffset.ofBinlogFilePosition("", 0);
            case TIMESTAMP:
                try (Connection connection = mySqlSourceConfig.openSnapshotConnection()) {
                    return DebeziumUtils.findBinlogOffset(
                            offset.getTimestampSec() * 1000, connection, mySqlSourceConfig);
                } catch (SQLException exception) {
                    throw new IllegalStateException("Cannot resolve MySQL binlog offset", exception);
                }
            case LATEST:
                try (Connection connection = mySqlSourceConfig.openSnapshotConnection()) {
                    return DebeziumUtils.currentBinlogOffset(connection);
                } catch (SQLException exception) {
                    throw new IllegalStateException("Cannot resolve MySQL binlog offset", exception);
                }
            default:
                return offset;
        }
    }

    public static boolean isNonStoppingOffset(BinlogOffset binlogOffset) {
        return BinlogOffsetKind.NON_STOPPING.equals(binlogOffset.getOffsetKind());
    }
}
