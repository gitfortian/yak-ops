package io.yak.ops.boot.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Sends real authenticated public API requests with explicit Workspace scoping. */
final class TaskApiClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private String workspace;

    TaskApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    void workspace(String id) {
        workspace = id;
    }

    String workspace() {
        return workspace;
    }

    JsonNode call(String method, String path) {
        return call(method, path, null, 200);
    }

    JsonNode call(String method, String path, Object body) {
        return call(method, path, body, 200);
    }

    JsonNode call(String method, String path, Object body, int expectedStatus) {
        return send(method, path, body, expectedStatus, null);
    }

    JsonNode submit(String definition) {
        return send("POST", "/api/v1/task-definitions/" + definition + "/instances", null, 202,
                java.util.UUID.randomUUID().toString());
    }

    JsonNode submit(String definition, String idempotencyKey, int expectedStatus) {
        return send("POST", "/api/v1/task-definitions/" + definition + "/instances", null,
                expectedStatus, idempotencyKey);
    }

    private JsonNode send(String method, String path, Object payload, int expectedStatus, String idempotencyKey) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json");
            if (workspace != null) request.header("X-Workspace-Id", workspace);
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expectedStatus) {
                throw new AssertionError(method + " " + path + " returned HTTP " + response.statusCode()
                        + "; expected " + expectedStatus);
            }
            JsonNode result = response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
            if (response.statusCode() < 300 && result.path("code").asInt(-1) != 200) {
                throw new AssertionError(method + " " + path + " returned unexpected business error "
                        + result.path("code").asInt(-1));
            }
            return result.path("data");
        } catch (IOException failure) {
            throw new IllegalStateException("HTTP test request unavailable: " + method + " " + path, failure);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }

    String download(String path) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
            if (workspace != null) request.header("X-Workspace-Id", workspace);
            HttpResponse<String> response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AssertionError("Download " + path + " returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException failure) {
            throw new IllegalStateException("Download unavailable: " + path, failure);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }
}
