package io.yak.ops.business.audit;

/** Stable business-level events. Keep this vocabulary intentionally small and technology agnostic. */
public enum AuditEventType {
  OPERATION_STARTED,
  RESOURCE_CREATED,
  RESOURCE_UPDATED,
  RESOURCE_DELETED,
  OPERATION_SUCCEEDED,
  OPERATION_FAILED
}
