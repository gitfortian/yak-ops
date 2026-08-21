package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlinkCdcEngineGatewayTest {

  private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

  @TempDir Path temp;

  private HttpServer server;
  private FlinkCdcEngineGateway gateway;
  private final AtomicBoolean cancelled = new AtomicBoolean();

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/overview", exchange -> json(exchange, 200, "{\"jobs-running\":1}"));
    server.createContext("/jobs/", this::jobApi);
    server.start();

    Path cdcHome = Files.createDirectories(temp.resolve("flink-cdc/bin")).getParent();
    Path cli = cdcHome.resolve("bin/flink-cdc.sh");
    Files.writeString(
        cli,
        "#!/bin/sh\n"
            + "if grep -q '\\${SECRET:' \"$1\"; then exit 9; fi\n"
            + "echo 'Pipeline has been submitted to cluster.'\n"
            + "echo 'Job ID: "
            + JOB_ID
            + "'\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setRestUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setFlinkHome(temp.resolve("flink").toString());
    properties.setFlinkCdcHome(cdcHome.toString());
    properties.setWorkDirectory(temp.resolve("work").toString());
    gateway = new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void submitsWithCliThenUsesJobScopedRestApis() throws Exception {
    String yaml =
        "source:\n  password: ${SECRET:source.password}\n"
            + "sink:\n  password: ${SECRET:sink.password}\n"
            + "pipeline:\n  name: test\n";
    RealtimeEngineGateway.DeployResult deployed;
    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            yaml,
            "test-key",
            new RealtimeDeployRequest.CredentialBinding("source", "source-secret"),
            new RealtimeDeployRequest.CredentialBinding("sink", "sink-secret"))) {
      deployed = gateway.deploy(request);
    }

    assertThat(deployed.jobId()).isEqualTo(JOB_ID);
    assertThat(gateway.status(JOB_ID).state())
        .isEqualTo(RealtimeEngineGateway.RuntimeStatus.State.RUNNING);
    assertThat(gateway.checkpoints(JOB_ID).path("counts").path("completed").asInt()).isEqualTo(3);
    assertThat(gateway.metrics(JOB_ID).path("metrics").isArray()).isTrue();
    assertThat(gateway.logs(JOB_ID, 20))
        .contains("Pipeline has been submitted")
        .contains("Flink job exceptions")
        .doesNotContain("source-secret", "sink-secret");

    gateway.stop(JOB_ID);
    assertThat(cancelled).isTrue();
    try (var files = Files.list(temp.resolve("work/pipelines"))) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void exposesCliAndFlinkCapabilities() {
    assertThat(gateway.health().path("jobs-running").asInt()).isEqualTo(1);
    assertThat(gateway.capabilities().path("deployEnabled").asBoolean()).isTrue();
    assertThat(gateway.capabilities().path("checkpointsApi").asBoolean()).isTrue();
  }

  @Test
  void redactsFailedSubmitOutputAndDeletesPipelineFile() throws Exception {
    Path cli = temp.resolve("flink-cdc/bin/flink-cdc.sh");
    Files.writeString(
        cli,
        "#!/bin/sh\necho 'password=source-secret connector rejected configuration'\nexit 7\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));
    String yaml =
        "source:\n  password: ${SECRET:source.password}\n"
            + "sink:\n  password: ${SECRET:sink.password}\n"
            + "pipeline:\n  name: test\n";

    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            yaml,
            "failed-key",
            new RealtimeDeployRequest.CredentialBinding("source", "source-secret"),
            new RealtimeDeployRequest.CredentialBinding("sink", "sink-secret"))) {
      assertThatThrownBy(() -> gateway.deploy(request))
          .isInstanceOf(RealtimeEngineException.class)
          .hasMessageContaining("exitCode=7")
          .hasMessageContaining("password=******")
          .hasMessageNotContaining("source-secret")
          .hasMessageNotContaining("sink-secret");
    }
    try (var files = Files.list(temp.resolve("work/pipelines"))) {
      assertThat(files).isEmpty();
    }
  }

  private void jobApi(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if ("PATCH".equals(exchange.getRequestMethod()) && path.equals("/jobs/" + JOB_ID)) {
      cancelled.set(true);
      json(exchange, 202, "{}");
    } else if (path.endsWith("/exceptions")) {
      json(exchange, 200, "{\"root-exception\":null,\"all-exceptions\":[]}");
    } else if (path.endsWith("/checkpoints")) {
      json(exchange, 200, "{\"counts\":{\"completed\":3}}");
    } else if (path.endsWith("/metrics")) {
      json(exchange, 200, "[]");
    } else if (path.equals("/jobs/" + JOB_ID)) {
      json(exchange, 200, "{\"jid\":\"" + JOB_ID + "\",\"state\":\"RUNNING\"}");
    } else {
      json(exchange, 404, "{}");
    }
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
