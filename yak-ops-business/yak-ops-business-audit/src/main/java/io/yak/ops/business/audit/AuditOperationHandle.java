package io.yak.ops.business.audit;

import java.util.Map;

/** Handle for appending events and completing one business operation. Implementations are fail-open. */
public interface AuditOperationHandle {

  String operationId();

  void resource(String resourceId, String resourceName);

  void event(AuditEventType type, String message, Map<String, ?> payload);

  void success(String summary);

  void failure(String reasonCode, Throwable cause);
}
