package io.yak.ops.business.audit;

import java.util.Optional;

/** Read-side contract consumed by the administrator Audit Center. */
public interface AuditQueryService {

  AuditPage<AuditOperationSummary> page(AuditOperationQuery query);

  Optional<AuditOperationDetail> detail(String operationId);

  AuditFilterOptions options();
}
