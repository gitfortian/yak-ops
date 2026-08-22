package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlinkJobDiscoveryClientTest {

  private static final String JOB_ID = "0123456789abcdef0123456789abcdef";
  private HttpServer server;
  private FlinkJobDiscoveryClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/jobs/overview",
        exchange -> {
          String body =
              "{\"jobs\":["
                  + "{\"jid\":\"" + JOB_ID + "\",\"name\":\"yak-rt-target\",\"state\":\"RUNNING\"},"
                  + "{\"jid\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"other\",\"state\":\"RUNNING\"}]}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setRestUrl("http://127.0.0.1:" + server.getAddress().getPort());
    client =
        new FlinkJobDiscoveryClient(
            HttpClient.newHttpClient(), new ObjectMapper(), properties);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void findsOnlyExactRuntimeName() {
    assertThat(client.findJobIds("yak-rt-target")).containsExactly(JOB_ID);
    assertThat(client.findJobIds("yak-rt")).isEmpty();
  }
}
