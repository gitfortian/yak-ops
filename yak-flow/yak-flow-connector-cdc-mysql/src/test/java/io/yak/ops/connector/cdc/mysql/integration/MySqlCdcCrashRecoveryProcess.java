package io.yak.ops.connector.cdc.mysql.integration;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedJobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Child JVM used by the real-process CDC crash acceptance test. */
public final class MySqlCdcCrashRecoveryProcess {

    private static final String SOURCE_DATABASE = "yak_cdc_it";

    private MySqlCdcCrashRecoveryProcess() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 12) throw new IllegalArgumentException("Expected 12 process arguments");

        String sourceHost = args[0];
        int sourcePort = Integer.parseInt(args[1]);
        String sourceTable = args[2];
        String targetUrl = args[3];
        String targetTable = args[4];
        Path stateDirectory = Path.of(args[5]);
        Path controlDirectory = Path.of(args[6]);
        int generation = Integer.parseInt(args[7]);
        boolean restore = Boolean.parseBoolean(args[8]);
        int serverId = Integer.parseInt(args[9]);
        String topicPrefix = args[10];
        StartupMode startupMode = StartupMode.from(args[11]);

        Files.createDirectories(stateDirectory);
        Files.createDirectories(controlDirectory);

        String sourceUsername = requiredEnvironment("YAK_CDC_SOURCE_USERNAME");
        String sourcePassword = requiredEnvironment("YAK_CDC_SOURCE_PASSWORD");
        String targetUsername = requiredEnvironment("YAK_CDC_TARGET_USERNAME");
        String targetPassword = requiredEnvironment("YAK_CDC_TARGET_PASSWORD");

        TableSchema schema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(64).copy(false))),
                List.of("ID"));
        TableId sourceId = new TableId(SOURCE_DATABASE, null, sourceTable);
        TableMetrics metrics = new TableMetrics();
        var sourceBuilder = MySqlSource.builder()
                .hostname(sourceHost)
                .port(sourcePort)
                .username(sourceUsername)
                .password(sourcePassword)
                .serverId(serverId)
                .topicPrefix(topicPrefix)
                .table(sourceId, schema);
        Source<TableRecord, ?, ?> source = startupMode.includesSnapshot()
                ? sourceBuilder.buildHybrid(1, metrics)
                : sourceBuilder.build(metrics);

        JdbcConnectionOptions targetOptions = new JdbcConnectionOptions(targetUrl, targetUsername, targetPassword);
        TableId targetId;
        try (var connection = DriverManager.getConnection(targetUrl, targetUsername, targetPassword)) {
            targetId = new TableId(connection.getCatalog(), null, targetTable);
        }
        JdbcSink sink = JdbcSink.builder()
                .withConnectionOptions(targetOptions)
                .withTablePlans(List.of(new JdbcTableWritePlan(sourceId, targetId, schema, JdbcWriteMode.UPSERT)))
                .withBatchFlushPolicy(new BatchFlushPolicy(1, Duration.ZERO))
                .build();

        Configuration configuration = new Configuration()
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(30))
                .set(CheckpointingOptions.STATE_DIRECTORY, stateDirectory.toString())
                .set(CheckpointingOptions.RESTORE_LATEST, restore);
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("mysql-cdc-crash-source", source, TableRecord.class, 1);
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "mysql-cdc-crash-sink", sink, 1);
        input.setUid("mysql-cdc-process-crash-source");
        output.setUid("mysql-cdc-process-crash-sink");
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(30, TimeUnit.SECONDS);

        try {
            if (startupMode.includesSnapshot()) {
                awaitStreaming(job, metrics);
            } else {
                awaitRunning(job);
            }
            Files.writeString(controlDirectory.resolve("ready-" + generation), startupMode.value());
            while (true) {
                JobStatus status = job.getJobStatus().get(5, TimeUnit.SECONDS);
                if (status.isTerminalState()) {
                    throw new IllegalStateException("CDC job terminated unexpectedly: " + status);
                }
                for (Path request : checkpointRequests(controlDirectory)) {
                    String fileName = request.getFileName().toString();
                    String token = fileName.substring("checkpoint-".length(), fileName.length() - ".request".length());
                    Files.deleteIfExists(request);
                    try {
                        long checkpointId =
                                ((EmbeddedJobClient) job).checkpoint().get(60, TimeUnit.SECONDS).checkpointId();
                        Files.writeString(
                                controlDirectory.resolve("checkpoint-" + token + ".ack"),
                                Long.toString(checkpointId));
                    } catch (Exception failure) {
                        Files.writeString(
                                controlDirectory.resolve("checkpoint-" + token + ".failed"),
                                failure.toString());
                        throw failure;
                    }
                }
                Thread.sleep(50);
            }
        } finally {
            // The acceptance parent normally kills this process forcibly. This path is used
            // only if startup or the control loop fails and must not hide the original error.
            if (!job.getJobStatus().get(5, TimeUnit.SECONDS).isTerminalState()) {
                try {
                    job.cancel().get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Preserve the original failure.
                }
            }
        }
    }

    private static void awaitRunning(JobClient job) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            JobStatus status = job.getJobStatus().get(5, TimeUnit.SECONDS);
            if (status == JobStatus.RUNNING) return;
            if (status.isTerminalState()) {
                throw new IllegalStateException("Latest-offset CDC stopped before startup: " + status);
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Latest-offset CDC did not start within 120 seconds");
    }

    private static void awaitStreaming(JobClient job, TableMetrics metrics) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            JobStatus status = job.getJobStatus().get(5, TimeUnit.SECONDS);
            if (status.isTerminalState()) throw new IllegalStateException("CDC job stopped before STREAMING: " + status);
            if (metrics.continuousSnapshot().phase() == TableMetrics.Phase.STREAMING) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("CDC Snapshot did not hand off to Binlog within 120 seconds");
    }

    private static List<Path> checkpointRequests(Path controlDirectory) throws Exception {
        try (Stream<Path> paths = Files.list(controlDirectory)) {
            return paths.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("checkpoint-") && name.endsWith(".request");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment variable " + name);
        return value;
    }
}
