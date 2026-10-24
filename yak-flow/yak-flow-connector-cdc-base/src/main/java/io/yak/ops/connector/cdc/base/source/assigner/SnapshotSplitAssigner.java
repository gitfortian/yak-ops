package io.yak.ops.connector.cdc.base.source.assigner;

import io.yak.ops.connector.cdc.base.source.meta.split.SnapshotSplit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinator-owned snapshot split assignment, matching the responsibilities of the
 * Flink CDC SnapshotSplitAssigner.
 *
 * <p>Assignment is not completion. The Reader owns its emitted cursor, and a completed
 * chunk is reported only after its output and watermark are established. This class
 * never performs database I/O or manages the stream handoff checkpoint.
 *
 * @param <SplitT> the database-specific finite snapshot split
 */
public class SnapshotSplitAssigner<SplitT extends SnapshotSplit> {

    private final Deque<SplitT> pending = new ArrayDeque<>();
    private final Map<String, SplitT> assigned = new LinkedHashMap<>();
    private final Set<String> finished = new LinkedHashSet<>();
    private final int maxSplits;
    private int total;
    private boolean planned;

    public SnapshotSplitAssigner(int maxSplits) {
        if (maxSplits <= 0) {
            throw new IllegalArgumentException("Snapshot split limit must be positive");
        }
        this.maxSplits = maxSplits;
    }

    /** Restores coordinator ownership without modifying Reader-owned cursors. */
    public SnapshotSplitAssigner(
            List<SplitT> pending,
            Map<String, SplitT> assigned,
            Set<String> finished,
            int total,
            boolean planned,
            int maxSplits) {
        this(maxSplits);
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(assigned, "assigned");
        Objects.requireNonNull(finished, "finished");
        if (total < 0 || total > maxSplits || pending.size() + assigned.size() + finished.size() != total) {
            throw new IllegalArgumentException("Invalid restored snapshot assignment accounting");
        }
        Set<String> ids = new HashSet<>(finished);
        for (SplitT split : pending) {
            checkSplit(split);
            if (!ids.add(split.splitId())) {
                throw new IllegalArgumentException("Duplicate restored pending split");
            }
        }
        for (Map.Entry<String, SplitT> entry : assigned.entrySet()) {
            checkSplit(entry.getValue());
            if (!entry.getKey().equals(entry.getValue().splitId()) || !ids.add(entry.getKey())) {
                throw new IllegalArgumentException("Duplicate or invalid restored assigned split");
            }
        }
        this.pending.addAll(pending);
        this.assigned.putAll(assigned);
        this.finished.addAll(finished);
        this.total = total;
        this.planned = planned;
    }

    /** Registers a fully enumerated list of snapshot ranges. */
    public void plan(List<SplitT> splits) {
        append(splits, true);
    }

    /** Adds a page of discovered ranges without completing discovery prematurely. */
    public void append(List<SplitT> splits, boolean lastPage) {
        Objects.requireNonNull(splits, "splits");
        if (planned || (long) total + splits.size() > maxSplits) {
            throw new IllegalStateException("Snapshot discovery is complete or exceeds the split limit");
        }
        Set<String> ids = new HashSet<>(assigned.keySet());
        ids.addAll(finished);
        pending.forEach(split -> ids.add(split.splitId()));
        for (SplitT split : splits) {
            checkSplit(split);
            if (!ids.add(split.splitId())) {
                throw new IllegalArgumentException("Duplicate snapshot split ID");
            }
        }
        pending.addAll(splits);
        total += splits.size();
        planned = lastPage;
    }

    /** Assigns work without marking it complete. */
    public SplitT next() {
        SplitT split = pending.pollFirst();
        if (split != null) {
            assigned.put(split.splitId(), split);
        }
        return split;
    }

    /** Records completion after the Reader has reported the finished split. */
    public void complete(String splitId) {
        if (finished.contains(splitId)) {
            return;
        }
        if (assigned.remove(splitId) == null) {
            throw new IllegalArgumentException("Completed snapshot split was not assigned");
        }
        finished.add(splitId);
    }

    /** Returns a failed Reader's assigned work to the front of the pending queue. */
    public void addBack(SplitT split) {
        checkSplit(split);
        if (finished.contains(split.splitId()) || assigned.remove(split.splitId()) == null) {
            throw new IllegalArgumentException("Returned snapshot split is not assigned");
        }
        pending.addFirst(split);
    }

    public boolean allFinished() {
        return planned && pending.isEmpty() && assigned.isEmpty() && finished.size() == total;
    }

    public boolean planned() {
        return planned;
    }

    public int total() {
        return total;
    }

    public List<SplitT> pending() {
        return List.copyOf(new ArrayList<>(pending));
    }

    public Map<String, SplitT> assigned() {
        return Map.copyOf(assigned);
    }

    public Set<String> finished() {
        return Set.copyOf(finished);
    }

    private static void checkSplit(SnapshotSplit split) {
        Objects.requireNonNull(split, "split");
        if (split.splitId() == null || split.splitId().isBlank() || split.tableId() == null) {
            throw new IllegalArgumentException("Snapshot split requires an ID and table");
        }
    }
}
