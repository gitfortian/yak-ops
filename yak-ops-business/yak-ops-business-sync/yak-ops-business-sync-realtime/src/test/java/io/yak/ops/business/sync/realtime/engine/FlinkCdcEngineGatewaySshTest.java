package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.SubmissionMode;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlinkCdcEngineGatewaySshTest {

  private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

  @TempDir Path temp;
  private HttpServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/overview", exchange -> json(exchange, 200, "{\"jobs-running\":0}"));
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void submitsThroughSshWithoutCreatingLocalPipelineFile() throws Exception {
    Path captured = temp.resolve("remote-pipeline.yaml");
    Path ssh = fakeSsh(captured, 0);
    RealtimeSyncProperties properties = sshProperties(ssh);
    FlinkCdcEngineGateway gateway =
        new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);

    String yaml = pipelineYaml();
    RealtimeEngineGateway.DeployResult result;
    try (RealtimeDeployRequest request = request(yaml, "ssh-key")) {
      result = gateway.deploy(request);
    }

    assertThat(result.jobId()).isEqualTo(JOB_ID);
    assertThat(gateway.capabilities().path("submissionMode").asText()).isEqualTo("SSH");
    assertThat(gateway.capabilities().path("submissionEndpoint").asText())
        .isEqualTo("flink@10.0.0.20:22");
    assertThat(gateway.capabilities().path("restTransport").asText()).isEqualTo("DIRECT");
    assertThat(Files.readString(captured, StandardCharsets.UTF_8))
        .contains("password: 'source-secret'")
        .contains("password: 'sink-secret'")
        .doesNotContain("${SECRET:");
    assertThat(temp.resolve("work/pipelines")).doesNotExist();
    assertThat(temp.resolve("work/logs/submit-ssh-key.log")).exists();
    assertThat(temp.resolve("work/logs/" + JOB_ID + ".submit.log")).exists();
  }

  @Test
  void usesEnvironmentScopedSshSettingsInsteadOfApplicationFallback() throws Exception {
    Path captured = temp.resolve("environment-pipeline.yaml");
    Path ssh = fakeSsh(captured, 0);
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setSubmissionMode(SubmissionMode.SSH);
    properties.setWorkDirectory(temp.resolve("work-env").toString());
    properties.setSubmitTimeout(Duration.ofSeconds(5));
    // Deliberately unusable application fallback. The explicit environment must win.
    properties.getSsh().setExecutable(temp.resolve("missing-ssh").toString());
    properties.getSsh().setHost("fallback.invalid");
    properties.getSsh().setUser("fallback");

    FlinkCdcEngineGateway gateway =
        new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    ComputeEnvironmentSnapshot environment = sshEnvironment(ssh, "10.0.0.88", 2222);

    RealtimeEngineGateway.DeployResult result;
    try (RealtimeDeployRequest request = request(pipelineYaml(), "environment-key")) {
      result = gateway.deploy(environment, request);
    }

    assertThat(result.jobId()).isEqualTo(JOB_ID);
    assertThat(gateway.capabilities(environment).path("submissionEndpoint").asText())
        .isEqualTo("flink@10.0.0.88:2222");
    assertThat(Files.readString(captured, StandardCharsets.UTF_8))
        .contains("password: 'source-secret'")
        .doesNotContain("${SECRET:");
  }

  @Test
  void propagatesSsh255AsUncertainEngineFailureAndRetainsLog() throws Exception {
    Path ssh = fakeSsh(temp.resolve("remote-uncertain.yaml"), 255);
    RealtimeSyncProperties properties = sshProperties(ssh);
    FlinkCdcEngineGateway gateway =
        new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);

    try (RealtimeDeployRequest request = request(pipelineYaml(), "ssh-uncertain")) {
      assertThatThrownBy(() -> gateway.deploy(request))
          .isInstanceOf(RealtimeEngineException.class)
          .satisfies(
              error -> assertThat(((RealtimeEngineException) error).uncertain()).isTrue())
          .hasMessageContaining("exitCode=255");
    }

    assertThat(temp.resolve("work/logs/submit-ssh-uncertain.log")).exists();
  }

  private ComputeEnvironmentSnapshot sshEnvironment(Path executable, String host, int port) {
    int restPort = server.getAddress().getPort();
    RuntimeConfig config =
        new RuntimeConfig(
            "http://127.0.0.1:" + restPort,
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig(
                executable.toString(),
                host,
                port,
                "flink",
                null,
                null,
                true,
                1,
                "127.0.0.1",
                restPort));
    return new ComputeEnvironmentSnapshot(
        9L,
        "ssh-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_SSH,
        config,
        3);
  }

  private RealtimeSyncProperties sshProperties(Path executable) {
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setSubmissionMode(SubmissionMode.SSH);
    properties.setRestUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setFlinkHome("/opt/flink");
    properties.setFlinkCdcHome("/opt/flink-cdc");
    properties.setWorkDirectory(temp.resolve("work").toString());
    properties.setSubmitTimeout(Duration.ofSeconds(5));
    properties.getSsh().setExecutable(executable.toString());
    properties.getSsh().setHost("10.0.0.20");
    properties.getSsh().setPort(22);
    properties.getSsh().setUser("flink");
    properties.getSsh().setConnectTimeout(Duration.ofSeconds(1));
    properties.getSsh().setRemoteRestAddress("127.0.0.1");
    properties.getSsh().setRemoteRestPort(server.getAddress().getPort());
    return properties;
  }

  private RealtimeDeployRequest request(String yaml, String key) {
    return new RealtimeDeployRequest(
        yaml,
        key,
        new RealtimeDeployRequest.CredentialBinding("source", "source-secret"),
        new RealtimeDeployRequest.CredentialBinding("sink", "sink-secret"));
  }

  private String pipelineYaml() {
    return "source:\n  password: ${SECRET:source.password}\n"
        + "sink:\n  password: ${SECRET:sink.password}\n"
        + "pipeline:\n  name: test\n";
  }

  private Path fakeSsh(Path captured, int submitExitCode) throws Exception {
    Path script = temp.resolve("gateway-fake-ssh-" + submitExitCode + "-" + captured.getFileName() + ".sh");
    String content =
        "#!/bin/sh\n"
            + "case \"$*\" in\n"
            + "  *YAK_REALTIME_SSH_READY*) exit 0 ;;\n"
            + "esac\n"
            + "cat > "
            + shell(captured)
            + "\n"
            + "echo 'Pipeline has been submitted to cluster.'\n"
            + "echo 'Job ID: "
            + JOB_ID
            + "'\n"
            + "exit "
            + submitExitCode
            + "\n";
    Files.writeString(script, content, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    return script;
  }

  private String shell(Path path) {
    return "'" + path.toString().replace("'", "'\"'\"'") + "'";
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
