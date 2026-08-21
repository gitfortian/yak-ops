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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpRealtimeEngineGatewayTest {

  private HttpServer server;
  private HttpRealtimeEngineGateway gateway;
  private final AtomicReference<String> stopBody = new AtomicReference<>();
  private final AtomicReference<String> deployBody = new AtomicReference<>();
  private final AtomicReference<String> deployKey = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/capabilities",
        exchange ->
            json(exchange, 200, "{\"runtimeVersion\":\"0.1.0-phase0\",\"sources\":[\"mysql\"]}"));
    server.createContext(
        "/validate",
        exchange ->
            json(exchange, 200, "{\"valid\":true,\"deliverySemantics\":\"at-least-once\"}"));
    server.createContext(
        "/deploy",
        exchange -> {
          deployKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
          deployBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          json(exchange, 202, "{\"jobId\":\"job-1\",\"deliverySemantics\":\"at-least-once\"}");
        });
    server.createContext(
        "/status", exchange -> json(exchange, 200, "{\"jobId\":\"job-1\",\"status\":\"RUNNING\"}"));
    server.createContext(
        "/stop",
        exchange -> {
          stopBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          json(exchange, 200, "{\"status\":\"stopping\"}");
        });
    server.createContext("/logs", exchange -> json(exchange, 200, "{\"logs\":\"line\"}"));
    server.start();
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    gateway =
        new HttpRealtimeEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void followsFixedRuntimeContract() {
    assertThat(gateway.capabilities().path("runtimeVersion").asText()).isEqualTo("0.1.0-phase0");
    assertThat(gateway.validate("source:\n  type: mysql").valid()).isTrue();
    try (RealtimeDeployRequest request = request()) {
      assertThat(gateway.deploy(request).jobId()).isEqualTo("job-1");
    }
    assertThat(deployKey.get()).isEqualTo("local-key");
    assertThat(new ObjectMapper().readTree(deployBody.get()).path("pipelineYaml").asText())
        .isEqualTo("source:\n  type: mysql");
    assertThat(deployBody.get()).contains("source-secret", "sink-secret");
    assertThat(gateway.status().state())
        .isEqualTo(RealtimeEngineGateway.RuntimeStatus.State.RUNNING);
    assertThat(gateway.logs(20)).isEqualTo("line");
    gateway.stop();
    assertThat(stopBody.get()).isEmpty();
  }

  @Test
  void malformedDeployResponseIsTreatedAsUncertain() {
    server.removeContext("/deploy");
    server.createContext("/deploy", exchange -> json(exchange, 202, "not-json"));

    assertThatThrownBy(
            () -> {
              try (RealtimeDeployRequest request = request()) {
                gateway.deploy(request);
              }
            })
        .isInstanceOf(HttpRealtimeEngineGateway.GatewayException.class)
        .satisfies(
            exception ->
                assertThat(((HttpRealtimeEngineGateway.GatewayException) exception).uncertain())
                    .isTrue());
  }

  private RealtimeDeployRequest request() {
    return new RealtimeDeployRequest(
        "source:\n  type: mysql",
        "local-key",
        new RealtimeDeployRequest.CredentialBinding("reader", "source-secret"),
        new RealtimeDeployRequest.CredentialBinding("writer", "sink-secret"));
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
