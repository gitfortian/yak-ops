package io.yak.ops.plugin.task.datasync.execution;

import static io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionReport.schemaStage;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionReport.StageResult;
import io.yak.ops.plugin.task.datasync.plan.TablePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;


/** Owns the active per-table preparation state and Connector metrics for one Task Instance. */
public final class TableExecutionProgress {
    private final List<TablePlan> plans;
    private final TableMetrics metrics;
    private final boolean continuous;
    private final String dataStep;
    private final List<StageResult> schemaStages;
    private boolean dataSubmitted;
    private boolean preparationStopped;

    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics) {
        this(plans, metrics, false);
    }

    /** Creates observations for a bounded batch or a snapshot-plus-continuous source. */
    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics, boolean continuous) {
        this(plans, metrics, continuous, "FULL");
    }

    /** Selects the physically executed data step rather than inferring it from the Definition. */
    public TableExecutionProgress(List<TablePlan> plans, TableMetrics metrics, boolean continuous, String dataStep) {
        this.dataStep = java.util.Objects.requireNonNull(dataStep, "dataStep");
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

    /** Returns progress from a point-in-time observation without mutating execution state. */
    public synchronized OptionalInt percent() {
        return reporter().percent();
    }

    /** Returns the existing versioned public JSON report contract. */
    public synchronized String report(JobStatus jobStatus) {
        return reporter().report(jobStatus);
    }

    /** Returns the compact public report summary, when available. */
    public synchronized String summary(JobStatus jobStatus) {
        return reporter().summary(jobStatus);
    }

    private DataSyncExecutionReport reporter() {
        return new DataSyncExecutionReport(
                plans, metrics, continuous, dataStep, List.copyOf(schemaStages), dataSubmitted, preparationStopped);
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
