package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.connector.cdc.mysql.source.MySqlHybridCdcSource;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlan;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlanner;
import io.yak.ops.plugin.task.datasync.execution.SyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.connector.SyncConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.pipeline.SyncPipelineCompiler;
import io.yak.ops.plugin.task.datasync.pipeline.SyncRuntimeOptions;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Verifies product DATA_SYNC planning and execution without opening physical JDBC connections. */
class SyncExecutionTest {
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(32))),
            List.of("id"));
    private static final TaskExecutionIdentity CONTEXT =
            new TaskExecutionIdentity("ws", "definition", 1, "instance-123", "attempt-123");

    @Test
    void boundedJdbcRunsThroughExistingPipelineExecutor() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(false);
        TaskAdmissionPlan plan = plugin.plan(parameters, CONTEXT);
        assertEquals(1, plan.exclusiveResources().size());
        assertEquals("DATA_SYNC_TARGET", plan.exclusiveResources().getFirst().type());
        assertEquals(0, resources.connections.get());

        var handle = plugin.start(parameters, plan, CONTEXT).toCompletableFuture().join();
        assertEquals(1, submissions.get());
        assertEquals("YAKFLOW", handle.runtimeReference().orElseThrow().engine());
        assertEquals(TaskOutcome.FINISHED, handle.completion().toCompletableFuture().join());
        assertEquals(0, resources.openedConnections.get());
        assertEquals(4, resources.tables.get());
        assertEquals(2, resources.connections.get());
    }


    @Test
    void preparesNonSecretLogicalJobPlanWithoutResolvingConnections() {
        Resources resources = new Resources();
        SyncJobPlanner planner = new SyncJobPlanner(resources, new SyncConnectorFactoryRegistry());
        SyncParameters parameters = parameters(true);

        SyncJobPlan prepared = planner.prepare(parameters, CONTEXT);

        assertEquals(parameters.source(), prepared.sourceParameters());
        assertEquals(parameters.sink(), prepared.sinkParameters());
        assertEquals("orders", prepared.source().table().table());
        assertEquals("ods_orders", prepared.target().table().table());
        assertEquals(planner.plan(parameters, CONTEXT), prepared.admissionPlan());
        assertEquals(0, resources.connections.get());
        assertEquals(0, resources.openedConnections.get());
    }

    @Test
    void rejectsChangedTableSchemaBeforeLaunchingEvenWhenDatasourceRevisionIsStable() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(false);
        TaskAdmissionPlan admitted = plugin.plan(parameters, CONTEXT);

        resources.targetSchema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(64))),
                List.of("id"));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> plugin.start(parameters, admitted, CONTEXT).toCompletableFuture().join());

        assertInstanceOf(TaskStartRejectedException.class, failure.getCause());
        assertEquals(0, submissions.get());
        assertEquals(0, resources.connections.get());
        assertEquals(0, resources.openedConnections.get());
    }

    @Test
    void refusesChangedDatasourceRevisionBeforeLaunching() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(false);
        TaskAdmissionPlan admitted = plugin.plan(parameters, CONTEXT);
        resources.targetRevision++;
        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> plugin.start(parameters, admitted, CONTEXT).toCompletableFuture().join());
        assertInstanceOf(TaskStartRejectedException.class, failure.getCause());
        assertEquals(0, submissions.get());
        assertEquals(0, resources.connections.get());
        assertEquals(4, resources.tables.get());
    }

    @Test
    void mySqlCdcBuildsHybridSnapshotPipeline() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(true);
        TaskAdmissionPlan plan = plugin.plan(parameters, CONTEXT);
        assertTrue(plan.resourceRevision().length() <= 128);
        var handle = plugin.start(parameters, plan, CONTEXT).toCompletableFuture().join();
        assertEquals("YAKFLOW", handle.runtimeReference().orElseThrow().engine());
        assertEquals(1, submissions.get());
        assertEquals(0, resources.openedConnections.get());
    }


    @Test
    void compilerSelectsJdbcAndCdcWithoutOpeningRuntimeConnections() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        AtomicReference<StreamGraph> lastGraph = new AtomicReference<>();
        AtomicReference<Configuration> lastConfiguration = new AtomicReference<>();
        PipelineExecutor capture = (graph, configuration) -> {
            lastGraph.set((StreamGraph) graph);
            lastConfiguration.set(new Configuration(configuration));
            return finishedExecutor(submissions).execute(graph, configuration);
        };
        SyncTaskPlugin plugin = plugin(resources, capture);

        SyncParameters jdbc = parameters(false);
        plugin.start(jdbc, plugin.plan(jdbc, CONTEXT), CONTEXT).toCompletableFuture().join();

        StreamGraph bounded = lastGraph.get();
        assertTrue(bounded.isBounded());
        assertInstanceOf(JdbcSource.class, bounded.getSourceNodes().getFirst().getSource().orElseThrow());
        assertInstanceOf(JdbcSink.class, bounded.getSinkNodes().getFirst().getSink().orElseThrow());
        assertEquals("data-sync-source-definition", bounded.getSourceNodes().getFirst().getUid());
        assertEquals("data-sync-sink-definition", bounded.getSinkNodes().getFirst().getUid());
        assertEquals(java.time.Duration.ZERO, lastConfiguration.get().get(CheckpointingOptions.CHECKPOINTING_INTERVAL));

        SyncParameters cdc = parameters(true);
        plugin.start(cdc, plugin.plan(cdc, CONTEXT), CONTEXT).toCompletableFuture().join();

        StreamGraph continuous = lastGraph.get();
        assertTrue(!continuous.isBounded());
        assertInstanceOf(MySqlHybridCdcSource.class, continuous.getSourceNodes().getFirst().getSource().orElseThrow());
        assertEquals(java.time.Duration.ofSeconds(10), lastConfiguration.get().get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(2, submissions.get());
        assertEquals(0, resources.openedConnections.get());
    }

    @Test
    void refusesChangedConnectionRevisionBeforePhysicalSubmission() {
        Resources resources = new Resources();
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(false);
        TaskAdmissionPlan plan = plugin.plan(parameters, CONTEXT);
        resources.sourceConnectionRevision = 2;

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> plugin.start(parameters, plan, CONTEXT).toCompletableFuture().join());

        assertInstanceOf(TaskStartRejectedException.class, failure.getCause());
        assertEquals(0, submissions.get());
        assertEquals(0, resources.openedConnections.get());
    }

    @Test
    void refusesTunneledCdcBeforePhysicalSubmission() {
        Resources resources = new Resources();
        resources.sourceTunneled = true;
        AtomicInteger submissions = new AtomicInteger();
        SyncTaskPlugin plugin = plugin(resources, finishedExecutor(submissions));
        SyncParameters parameters = parameters(true);
        TaskAdmissionPlan plan = plugin.plan(parameters, CONTEXT);

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> plugin.start(parameters, plan, CONTEXT).toCompletableFuture().join());

        assertInstanceOf(TaskStartRejectedException.class, failure.getCause());
        assertEquals(0, submissions.get());
        assertEquals(0, resources.openedConnections.get());
    }


    @Test
    void cdcPlanReservesTheReplicationServerIdAlongsideTheTarget() {
        Resources resources = new Resources();
        SyncJobPlanner planner = new SyncJobPlanner(resources, new SyncConnectorFactoryRegistry());
        SyncParameters parameters = parameters(true);
        TaskAdmissionPlan admitted = planner.plan(parameters, CONTEXT);
        TaskExecutionIdentity anotherAttempt =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-123", "attempt-456");

        assertEquals(2, admitted.exclusiveResources().size());
        assertTrue(admitted.exclusiveResources().stream().anyMatch(
                resource -> resource.type().equals("DATA_SYNC_TARGET")));
        assertTrue(admitted.exclusiveResources().stream().anyMatch(
                resource -> resource.type().equals("MYSQL_CDC_SERVER_ID")));
        assertEquals(admitted, planner.plan(parameters, anotherAttempt));
        assertEquals(0, resources.connections.get());
    }

    @Test
    void systemRuntimeOptionsControlGraphParallelismAndSameJobRecovery() {
        Resources resources = new Resources();
        SyncConnectorFactoryRegistry factories = new SyncConnectorFactoryRegistry();
        SyncJobPlanner planner = new SyncJobPlanner(resources, factories);
        SyncRuntimeOptions options = new SyncRuntimeOptions(2, 1, 512, 64, Duration.ofMillis(750), 2);
        SyncPipelineCompiler compiler =
                new SyncPipelineCompiler(resources, factories, options, Path.of("target/data-sync"));
        SyncParameters parameters = parameters(true);

        var compiled = compiler.compile(planner.prepare(parameters, CONTEXT), CONTEXT);

        assertEquals(2, compiled.graph().getSourceNodes().getFirst().getParallelism());
        assertEquals(1, compiled.graph().getSinkNodes().getFirst().getParallelism());
        assertEquals(Duration.ofMillis(750), compiled.configuration().get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(2, compiled.configuration().get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, compiled.configuration().get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(2, resources.connections.get());
        assertEquals(0, resources.openedConnections.get());
    }

    @Test
    void refusesChangelogSinkScalingBeforeLoadingConnectionCredentials() {
        Resources resources = new Resources();
        SyncConnectorFactoryRegistry factories = new SyncConnectorFactoryRegistry();
        SyncJobPlanner planner = new SyncJobPlanner(resources, factories);
        SyncRuntimeOptions settings = new SyncRuntimeOptions(2, 2, 1024, 256, Duration.ofSeconds(10), 1);
        SyncPipelineCompiler compiler =
                new SyncPipelineCompiler(resources, factories, settings, Path.of("target/data-sync"));
        SyncParameters parameters = parameters(true);

        assertThrows(
                UnsupportedOperationException.class,
                () -> compiler.compile(planner.prepare(parameters, CONTEXT), CONTEXT));
        assertEquals(0, resources.connections.get());
        assertEquals(0, resources.openedConnections.get());
    }

    private static SyncTaskPlugin plugin(SyncRuntimeResources resources, PipelineExecutor executor) {
        SyncConnectorFactoryRegistry factories = new SyncConnectorFactoryRegistry();
        SyncJobPlanner planner = new SyncJobPlanner(resources, factories);
        SyncPipelineCompiler compiler = new SyncPipelineCompiler(resources, factories, Path.of("target/data-sync"));
        return new SyncTaskPlugin(planner, new SyncTaskExecutor(planner, compiler, executor), factories);
    }

    private static SyncParameters parameters(boolean cdc) {
        var source = cdc
                ? new MySqlCdcSourceParameters("source", null, null, "orders")
                : new JdbcSourceParameters("source", null, null, "orders");
        return new SyncParameters(
                source,
                List.of(),
                new JdbcSinkParameters(
                        "target", null, null, "ods_orders", cdc ? DataSyncWriteMode.UPSERT : DataSyncWriteMode.APPEND));
    }

    private static PipelineExecutor finishedExecutor(AtomicInteger submissions) {
        return (Pipeline graph, Configuration config) -> {
            submissions.incrementAndGet();
            JobID id = JobID.generate();
            JobClient client = new JobClient() {
                @Override
                public JobID getJobID() {
                    return id;
                }

                @Override
                public CompletableFuture<JobStatus> getJobStatus() {
                    return CompletableFuture.completedFuture(JobStatus.FINISHED);
                }

                @Override
                public CompletableFuture<Void> cancel() {
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
                    return CompletableFuture.completedFuture(new JobExecutionResult(id, 1));
                }
            };
            return CompletableFuture.completedFuture(client);
        };
    }

    private static final class Resources implements SyncRuntimeResources {
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger tables = new AtomicInteger();
        private final AtomicInteger openedConnections = new AtomicInteger();
        private long targetRevision = 1;
        private long sourceConnectionRevision = 1;
        private boolean sourceTunneled;
        private TableSchema targetSchema = SCHEMA;

        @Override
        public SyncRuntimeTable table(
                String workspace, String datasource, String database, String schema, String table) {
            assertEquals("ws", workspace);
            tables.incrementAndGet();
            if ("source".equals(datasource)) {
                return new SyncRuntimeTable("MYSQL", new TableId("app", null, "orders"), SCHEMA, 1);
            }
            return new SyncRuntimeTable(
                    "POSTGRE_SQL", new TableId(null, "public", "ods_orders"), targetSchema, targetRevision);
        }

        @Override
        public SyncRuntimeConnection connection(String workspace, String datasource) {
            connections.incrementAndGet();
            JdbcConnectionProvider provider = () -> {
                openedConnections.incrementAndGet();
                throw new SQLException("Physical JDBC connections are not opened during graph planning");
            };
            if ("source".equals(datasource)) {
                return new SyncRuntimeConnection(
                        provider, "jdbc:mysql://localhost:3306/app", "MYSQL",
                        "user", "secret", "localhost", 3306, sourceConnectionRevision, sourceTunneled);
            }
            return new SyncRuntimeConnection(
                    provider, "jdbc:postgresql://localhost:5432/app", "POSTGRE_SQL",
                    "user", "secret", "localhost", 5432, targetRevision, false);
        }
    }
}
