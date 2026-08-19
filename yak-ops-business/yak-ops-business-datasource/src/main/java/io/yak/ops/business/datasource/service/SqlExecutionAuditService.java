package io.yak.ops.business.datasource.service;

import io.yak.framework.common.PagingData;
import io.yak.ops.common.bean.dto.observability.SqlExecutionAuditQueryDTO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditDetailVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditSummaryVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditVO;

/** Read-side service for SQL execution history and observability aggregates. */
public interface SqlExecutionAuditService {

  PagingData<SqlExecutionAuditVO> page(SqlExecutionAuditQueryDTO query);

  SqlExecutionAuditDetailVO detail(String executionId);

  SqlExecutionAuditSummaryVO summary(SqlExecutionAuditQueryDTO query);
}
