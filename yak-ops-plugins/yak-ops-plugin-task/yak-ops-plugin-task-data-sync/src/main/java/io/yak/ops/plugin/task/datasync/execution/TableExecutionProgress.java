package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.task.datasync.plan.TablePlan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Projects connector measurements into non-secret, versioned table and stage observations. */
public final class TableExecutionProgress {
    private final List<TablePlan> plans;
    private final TableMetrics metrics;
    private final boolean continuous;
    private final List<StageResult> schemaStages;
    private boolean dataSubmitted;
    private boolean preparationStopped;

    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics) {
        this(plans, metrics, false);
    }

    /** Creates observations for a bounded batch or a snapshot-plus-continuous source. */
    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics, boolean continuous) {
        this.continuous = continuous;
        this.plans = List.copyOf(plans);
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
        this.schemaStages = new ArrayList<>();
        if (plans.stream().anyMatch(plan -> plan.schemaChange() != null)) {
            if (plans.stream().anyMatch(plan -> plan.schemaChange() == null)) {
                throw new IllegalArgumentException("Schema preparation must cover every table mapping");
            }
            for (int index = 0; index < plans.size(); index++) {
                schemaStages.add(schemaStage("WAITING", 1, 0, null, null));
            }
        }
    }

    /** Returns the shared measurements used by the compiled row pipeline. */
    public TableMetrics metrics() {
        return metrics;
    }

    /** Marks a table's preparation as active without claiming that DDL has completed. */
    public synchronized void schemaStarted(int index) {
        schemaStages.set(index, schemaStage("RUNNING", 1, 0, null, null));
    }

    /** Records complete target readback, preserving this fact across row-engine generations. */
    public synchronized void schemaFinished(int index, String action) {
        schemaStages.set(index, schemaStage("FINISHED", 1, 1, action, null));
    }

    /** Records a safe failure identifier for a table whose preparation did not verify. */
    public synchronized void schemaFailed(int index, String failureCode) {
        schemaStages.set(index, schemaStage("FAILED", 1, 0, null, failureCode));
    }

    /** Records an unconfirmed external DDL outcome without claiming a terminal table result. */
    public synchronized void schemaUnknown(int index) {
        preparationStopped = true;
        schemaStages.set(index, schemaStage("UNKNOWN", 1, 0, null, null));
    }

    /** Records cancellation after the current preparation operation has returned. */
    public synchronized void schemaCanceled(int index) {
        schemaStages.set(index, schemaStage("CANCELED", 1, 0, null, null));
    }

    /** Stops unexecuted preparations without erasing previously verified tables. */
    public synchronized void preparationEnded() {
        preparationStopped = true;
        for (int index = 0; index < schemaStages.size(); index++) {
            StageResult stage = schemaStages.get(index);
            if ("WAITING".equals(stage.status()) || "RUNNING".equals(stage.status())) {
                schemaStages.set(index, schemaStage("CANCELED", 1, 0, null, null));
            }
        }
    }

    /** Marks the single engine-submission boundary after all target structures have verified. */
    public synchronized void dataSubmissionStarted() {
        if (schemaStages.stream().anyMatch(stage -> !"FINISHED".equals(stage.status()))) {
            throw new IllegalStateException("Every target must verify before row-engine submission");
        }
        dataSubmitted = true;
    }

    /** Calculates a split-weighted percent only once every table's denominator is known. */
    public synchronized OptionalInt percent() {
        if (!schemaStages.isEmpty() && !dataSubmitted) return OptionalInt.empty();
        if (continuous) return OptionalInt.empty();
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        long total = 0;
        long completed = 0;
        for (var plan : plans) {
            var value = snapshot.get(plan.source().table());
            if (value == null || value.plannedSplits() == null) return OptionalInt.empty();
            total += value.plannedSplits();
            completed += Math.min(value.completedSplits(), value.plannedSplits());
        }
        if (total == 0) return OptionalInt.empty();
        return OptionalInt.of((int) Math.min(99, Math.floor(100.0 * completed / total)));
    }

    /** Serializes one immutable report; terminal failure does not erase already committed table work. */
    public synchronized String report(JobStatus jobStatus) {
        if (!schemaStages.isEmpty()) return JSONUtils.toJson(preparedReport(jobStatus));
        if (continuous) return JSONUtils.toJson(realtimeReport(jobStatus));
        return JSONUtils.toJson(batchReport(jobStatus));
    }

    private Report batchReport(JobStatus jobStatus) {
        Map<TableId, TableMetrics.Snapshot> snapshot = metrics.snapshot();
        List<TableResult> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            var plan = plans.get(index);
            var value = snapshot.get(plan.source().table());
            if (value == null) value = new TableMetrics.Snapshot(null, 0, 0, 0, null);
            String state = "WAITING";
            boolean sourceComplete = value.plannedSplits() != null && value.completedSplits() >= value.plannedSplits();
            boolean committed = sourceComplete && value.committedRows() == value.readRows();
            if (value.failureCode() != null) state = "FAILED";
            else if (committed || jobStatus == JobStatus.FINISHED) state = "FINISHED";
            else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.CANCELED) state = "CANCELED";
            else if (value.plannedSplits() != null || value.readRows() > 0) state = "RUNNING";
            Integer percent = null;
            if ("FINISHED".equals(state)) percent = 100;
            else if (value.plannedSplits() != null && value.plannedSplits() > 0) {
                percent = (int) Math.min(99, Math.floor(100.0 * value.completedSplits() / value.plannedSplits()));
            }
            tables.add(new TableResult(
                    index,
                    "FULL",
                    state,
                    percent,
                    "SPLITS",
                    value.readRows(),
                    value.committedRows(),
                    value.plannedSplits(),
                    value.completedSplits(),
                    value.failureCode()));
        }
        return new Report(1, Instant.now().toString(), tables);
    }

    /** Returns a compact stage summary without per-table results, or null for bounded reports. */
    public synchronized String summary(JobStatus jobStatus) {
        if (!schemaStages.isEmpty())
            return JSONUtils.toJson(preparedReport(jobStatus).summary());
        if (!continuous) return null;
        return JSONUtils.toJson(realtimeSummary(jobStatus, metrics.continuousSnapshot()));
    }

    private RealtimeReport preparedReport(JobStatus status) {
        var observation = metrics.continuousSnapshot();
        RealtimeReport data = continuous ? realtimeReport(status) : null;
        Report batch = continuous ? null : batchReport(status);
        List<TableStages> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            List<StageResult> stages = new ArrayList<>();
            stages.add(schemaStages.get(index));
            if (!dataSubmitted) {
                stages.add(waitingDataStage("FULL"));
                if (continuous) stages.add(waitingDataStage("INCREMENTAL"));
            } else if (continuous) {
                stages.addAll(data.tables().get(index).stages());
            } else {
                TableResult table = batch.tables().get(index);
                stages.add(new StageResult(
                        "FULL",
                        table.status(),
                        table.progressPercent(),
                        "SPLITS",
                        table.readRows(),
                        table.committedRows(),
                        table.plannedSplits(),
                        table.completedSplits(),
                        table.failureCode()));
            }
            tables.add(new TableStages(index, stages));
        }
        List<StageResult> stages = new ArrayList<>();
        stages.add(schemaSummary());
        if (!dataSubmitted) {
            stages.add(waitingDataStage("FULL"));
            if (continuous) stages.add(waitingDataStage("INCREMENTAL"));
        } else if (continuous) {
            stages.addAll(data.stages());
        } else {
            var totals = aggregate(metrics.snapshot());
            boolean complete = batch.tables().stream().allMatch(table -> "FINISHED".equals(table.status()));
            String state = complete
                    ? "FINISHED"
                    : status == JobStatus.FAILED ? "FAILED" : status == JobStatus.CANCELED ? "CANCELED" : "RUNNING";
            Integer percentage = null;
            if (complete) percentage = 100;
            else if (percent().isPresent()) percentage = percent().getAsInt();
            stages.add(new StageResult(
                    "FULL",
                    state,
                    percentage,
                    "SPLITS",
                    totals.readRows(),
                    totals.committedRows(),
                    totals.plannedSplits(),
                    totals.completedSplits(),
                    totals.failureCode()));
        }
        String observedAt =
                dataSubmitted && continuous ? data.observedAt() : Instant.now().toString();
        String phase = !dataSubmitted ? "SCHEMA" : continuous ? data.phase() : "FULL";
        var summary = new RealtimeSummary(
                3,
                observedAt,
                continuous ? observation.generation() : 0,
                continuous && observation.restored(),
                continuous ? "GENERATION" : "INSTANCE",
                phase,
                new Checkpoints(
                        continuous ? observation.handoffCheckpointId() : null,
                        continuous ? observation.completedCheckpointId() : null),
                stages);
        return new RealtimeReport(
                3,
                observedAt,
                summary.generation(),
                summary.restored(),
                summary.counterScope(),
                phase,
                summary.checkpoints(),
                stages,
                tables,
                summary);
    }

    private StageResult waitingDataStage(String step) {
        return new StageResult(
                step,
                preparationStopped ? "CANCELED" : "WAITING",
                null,
                "FULL".equals(step) ? "SPLITS" : "RECORDS",
                null,
                null,
                null,
                null,
                null);
    }

    private StageResult schemaSummary() {
        int completed = (int) schemaStages.stream()
                .filter(stage -> "FINISHED".equals(stage.status()))
                .count();
        String failure = schemaStages.stream()
                .map(StageResult::failureCode)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean unknown = schemaStages.stream().anyMatch(stage -> "UNKNOWN".equals(stage.status()));
        String status = unknown
                ? "UNKNOWN"
                : completed == schemaStages.size()
                        ? "FINISHED"
                        : failure != null
                                ? "FAILED"
                                : preparationStopped
                                        ? "CANCELED"
                                        : schemaStages.stream().anyMatch(stage -> "RUNNING".equals(stage.status()))
                                                        || completed > 0
                                                ? "RUNNING"
                                                : "WAITING";
        return schemaStage(status, schemaStages.size(), completed, null, failure);
    }

    private static StageResult schemaStage(String status, int planned, int completed, String action, String failure) {
        return new StageResult(
                "SCHEMA",
                status,
                (int) (100L * completed / planned),
                "TABLES",
                null,
                null,
                null,
                null,
                failure,
                planned,
                completed,
                action);
    }

    private RealtimeReport realtimeReport(JobStatus status) {
        var observation = metrics.continuousSnapshot();
        List<TableStages> tables = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            var id = plans.get(index).source().table();
            var full = observation.snapshotTables().get(id);
            var incremental = observation.incrementalTables().get(id);
            tables.add(new TableStages(
                    index,
                    List.of(
                            stage("FULL", full, observation, status),
                            stage("INCREMENTAL", incremental, observation, status))));
        }
        var summary = realtimeSummary(status, observation);
        return new RealtimeReport(
                2,
                summary.observedAt(),
                summary.generation(),
                summary.restored(),
                summary.counterScope(),
                summary.phase(),
                summary.checkpoints(),
                summary.stages(),
                tables,
                summary);
    }

    private RealtimeSummary realtimeSummary(JobStatus status, TableMetrics.ContinuousSnapshot observation) {
        List<StageResult> stages = List.of(
                stage("FULL", aggregate(observation.snapshotTables()), observation, status),
                stage("INCREMENTAL", aggregate(observation.incrementalTables()), observation, status));
        return new RealtimeSummary(
                2,
                Instant.ofEpochMilli(observation.observedAt()).toString(),
                observation.generation(),
                observation.restored(),
                "GENERATION",
                observation.phase().name(),
                new Checkpoints(observation.handoffCheckpointId(), observation.completedCheckpointId()),
                stages);
    }

    private TableMetrics.Snapshot aggregate(Map<TableId, TableMetrics.Snapshot> values) {
        Integer planned = 0;
        long completed = 0;
        long read = 0;
        long committed = 0;
        String failure = null;
        for (var plan : plans) {
            var value = values.get(plan.source().table());
            if (value == null) {
                planned = null;
                continue;
            }
            if (planned != null && value.plannedSplits() != null) planned += value.plannedSplits();
            else planned = null;
            completed += value.completedSplits();
            read += value.readRows();
            committed += value.committedRows();
            if (value.failureCode() != null) failure = value.failureCode();
        }
        return new TableMetrics.Snapshot(planned, completed, read, committed, failure);
    }

    private StageResult stage(
            String step,
            TableMetrics.Snapshot value,
            TableMetrics.ContinuousSnapshot observation,
            JobStatus jobStatus) {
        if (value == null) value = new TableMetrics.Snapshot(null, 0, 0, 0, null);
        boolean full = "FULL".equals(step);
        String state;
        if (full && observation.fullComplete()) state = "FINISHED";
        else if (value.failureCode() != null) state = "FAILED";
        else if (jobStatus == JobStatus.FAILED) state = "FAILED";
        else if (jobStatus == JobStatus.CANCELED || jobStatus == JobStatus.CANCELLING) state = "CANCELED";
        else if (jobStatus == JobStatus.FAILING || (observation.generation() > 0 && !observation.active()))
            state = "UNKNOWN";
        else if (observation.generation() == 0 || (!full && observation.phase() != TableMetrics.Phase.STREAMING))
            state = "WAITING";
        else state = "RUNNING";
        // A queued incremental phase was not executed when initialization failed or was cancelled.
        if (!full && !observation.fullComplete() && jobStatus == JobStatus.FAILED) state = "CANCELED";
        Integer percent = null;
        if (full && observation.fullComplete()) percent = 100;
        else if (full && !observation.restored() && value.plannedSplits() != null && value.plannedSplits() > 0) {
            percent = (int) Math.min(99, Math.floor(100.0 * value.completedSplits() / value.plannedSplits()));
        }
        Long read = value.readRows();
        Long committed = value.committedRows();
        Integer planned = value.plannedSplits();
        Long completed = value.completedSplits();
        if (full && observation.restored()) {
            // Checkpoints persist execution state, not historical measurement totals.
            read = null;
            committed = null;
            planned = null;
            completed = null;
        }
        if (!full) {
            planned = null;
            completed = null;
        }
        return new StageResult(
                step,
                state,
                percent,
                full ? "SPLITS" : "RECORDS",
                read,
                committed,
                planned,
                completed,
                value.failureCode());
    }

    /** Two completed-checkpoint observations; unavailable restored identifiers remain null. */
    public record Checkpoints(Long handoffId, Long completedId) {}

    /** Public phase measurements. Incremental percent is always null; counters count input records. */
    public record StageResult(
            String step,
            String status,
            Integer progressPercent,
            String progressUnit,
            Long readRows,
            Long committedRows,
            Integer plannedSplits,
            Long completedSplits,
            String failureCode,
            Integer plannedTables,
            Integer completedTables,
            String action) {
        /** Creates a row-stage observation without schema-preparation counters. */
        public StageResult(
                String step,
                String status,
                Integer progressPercent,
                String progressUnit,
                Long readRows,
                Long committedRows,
                Integer plannedSplits,
                Long completedSplits,
                String failureCode) {
            this(
                    step,
                    status,
                    progressPercent,
                    progressUnit,
                    readRows,
                    committedRows,
                    plannedSplits,
                    completedSplits,
                    failureCode,
                    null,
                    null,
                    null);
        }
    }

    /** Table identity is resolved only from the Instance's immutable mapping version. */
    public record TableStages(int mappingIndex, List<StageResult> stages) {
        public TableStages {
            stages = List.copyOf(stages);
        }
    }

    /** Compact observation for list and batch Instance queries without fetching each table report. */
    public record RealtimeSummary(
            int version,
            String observedAt,
            long generation,
            boolean restored,
            String counterScope,
            String phase,
            Checkpoints checkpoints,
            List<StageResult> stages) {
        public RealtimeSummary {
            stages = List.copyOf(stages);
        }
    }

    /** Full report and its bounded summary are persisted together with the terminal Task outcome. */
    public record RealtimeReport(
            int version,
            String observedAt,
            long generation,
            boolean restored,
            String counterScope,
            String phase,
            Checkpoints checkpoints,
            List<StageResult> stages,
            List<TableStages> tables,
            RealtimeSummary summary) {
        public RealtimeReport {
            stages = List.copyOf(stages);
            tables = List.copyOf(tables);
        }
    }

    /** Report fields contain observations only; the matching table identities come from the pinned definition. */
    public record Report(int version, String observedAt, List<TableResult> tables) {
        public Report {
            tables = List.copyOf(tables);
        }
    }

    /** One mapping-indexed FULL-stage observation. Rows mean emitted and acknowledged input records, not affected rows. */
    public record TableResult(
            int mappingIndex,
            String step,
            String status,
            Integer progressPercent,
            String progressUnit,
            long readRows,
            long committedRows,
            Integer plannedSplits,
            long completedSplits,
            String failureCode) {}
}
