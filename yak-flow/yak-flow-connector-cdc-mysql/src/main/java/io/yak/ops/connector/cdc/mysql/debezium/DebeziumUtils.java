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
import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import java.util.Objects;

/** Utilities related to Debezium. */
public final class DebeziumUtils {

    private DebeziumUtils() {}

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
