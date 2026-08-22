package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.SubmissionMode;
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

    String yaml =
        "source:\n  password: ${SECRET:source.password}\n"
            + "sink:\n  password: ${SECRET:sink.password}\n"
            + "pipeline:\n  name: test\n";
    RealtimeEngineGateway.DeployResult result;
    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            yaml,
            "ssh-key",
            new RealtimeDeployRequest.CredentialBinding("source", "source-secret"),
            new RealtimeDeployRequest.CredentialBinding("sink", "sink-secret"))) {
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
  void propagatesSsh255AsUncertainEngineFailureAndRetainsLog() throws Exception {
    Path ssh = fakeSsh(temp.resolve("remote-uncertain.yaml"), 255);
    RealtimeSyncProperties properties = sshProperties(ssh);
    FlinkCdcEngineGateway gateway =
        new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    String yaml =
        "source:\n  password: ${SECRET:source.password}\n"
            + "sink:\n  password: ${SECRET:sink.password}\n"
            + "pipeline:\n  name: test\n";

    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            yaml,
            "ssh-uncertain",
            new RealtimeDeployRequest.CredentialBinding("source", "source-secret"),
            new RealtimeDeployRequest.CredentialBinding("sink", "sink-secret"))) {
      assertThatThrownBy(() -> gateway.deploy(request))
          .isInstanceOf(RealtimeEngineException.class)
          .satisfies(
              error -> assertThat(((RealtimeEngineException) error).uncertain()).isTrue())
          .hasMessageContaining("exitCode=255");
    }

    assertThat(temp.resolve("work/logs/submit-ssh-uncertain.log")).exists();
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

  private Path fakeSsh(Path captured, int submitExitCode) throws Exception {
    Path script = temp.resolve("gateway-fake-ssh-" + submitExitCode + ".sh");
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
