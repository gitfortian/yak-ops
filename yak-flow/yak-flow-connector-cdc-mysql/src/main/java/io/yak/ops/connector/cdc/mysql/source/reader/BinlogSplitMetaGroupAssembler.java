package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles Flink-style bounded Binlog metadata groups in the Source Reader mailbox.
 *
 * <p>Uncheckpointed partial groups are discarded on Reader recovery. The coordinator
 * resends metadata and waits for a new assembled event before releasing the handoff.
 */
final class BinlogSplitMetaGroupAssembler {
    private long generation = -1;
    private int totalGroups;
    private int totalSplits;
    private int nextGroup;
    private final List<FinishedSnapshotSplitInfo> finished = new ArrayList<>();
    private final Set<String> splitIds = new HashSet<>();
    private boolean assembled;

    /** Accepts the next group. Returns true when the complete set is now assembled. */
    boolean addGroup(BinlogSplitMetaEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getMetaGroupId() == 0 && event.getBinlogAssignmentGeneration() > generation) {
            generation = event.getBinlogAssignmentGeneration();
            totalGroups = event.getTotalGroups();
            totalSplits = event.getTotalFinishedSplitSize();
            nextGroup = 0;
            assembled = false;
            finished.clear();
            splitIds.clear();
        }
        if (event.getBinlogAssignmentGeneration() < generation) return false;
        if (generation != event.getBinlogAssignmentGeneration()
                || totalGroups != event.getTotalGroups()
                || totalSplits != event.getTotalFinishedSplitSize()) {
            throw new IllegalStateException("Binlog metadata groups belong to different assignments");
        }
        if (assembled) {
            if (event.getMetaGroupId() == 0) return true;
            throw new IllegalStateException("Extra metadata group after assembly");
        }
        if (event.getMetaGroupId() != nextGroup) {
            throw new IllegalStateException("Out-of-order or duplicate Binlog metadata group");
        }
        for (byte[] serialized : event.getMetaGroup()) {
            FinishedSnapshotSplitInfo info = FinishedSnapshotSplitInfo.deserialize(serialized);
            if (!splitIds.add(info.splitId())) {
                throw new IllegalStateException("Duplicate Snapshot split in Binlog metadata groups");
            }
            finished.add(info);
        }
        nextGroup++;
        if (finished.size() > totalSplits) {
            throw new IllegalStateException("Binlog metadata count exceeds snapshot split count");
        }
        if (nextGroup == totalGroups) {
            if (finished.size() != totalSplits) {
                throw new IllegalStateException("Incomplete assembled Binlog metadata");
            }
            assembled = true;
        }
        return assembled;
    }

    boolean isAssembled() {
        return assembled;
    }

    int nextGroupId() {
        if (generation < 0 || assembled) throw new IllegalStateException("No missing Binlog metadata group");
        return nextGroup;
    }

    long generation() {
        return generation;
    }

    List<FinishedSnapshotSplitInfo> finishedSplits(DebeziumOffset high) {
        if (!assembled) throw new IllegalStateException("Binlog metadata is not fully assembled");
        DebeziumOffset maximum = finished.stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .max(DebeziumOffset::compareTo)
                .orElseThrow();
        if (!maximum.equals(Objects.requireNonNull(high, "high"))) {
            throw new IllegalStateException("Binlog handoff High watermark differs from assembled metadata");
        }
        return List.copyOf(finished);
    }
}
