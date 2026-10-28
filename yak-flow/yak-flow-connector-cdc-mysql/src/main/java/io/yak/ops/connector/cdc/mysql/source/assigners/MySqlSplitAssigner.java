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

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Determines which MySQL split to process and owns enumerator-side assignment state.
 *
 * <p>Source: apache/flink-cdc@b806d26762862a76dc91ac81f16dbd7f6ec62821,
 * MySqlSplitAssigner. The completion-offset payload is adapted to YakFlow's
 * Debezium 2.7 {@link DebeziumOffset}; source-reader state stays reader-owned.
 */
public interface MySqlSplitAssigner extends Closeable {

    /** Called to open any resources used for discovering and assigning splits. */
    void open();

    /** Returns the next split, or an empty result when no split is ready. */
    Optional<MySqlSplit> getNext();

    /** Whether assigned snapshot splits still need finished-offset reports. */
    boolean waitingForFinishedSplits();

    /** Finished snapshot split metadata used to construct the binlog split. */
    List<FinishedSnapshotSplitInfo> getFinishedSplitInfos();

    /** Handle finished-split offsets emitted by the Debezium 2.7 reader. */
    void onFinishedSplits(Map<String, DebeziumOffset> splitFinishedOffsets);

    /** Reassign splits returned after reader failure. */
    void addSplits(Collection<MySqlSplit> splits);

    /** Save only the enumerator-side assignment and discovery state. */
    PendingSplitsState snapshotState(long checkpointId);

    /** Called after a checkpoint became durable. */
    void notifyCheckpointComplete(long checkpointId);

    /** Current assignment status. */
    AssignerStatus getAssignerStatus();

    /** Trigger dynamic table discovery, if this source supports it. */
    void startAssignNewlyAddedTables();

    /** Whether all available splits have already been assigned. */
    boolean noMoreSplits();

    /** Acknowledge binlog split metadata update for newly discovered tables. */
    void onBinlogSplitUpdated();

    @Override
    void close() throws IOException;
}
