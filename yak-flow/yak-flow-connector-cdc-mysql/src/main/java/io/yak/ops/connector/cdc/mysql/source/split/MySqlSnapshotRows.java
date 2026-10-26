package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.UnaryOperator;

/**
 * Immutable logical snapshot rows represented by full-row values and occurrence counts.
 *
 * <p>Chunk keys are not row identity. Identical physical rows remain distinct logical
 * emissions, while the frozen checkpoint retains one representative per equal full row.
 * List positions are stable across checkpoint recovery and preserve replay event ordinals.
 */
public final class MySqlSnapshotRows extends AbstractList<TableRecord> implements RandomAccess {
    public record Run(TableRecord record, int copies) {
        public Run {
            Objects.requireNonNull(record, "snapshot row");
            if (record.rowKind() != RowKind.INSERT || copies < 1) {
                throw new IllegalArgumentException("Snapshot run requires INSERT rows and positive multiplicity");
            }
        }
    }

    private final List<Run> runs;
    private final int[] ends;
    private final int size;

    public MySqlSnapshotRows(List<Run> groups) {
        Objects.requireNonNull(groups, "groups");
        List<Run> detached = new ArrayList<>(groups.size());
        ends = new int[groups.size()];
        int total = 0;
        for (int index = 0; index < groups.size(); index++) {
            Run run = Objects.requireNonNull(groups.get(index), "run");
            total = Math.addExact(total, run.copies());
            detached.add(run);
            ends[index] = total;
        }
        runs = List.copyOf(detached);
        size = total;
    }

    public List<Run> runs() {
        return runs;
    }

    public MySqlSnapshotRows mapRows(UnaryOperator<TableRecord> copier) {
        Objects.requireNonNull(copier, "copier");
        List<Run> mapped = new ArrayList<>(runs.size());
        for (Run run : runs) mapped.add(new Run(copier.apply(run.record()), run.copies()));
        return new MySqlSnapshotRows(mapped);
    }

    @Override
    public TableRecord get(int index) {
        Objects.checkIndex(index, size);
        int run = Arrays.binarySearch(ends, index + 1);
        if (run < 0) run = -run - 1;
        return runs.get(run).record();
    }

    @Override
    public int size() {
        return size;
    }
}
