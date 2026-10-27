package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.base.source.assigner.SnapshotSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MySQL Snapshot split assignment backed by the common CDC assigner.
 *
 * <p>MySQL chunk discovery and Binlog handoff remain in the MySQL connector. Existing
 * checkpoint state and the reader-assignment protocol are unchanged.
 */
public final class MySqlSnapshotSplitAssigner extends SnapshotSplitAssigner<MySqlSnapshotSplit> {

    private static final int MAX_SPLITS = 10_000;

    public MySqlSnapshotSplitAssigner() {
        super(MAX_SPLITS);
    }

    public MySqlSnapshotSplitAssigner(
            List<MySqlSnapshotSplit> pending,
            Map<String, MySqlSnapshotSplit> assigned,
            Set<String> finished,
            int total,
            boolean planned) {
        super(pending, assigned, finished, total, planned, MAX_SPLITS);
    }
}
