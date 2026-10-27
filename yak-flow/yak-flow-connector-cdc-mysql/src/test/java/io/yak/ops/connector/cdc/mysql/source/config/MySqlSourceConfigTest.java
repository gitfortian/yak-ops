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

package io.yak.ops.connector.cdc.mysql.source.config;

import io.debezium.relational.CachedTableFilter;
import io.debezium.relational.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link MySqlSourceConfig}. */
class MySqlSourceConfigTest {

    @Test
    void testTableFilterWithExcludeTableList() {
        MySqlSourceConfig config =
                new MySqlSourceConfigFactory()
                        .hostname("localhost")
                        .username("user")
                        .password("password")
                        .databaseList("test_db")
                        .tableList("test_db\\.orders_.*")
                        .excludeTableList("test_db.orders_skip")
                        .topicPrefix("yak_mysql_test")
                        .serverId(6001)
                        .table(
                                new io.yak.ops.core.data.TableId("test_db", null, "orders_1"),
                                new TableSchema(
                                        List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                        List.of("id")))
                        .createConfig(0);

        Predicate<TableId> tableFilter = config.getTableFilter();
        TableId includedTable = new TableId("test_db", null, "orders_1");
        TableId excludedTable = new TableId("test_db", null, "orders_skip");
        TableId unmatchedTable = new TableId("test_db", null, "customers");

        assertThat(config.getTableFilters().dataCollectionFilter())
                .isInstanceOf(CachedTableFilter.class);
        assertThat(tableFilter.test(includedTable)).isTrue();
        assertThat(tableFilter.test(excludedTable)).isFalse();
        assertThat(tableFilter.test(unmatchedTable)).isFalse();
    }
}
