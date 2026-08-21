package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** HTTP implementation matching yak-flink-cdc-connectors/yak-cdc-runtime exactly. */
@Component
public class HttpRealtimeEngineGateway implements RealtimeEngineGateway {

  private final HttpClient client;
  private final ObjectMapper json;
  private final RealtimeSyncProperties properties;

  public HttpRealtimeEngineGateway(
      @Qualifier("realtimeHttpClient") HttpClient client,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      RealtimeSyncProperties properties) {
    this.client = client;
    this.json = json;
    this.properties = properties;
  }

  @Override
  public JsonNode health() {
    return get("/health", false);
  }

  @Override
  public JsonNode capabilities() {
    return get("/capabilities", false);
  }

  @Override
  public ValidationResult validate(String pipelineYaml) {
    Response response = yaml("/validate", pipelineYaml, false, null);
    if (response.status() == 422) {
      throw rejection("Runtime 校验失败", response.body());
    }
    requireStatus(response, 200);
    boolean valid = response.body().path("valid").asBoolean(false);
    if (!valid) {
      throw rejection("Runtime 校验失败", response.body());
    }
    return new ValidationResult(true, response.body().path("deliverySemantics").asText(null));
  }

  @Override
  public DeployResult deploy(String pipelineYaml, String localIdempotencyKey) {
    Response response = yaml("/deploy", pipelineYaml, true, localIdempotencyKey);
    if (response.status() == 409) {
      throw new GatewayException(Kind.CONFLICT, "Runtime 已有活动任务", false, response.status(), null);
    }
    if (response.status() == 422) {
      throw rejection("Runtime 拒绝 Pipeline", response.body());
    }
    requireStatus(response, 202);
    String jobId = response.body().path("jobId").asText(null);
    if (jobId == null || jobId.isBlank()) {
      throw new GatewayException(Kind.PROTOCOL, "Runtime 未返回 jobId", true, 202, null);
    }
    return new DeployResult(jobId, response.body().path("deliverySemantics").asText(null));
  }

  @Override
  public RuntimeStatus status() {
    JsonNode body = get("/status", true);
    String raw = body.path("status").asText("UNKNOWN").toUpperCase(Locale.ROOT);
    RuntimeStatus.State state;
    try {
      state = RuntimeStatus.State.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      state = RuntimeStatus.State.UNKNOWN;
    }
    return new RuntimeStatus(
        body.path("jobId").isNull() ? null : body.path("jobId").asText(null), state);
  }

  @Override
  public void stop() {
    try {
      HttpRequest request = request("/stop").POST(HttpRequest.BodyPublishers.noBody()).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw httpError(response.statusCode(), parse(response.body(), true), true);
      }
    } catch (HttpTimeoutException exception) {
      throw new GatewayException(Kind.UNAVAILABLE, "Runtime 停止结果不确定", true, null, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable("Runtime 停止请求被中断", true, exception);
    } catch (IOException exception) {
      throw unavailable("Runtime 停止连接失败", true, exception);
    }
  }

  @Override
  public String logs(int tailLines) {
    int tail = Math.max(1, Math.min(tailLines, properties.getMaxLogLines()));
    JsonNode body = get("/logs?tail=" + tail, false);
    return body.path("logs").asText("");
  }

  private JsonNode get(String path, boolean uncertainOnFailure) {
    try {
      HttpResponse<String> response =
          client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
      JsonNode body = parse(response.body(), false);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw httpError(response.statusCode(), body, uncertainOnFailure);
      }
      return body;
    } catch (HttpTimeoutException exception) {
      throw unavailable("Runtime 请求超时", uncertainOnFailure, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable("Runtime 请求被中断", uncertainOnFailure, exception);
    } catch (IOException exception) {
      throw unavailable("Runtime 连接失败", uncertainOnFailure, exception);
    }
  }

  private Response yaml(
      String path, String body, boolean uncertainOnFailure, String idempotencyKey) {
    try {
      HttpRequest.Builder request = request(path).header("Content-Type", "text/yaml");
      if (idempotencyKey != null && !idempotencyKey.isBlank()) {
        // Current Runtime does not guarantee server-side idempotency. This header is advisory;
        // Yak Ops still prevents re-submission after an uncertain response.
        request.header("Idempotency-Key", idempotencyKey);
      }
      HttpResponse<String> response =
          client.send(
              request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
              HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(), parse(response.body(), uncertainOnFailure));
    } catch (HttpTimeoutException exception) {
      throw unavailable("Runtime 提交超时，结果不确定", uncertainOnFailure, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable("Runtime 提交被中断", uncertainOnFailure, exception);
    } catch (IOException exception) {
      throw unavailable("Runtime 连接失败", uncertainOnFailure, exception);
    }
  }

  private HttpRequest.Builder request(String path) {
    String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
    return HttpRequest.newBuilder(URI.create(baseUrl + path))
        .timeout(properties.getRequestTimeout())
        .header("Accept", "application/json");
  }

  private JsonNode parse(String value, boolean uncertain) {
    try {
      return value == null || value.isBlank() ? json.createObjectNode() : json.readTree(value);
    } catch (Exception exception) {
      throw new GatewayException(Kind.PROTOCOL, "Runtime 返回了无效 JSON", uncertain, null, exception);
    }
  }

  private void requireStatus(Response response, int expected) {
    if (response.status() != expected) {
      throw httpError(response.status(), response.body(), false);
    }
  }

  private GatewayException rejection(String fallback, JsonNode body) {
    return new GatewayException(
        Kind.VALIDATION, body.path("error").asText(fallback), false, 422, null);
  }

  private GatewayException httpError(int status, JsonNode body, boolean uncertain) {
    String message = body.path("error").asText("Runtime HTTP " + status);
    return new GatewayException(Kind.HTTP, message, uncertain, status, null);
  }

  private GatewayException unavailable(String message, boolean uncertain, Exception cause) {
    return new GatewayException(Kind.UNAVAILABLE, message, uncertain, null, cause);
  }

  private record Response(int status, JsonNode body) {}

  public enum Kind {
    VALIDATION,
    CONFLICT,
    UNAVAILABLE,
    PROTOCOL,
    HTTP
  }

  public static final class GatewayException extends RuntimeException {
    private final Kind kind;
    private final boolean uncertain;
    private final Integer httpStatus;

    GatewayException(
        Kind kind, String message, boolean uncertain, Integer httpStatus, Throwable cause) {
      super(message, cause);
      this.kind = kind;
      this.uncertain = uncertain;
      this.httpStatus = httpStatus;
    }

    public Kind kind() {
      return kind;
    }

    public boolean uncertain() {
      return uncertain;
    }

    public Integer httpStatus() {
      return httpStatus;
    }
  }
}
