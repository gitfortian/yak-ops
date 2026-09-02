package io.yak.ops.business.audit;

/** Entry point shared by business modules. Audit persistence must never become a business dependency. */
public interface BusinessAuditService {
  AuditOperationHandle start(AuditOperationRequest request);
}
