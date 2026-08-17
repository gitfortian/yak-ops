package io.yak.ops.business.quality.service;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSpec;
import io.yak.ops.business.quality.domain.QualityDomain.RuleSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityRepository;
import io.yak.ops.business.quality.schedule.QualityScheduleCalculator;
import io.yak.ops.business.quality.schedule.QualityScheduleLifecycle;
import io.yak.ops.business.quality.service.support.QualityViewMapper;
import io.yak.ops.common.bean.dto.quality.QualityMonitorDTO;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConditionalOnQualityEnabled
@Service
public class QualityMonitorService {

  private final QualityRepository repository;
  private final QualityExecutionService executionService;
  private final QualityScheduleCalculator scheduleCalculator;
  private final QualityScheduleLifecycle scheduleLifecycle;

  public QualityMonitorService(
      QualityRepository repository,
      QualityExecutionService executionService,
      QualityScheduleCalculator scheduleCalculator,
      QualityScheduleLifecycle scheduleLifecycle) {
    this.repository = repository;
    this.executionService = executionService;
    this.scheduleCalculator = scheduleCalculator;
    this.scheduleLifecycle = scheduleLifecycle;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Page page(QualityMonitorDTO.PageRequest request) {
    QualityMonitorDTO.PageRequest normalized = request == null
        ? new QualityMonitorDTO.PageRequest(1, 20, null, null, null, null, null, null, null)
        : request;
    QualityQuery.Monitor query = new QualityQuery.Monitor(
        normalized.normalizedCurrent(), normalized.normalizedPageSize(), normalized.keyword(),
        normalized.dataSourceId(), normalized.databaseName(), normalized.databaseName() != null,
        normalized.schemaName(), normalized.schemaName() != null, normalized.tableName(),
        normalized.enabled(), normalized.lastResult());
    var result = repository.pageMonitors(query);
    return new QualityMonitorVO.Page(
        result.records().stream().map(QualityViewMapper::monitorList).toList(),
        result.total(), query.current(), query.pageSize());
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Detail get(long id) {
    return repository.findMonitor(id)
        .map(QualityViewMapper::monitor)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + id));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Settings getSettings(long id) {
    requireMonitor(id);
    return QualityViewMapper.settings(repository.findMonitorSettings(id));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<QualityMonitorVO.TableSummary> tableSummaries(
      long dataSourceId, String databaseName, String schemaName) {
    if (dataSourceId <= 0) throw new IllegalArgumentException("数据源编号无效");
    return repository.tableSummaries(dataSourceId, databaseName, schemaName).stream()
        .map(QualityViewMapper::tableSummary).toList();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Detail create(QualityMonitorDTO.SaveRequest request) {
    validateTarget(null, request);
    List<RuleSpec> rules = normalizeRules(request.rules());
    MonitorSettingsSpec settings = normalizeSettings(request.settings(), null, request.enabled());
    long id = repository.insertMonitor(toMonitorSpec(request));
    repository.upsertMonitorSettings(id, settings);
    repository.replaceRules(id, rules);
    scheduleLifecycle.sync(id);
    return get(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Detail update(long id, QualityMonitorDTO.SaveRequest request) {
    requireMonitor(id);
    validateTarget(id, request);
    List<RuleSpec> rules = normalizeRules(request.rules());
    MonitorSettingsSpec settings = normalizeSettings(
        request.settings(), repository.findMonitorSettings(id), request.enabled());
    if (!repository.updateMonitor(id, toMonitorSpec(request))) {
      throw new IllegalArgumentException("质量监控不存在：" + id);
    }
    repository.upsertMonitorSettings(id, settings);
    repository.replaceRules(id, rules);
    scheduleLifecycle.sync(id);
    return get(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean delete(long id) {
    if (repository.hasActiveExecution(id)) {
      throw new IllegalStateException("质量监控正在运行，暂时不能删除");
    }
    if (!repository.deleteMonitor(id)) {
      throw new IllegalArgumentException("质量监控不存在：" + id);
    }
    scheduleLifecycle.remove(id);
    return true;
  }

  public QualityMonitorVO.Run run(long id, String operator) {
    return executionService.run(id, operator);
  }

  private MonitorSettingsSpec normalizeSettings(
      QualityMonitorDTO.SettingsRequest request,
      MonitorSettings existing,
      Boolean monitorEnabled) {
    RunMode runMode = request == null
        ? existing == null ? RunMode.MANUAL : existing.runMode()
        : defaultValue(request.runMode(), RunMode.MANUAL);
    ScheduleFrequency frequency = request == null
        ? existing == null ? null : existing.scheduleFrequency()
        : request.scheduleFrequency();
    String scheduleTime = request == null
        ? existing == null ? null : existing.scheduleTime()
        : trimToNull(request.scheduleTime());
    ScheduleWeekday weekday = request == null
        ? existing == null ? null : existing.scheduleWeekday()
        : request.scheduleWeekday();
    String cron = request == null
        ? existing == null ? null : existing.cronExpression()
        : trimToNull(request.cronExpression());
    RuleFailureAction failureAction = request == null
        ? existing == null ? RuleFailureAction.CONTINUE : existing.ruleFailureAction()
        : defaultValue(request.ruleFailureAction(), RuleFailureAction.CONTINUE);
    boolean notifyEnabled = request == null
        ? existing != null && existing.notifyEnabled()
        : Boolean.TRUE.equals(request.notifyEnabled());
    NotifyChannel notifyChannel = request == null
        ? existing == null ? NotifyChannel.MESSAGE : existing.notifyChannel()
        : defaultValue(request.notifyChannel(), NotifyChannel.MESSAGE);
    String notifyTarget = request == null
        ? existing == null ? null : existing.notifyTarget()
        : trimToNull(request.notifyTarget());
    AlertLevel alertLevel = request == null
        ? existing == null ? AlertLevel.WARNING : existing.alertLevel()
        : defaultValue(request.alertLevel(), AlertLevel.WARNING);

    if (runMode == RunMode.MANUAL) {
      frequency = null;
      scheduleTime = null;
      weekday = null;
      cron = null;
    } else {
      if (frequency == null) throw new IllegalArgumentException("调度触发必须选择调度周期");
      switch (frequency) {
        case DAILY -> {
          requireScheduleTime(scheduleTime);
          weekday = null;
          cron = null;
        }
        case WEEKLY -> {
          requireScheduleTime(scheduleTime);
          if (weekday == null) throw new IllegalArgumentException("每周调度必须选择执行日期");
          cron = null;
        }
        case CRON -> {
          scheduleCalculator.validateCron(cron);
          scheduleTime = null;
          weekday = null;
        }
      }
    }

    if (notifyEnabled && notifyChannel != NotifyChannel.MESSAGE && notifyTarget == null) {
      throw new IllegalArgumentException(
          notifyChannel == NotifyChannel.EMAIL
              ? "邮件通知必须填写接收邮箱"
              : "Webhook 通知必须填写回调地址");
    }

    boolean enabled = monitorEnabled == null || monitorEnabled;
    LocalDateTime nextRunTime = enabled
        ? scheduleCalculator.nextRun(
            runMode, frequency, scheduleTime, weekday, cron, LocalDateTime.now())
        : null;
    return new MonitorSettingsSpec(
        runMode, frequency, scheduleTime, weekday, cron, nextRunTime,
        failureAction, notifyEnabled, notifyChannel, notifyTarget, alertLevel);
  }

  private void requireScheduleTime(String scheduleTime) {
    scheduleCalculator.nextRun(
        RunMode.SCHEDULE, ScheduleFrequency.DAILY, scheduleTime, null, null, LocalDateTime.now());
  }

  private void validateTarget(Long excludeId, QualityMonitorDTO.SaveRequest request) {
    if (request.dataSourceId() == null || request.dataSourceId() <= 0) {
      throw new IllegalArgumentException("请选择有效的数据源");
    }
    String tableName = request.tableName().trim();
    if (!repository.existsTableAssetTarget(
        request.dataSourceId(), request.databaseName(), request.schemaName(), tableName)) {
      throw new IllegalStateException("当前数据表尚未注册，请先在按表配置页面注册数据表");
    }
    if (repository.existsMonitorForTarget(
        excludeId, request.dataSourceId(), request.databaseName(), request.schemaName(), tableName)) {
      throw new IllegalStateException("当前数据表已经创建质量监控，请直接进入监控详情");
    }
    validateWhereClause(request.whereClause());
  }

  private List<RuleSpec> normalizeRules(List<QualityMonitorDTO.SaveRuleRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new IllegalArgumentException("至少需要添加一条质量规则");
    }
    List<RuleSpec> result = new ArrayList<>();
    for (QualityMonitorDTO.SaveRuleRequest request : requests) {
      Template template = repository.findTemplate(request.templateId())
          .orElseThrow(() -> new IllegalArgumentException("规则模板不存在：" + request.templateId()));
      String columnName = trimToNull(request.columnName());
      if (template.scope() == RuleScope.COLUMN && columnName == null) {
        throw new IllegalArgumentException(template.name() + " 必须选择检查字段");
      }

      ComparisonOperator operator;
      BigDecimal threshold;
      BigDecimal thresholdEnd = request.thresholdEnd();
      List<String> enumValues = normalizeEnumValues(request.enumValues());
      String customSql = trimToNull(request.customSql());

      if (template.ruleType() == RuleType.COLUMN_RANGE) {
        operator = ComparisonOperator.EQ;
        threshold = required(request.threshold(), template.name() + " 缺少最小值");
        thresholdEnd = required(request.thresholdEnd(), template.name() + " 缺少最大值");
        if (threshold.compareTo(thresholdEnd) > 0) {
          throw new IllegalArgumentException(template.name() + " 最小值不能大于最大值");
        }
      } else if (template.ruleType() == RuleType.COLUMN_ENUM) {
        operator = ComparisonOperator.EQ;
        threshold = BigDecimal.ZERO;
        thresholdEnd = null;
        if (enumValues.isEmpty()) {
          throw new IllegalArgumentException(template.name() + " 至少需要一个允许值");
        }
      } else {
        operator = request.operator() == null
            ? defaultOperator(template.ruleType())
            : ComparisonOperator.fromValue(request.operator());
        threshold = request.threshold() == null
            ? defaultThreshold(template.ruleType())
            : request.threshold();
        if (operator == ComparisonOperator.BETWEEN && thresholdEnd == null) {
          throw new IllegalArgumentException(template.name() + " 缺少区间最大值");
        }
      }

      if (template.ruleType() == RuleType.CUSTOM_SQL) validateCustomSql(customSql);
      else customSql = null;

      result.add(new RuleSpec(
          template.id(), template.code(), request.name().trim(), template.ruleType(), template.scope(),
          template.dimension(), columnName, operator, threshold, thresholdEnd, enumValues, customSql,
          request.enabled() == null || request.enabled()));
    }
    return result;
  }

  private MonitorSpec toMonitorSpec(QualityMonitorDTO.SaveRequest request) {
    return new MonitorSpec(
        request.name().trim(), trimToNull(request.description()), request.dataSourceId(),
        request.dataSourceName().trim(), trimToNull(request.databaseName()),
        trimToNull(request.schemaName()), request.tableName().trim(),
        trimToNull(request.whereClause()), request.owner().trim(),
        request.enabled() == null || request.enabled());
  }

  private void requireMonitor(long id) {
    repository.findMonitor(id)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + id));
  }

  private ComparisonOperator defaultOperator(RuleType ruleType) {
    return switch (ruleType) {
      case TABLE_ROW_COUNT -> ComparisonOperator.GT;
      case COLUMN_NOT_NULL, COLUMN_UNIQUE -> ComparisonOperator.GTE;
      case CUSTOM_SQL, COLUMN_RANGE, COLUMN_ENUM -> ComparisonOperator.EQ;
    };
  }

  private BigDecimal defaultThreshold(RuleType ruleType) {
    return switch (ruleType) {
      case TABLE_ROW_COUNT -> BigDecimal.ZERO;
      case COLUMN_NOT_NULL, COLUMN_UNIQUE -> BigDecimal.valueOf(100);
      case CUSTOM_SQL, COLUMN_RANGE, COLUMN_ENUM -> BigDecimal.ZERO;
    };
  }

  private List<String> normalizeEnumValues(List<String> values) {
    if (values == null) return List.of();
    return values.stream().map(QualityMonitorService::trimToNull)
        .filter(value -> value != null).distinct().toList();
  }

  private void validateWhereClause(String value) {
    String filter = trimToNull(value);
    if (filter == null) return;
    String upper = filter.toUpperCase();
    if (filter.contains(";") || upper.contains("--") || upper.contains("/*")
        || upper.matches("(?s).*\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\\b.*")) {
      throw new IllegalArgumentException("数据范围仅允许填写 WHERE 条件片段");
    }
  }

  private void validateCustomSql(String sql) {
    if (sql == null) throw new IllegalArgumentException("自定义 SQL 不能为空");
    String normalized = sql.trim();
    if (normalized.endsWith(";")) normalized = normalized.substring(0, normalized.length() - 1).trim();
    if (!normalized.toUpperCase().startsWith("SELECT ") || normalized.contains(";")) {
      throw new IllegalArgumentException("自定义 SQL 仅允许执行单条 SELECT 查询");
    }
  }

  private static BigDecimal required(BigDecimal value, String message) {
    if (value == null) throw new IllegalArgumentException(message);
    return value;
  }

  private static <T> T defaultValue(T value, T fallback) {
    return value == null ? fallback : value;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
