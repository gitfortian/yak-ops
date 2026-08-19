package io.yak.ops.business.datasource.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.SqlExecutionAuditDao;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditSummaryRow;
import io.yak.ops.business.datasource.dao.model.SqlStatementExecutionAuditPO;
import io.yak.ops.business.datasource.service.SqlExecutionAuditService;
import io.yak.ops.common.bean.dto.observability.SqlExecutionAuditQueryDTO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditDetailVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditSummaryVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditVO;
import io.yak.ops.common.bean.vo.observability.SqlStatementExecutionAuditVO;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** SQL execution observability read service. */
@Service
@ConditionalOnDataSourceEnabled
public class SqlExecutionAuditServiceImpl implements SqlExecutionAuditService {

  private final SqlExecutionAuditDao auditDao;

  public SqlExecutionAuditServiceImpl(SqlExecutionAuditDao auditDao) {
    this.auditDao = auditDao;
  }

  @Override
  public PagingData<SqlExecutionAuditVO> page(SqlExecutionAuditQueryDTO queryDTO) {
    SqlExecutionAuditQuery query = toQuery(queryDTO);
    IPage<SqlExecutionAuditPO> page = auditDao.selectPage(query);
    List<SqlExecutionAuditVO> records = page.getRecords().stream().map(this::executionView).toList();
    return PagingData.from(new PageData<>(
        records,
        page.getTotal(),
        page.getPages(),
        page.getCurrent(),
        page.getSize()));
  }

  @Override
  public SqlExecutionAuditDetailVO detail(String executionId) {
    if (executionId == null || executionId.isBlank()) {
      throw new IllegalArgumentException("executionId must not be blank");
    }
    SqlExecutionAuditPO execution = auditDao.selectByExecutionId(executionId.trim());
    if (execution == null) {
      throw new IllegalArgumentException("SQL execution audit not found: " + executionId.trim());
    }
    List<SqlStatementExecutionAuditVO> statements = auditDao.selectStatements(executionId).stream()
        .map(this::statementView)
        .toList();
    return new SqlExecutionAuditDetailVO(executionView(execution), statements);
  }

  @Override
  public SqlExecutionAuditSummaryVO summary(SqlExecutionAuditQueryDTO queryDTO) {
    SqlExecutionAuditQuery query = toQuery(queryDTO);
    SqlExecutionAuditSummaryRow summary = auditDao.selectSummary(query);
    long p95 = auditDao.selectP95DurationMs(query);
    List<SqlExecutionAuditSummaryVO.StatementTypeCountVO> statementTypes =
        auditDao.selectStatementTypeCounts(query).stream()
            .map(row -> new SqlExecutionAuditSummaryVO.StatementTypeCountVO(
                row.getStatementType() == null ? "OTHER" : row.getStatementType().name(),
                row.getCount()))
            .toList();
    double successRate = summary.getTotal() == 0L
        ? 0D
        : summary.getSucceeded() / (double) summary.getTotal();
    return new SqlExecutionAuditSummaryVO(
        summary.getTotal(),
        summary.getSucceeded(),
        summary.getFailed(),
        summary.getCancelled(),
        summary.getTimedOut(),
        successRate,
        summary.getAvgDurationMs(),
        summary.getMaxDurationMs(),
        p95,
        summary.getReturnedRows(),
        summary.getAffectedRows(),
        statementTypes);
  }

  private SqlExecutionAuditQuery toQuery(SqlExecutionAuditQueryDTO dto) {
    SqlExecutionAuditQueryDTO source = dto == null ? new SqlExecutionAuditQueryDTO() : dto;
    if (source.getStartedFrom() != null
        && source.getStartedTo() != null
        && source.getStartedFrom().isAfter(source.getStartedTo())) {
      throw new IllegalArgumentException("startedFrom must not be after startedTo");
    }
    return new SqlExecutionAuditQuery(
        source.getPageNo() == null ? 1 : source.getPageNo(),
        source.getPageSize() == null ? 20 : source.getPageSize(),
        source.getExecutionId(),
        source.getDataSourceId(),
        parseEnum(SqlExecutionCaller.class, source.getCaller(), "caller"),
        source.getCallerReference(),
        source.getOperatorName(),
        parseEnum(SqlExecutionStatus.class, source.getStatus(), "status"),
        parseEnum(SqlTransactionMode.class, source.getTransactionMode(), "transactionMode"),
        parseEnum(SqlStatementType.class, source.getStatementType(), "statementType"),
        source.getSqlFingerprint(),
        source.getMinDurationMs(),
        source.getStartedFrom(),
        source.getStartedTo());
  }

  private SqlExecutionAuditVO executionView(SqlExecutionAuditPO row) {
    return new SqlExecutionAuditVO(
        row.getExecutionId(),
        row.getDataSourceId(),
        name(row.getCaller()),
        row.getCallerReference(),
        row.getOperatorName(),
        name(row.getTransactionMode()),
        name(row.getStatus()),
        value(row.getStatementCount()),
        value(row.getSucceededStatementCount()),
        value(row.getReturnedRows()),
        value(row.getAffectedRows()),
        row.getStartedAt(),
        row.getFinishedAt(),
        value(row.getDurationMs()),
        row.getErrorMessage());
  }

  private SqlStatementExecutionAuditVO statementView(SqlStatementExecutionAuditPO row) {
    return new SqlStatementExecutionAuditVO(
        row.getStatementId(),
        value(row.getStatementIndex()),
        name(row.getStatementType()),
        row.getSqlFingerprint(),
        row.getSqlPreview(),
        name(row.getStatus()),
        name(row.getResultType()),
        value(row.getReturnedRows()),
        value(row.getAffectedRows()),
        Boolean.TRUE.equals(row.getTruncated()),
        row.getStartedAt(),
        row.getFinishedAt(),
        value(row.getDurationMs()),
        row.getErrorMessage());
  }

  private static <E extends Enum<E>> E parseEnum(
      Class<E> enumType,
      String value,
      String fieldName) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    try {
      return Enum.valueOf(enumType, normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Invalid " + fieldName + ": " + value,
          exception);
    }
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }

  private static long value(Long value) {
    return value == null ? 0L : value;
  }
}
