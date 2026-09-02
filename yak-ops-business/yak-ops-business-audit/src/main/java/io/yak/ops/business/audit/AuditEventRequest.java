package io.yak.ops.business.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured event command with an optional idempotency key for async redelivery/retry. */
public record AuditEventRequest(
    AuditEventType type,
    String eventKey,
    String message,
    String reasonCode,
    Map<String, ?> payload) {

  public AuditEventRequest {
    if (type == null) throw new IllegalArgumentException("type must not be null");
    eventKey = normalize(eventKey);
    message = normalize(message);
    reasonCode = normalize(reasonCode);
    payload =
        payload == null || payload.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }

  public static AuditEventRequest of(
      AuditEventType type, String eventKey, String message, Map<String, ?> payload) {
    return new AuditEventRequest(type, eventKey, message, null, payload);
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
