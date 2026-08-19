package io.yak.ops.business.dataservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.service.DataServiceAccessService.AccessContext;
import io.yak.ops.business.dataservice.service.DataServiceRuntimeService.RuntimeSnapshot;
import io.yak.ops.business.dataservice.service.support.DataServiceSqlCompiler;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Data Service definition, protected read-only SQL runtime and invocation audit. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceService {

  private static final int DEFAULT_MAX_ROWS = 1_000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int DEFAULT_CACHE_TTL_SECONDS = 60;
  private static final int DEFAULT_CACHE_MAX_ENTRIES = 200;
  private static final int DEFAULT_CIRCUIT_FAILURE_THRESHOLD = 5;
  private static final int DEFAULT_CIRCUIT_RECOVERY_SECONDS = 30;
  private static final String RUNTIME_PREFIX = "/api/v1/data-service/runtime";

  private final DataServiceApiMapper apiMapper;
  private final DataServiceCallLogMapper callLogMapper;
  private final SqlExecutionRuntime sqlExecutionRuntime;
  private final DataServiceSqlCompiler sqlCompiler;
  private final ObjectMapper objectMapper;
  private final DataServiceAccessService accessService;
  private final DataServiceRuntimeService runtimeService;

  public List<ApiView> list() {
    return apiMapper.selectList(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .orderByDesc(DataServiceApiPO::getUpdateTime)
                .orderByDesc(DataServiceApiPO::getId))
        .stream()
        .map(this::toView)
        .toList();
  }

  public ApiView get(Long id) {
    return toView(requireApi(id));
  }

  public Optional<ApiView> findBySource(String sourceType, String sourceRef) {
    SourceKey source = normalizeSourceKey(sourceType, sourceRef);
    DataServiceApiPO api = apiMapper.selectOne(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getSourceType, source.sourceType())
            .eq(DataServiceApiPO::getSourceRef, source.sourceRef())
            .last("LIMIT 1"));
    return Optional.ofNullable(api).map(this::toView);
  }

  /** Updates only service-facing settings; executable SQL and datasource are never accepted here. */
  @Transactional
  public ApiView updateSettings(Long id, ServiceSettingsInput input) {
    DataServiceApiPO api = requireApi(id);
    ServiceSettingsInput normalized = normalizeSettings(input, api);
    ensurePathAvailable(id, normalized.path());
    applySettings(api, normalized);
    api.setUpdateTime(LocalDateTime.now());
    apiMapper.updateById(api);
    runtimeService.invalidate(api.getId());
    return toView(api);
  }

  /**
   * Creates or refreshes the stable Data Service bound to one upstream asset.
   *
   * <p>This is the only write path that accepts executable SQL and datasource identity. Those values
   * are copied from a server-resolved immutable source release and become the Runtime snapshot. Access
   * control and Runtime policies are intentionally preserved across source refreshes.
   */
  @Transactional
  public ApiView saveFromSource(
      SourceSnapshot source,
      RuntimeDefinition runtimeDefinition,
      ServiceSettingsInput settings) {
    SourceSnapshot normalizedSource = normalizeSource(source);
    RuntimeDefinition normalizedRuntime = normalizeRuntimeDefinition(runtimeDefinition);
    Optional<ApiView> existing = findBySource(
        normalizedSource.sourceType(), normalizedSource.sourceRef());
    Long id = existing.map(ApiView::id).orElse(null);
    boolean creating = id == null;
    DataServiceApiPO api = creating ? new DataServiceApiPO() : requireApi(id);
    ServiceSettingsInput normalizedSettings = normalizeSettings(settings, creating ? null : api);
    ensurePathAvailable(id, normalizedSettings.path());

    applySettings(api, normalizedSettings);
    api.setDataSourceId(normalizedRuntime.dataSourceId());
    api.setSqlText(normalizedRuntime.sql());
    if (!StringUtils.hasText(api.getAuthMode())) api.setAuthMode("NONE");
    initializeRuntimeDefaults(api, creating);
    api.setSourceType(normalizedSource.sourceType());
    api.setSourceRef(normalizedSource.sourceRef());
    api.setSourceRevisionId(normalizedSource.sourceRevisionId());
    api.setSourceRevisionNo(normalizedSource.sourceRevisionNo());
    api.setUpdateTime(LocalDateTime.now());

    if (creating) {
      api.setCreateTime(api.getUpdateTime());
      apiMapper.insert(api);
    } else {
      apiMapper.updateById(api);
    }
    runtimeService.invalidate(api.getId());
    return toView(api);
  }

  @Transactional
  public void delete(Long id) {
    requireApi(id);
    accessService.deleteKeysForApi(id);
    if (apiMapper.deleteById(id) == 0) {
      throw new IllegalArgumentException("数据服务不存在：" + id);
    }
    runtimeService.remove(id);
  }

  @Transactional
  public ApiView setEnabled(Long id, boolean enabled) {
    DataServiceApiPO api = requireApi(id);
    api.setEnabled(enabled);
    api.setUpdateTime(LocalDateTime.now());
    apiMapper.updateById(api);
    if (!enabled) runtimeService.invalidate(id);
    return toView(api);
  }

  public RuntimeSnapshot runtimeStatus(Long id) {
    return runtimeService.snapshot(requireApi(id));
  }

  @Transactional
  public RuntimeSnapshot updateRuntimeConfig(Long id, RuntimeConfigInput input) {
    DataServiceApiPO api = requireApi(id);
    RuntimeConfigInput normalized = normalizeRuntimeConfig(input);
    api.setCacheEnabled(normalized.cacheEnabled());
    api.setCacheTtlSeconds(normalized.cacheTtlSeconds());
    api.setCacheMaxEntries(normalized.cacheMaxEntries());
    api.setCircuitBreakerEnabled(normalized.circuitBreakerEnabled());
    api.setCircuitFailureThreshold(normalized.failureThreshold());
    api.setCircuitRecoverySeconds(normalized.recoverySeconds());
    api.setUpdateTime(LocalDateTime.now());
    apiMapper.updateById(api);
    runtimeService.invalidate(id);
    return runtimeService.snapshot(api);
  }

  /** Console tests intentionally bypass external cache/circuit behavior to validate the live datasource. */
  public QueryResponse test(Long id, Map<String, String> parameters) {
    return execute(requireApi(id), parameters, true, AccessContext.console(), false);
  }

  /** Backward-compatible call path; API_KEY services will reject because no key is supplied. */
  public QueryResponse invoke(String servicePath, Map<String, String> parameters) {
    return invoke(servicePath, parameters, null);
  }

  public QueryResponse invoke(
      String servicePath,
      Map<String, String> parameters,
      String rawApiKey) {
    String normalizedPath = normalizePath(servicePath);
    DataServiceApiPO api = apiMapper.selectOne(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getPath, normalizedPath)
            .last("LIMIT 1"));
    if (api == null) {
      throw new IllegalArgumentException("数据服务不存在：" + normalizedPath);
    }
    if (!Boolean.TRUE.equals(api.getEnabled())) {
      throw new IllegalStateException("数据服务未启用：" + normalizedPath);
    }

    AccessContext access;
    try {
      access = accessService.authorize(api, rawApiKey);
    } catch (DataServiceUnauthorizedException | DataServiceRateLimitException exception) {
      saveLog(
          api,
          parameters,
          false,
          0L,
          0,
          safeMessage(exception),
          AccessContext.rejectedApiKey());
      throw exception;
    }
    return execute(api, parameters, true, access, true);
  }

  public List<DataServiceCallLogPO> logs() {
    return callLogMapper.selectList(
        Wrappers.<DataServiceCallLogPO>lambdaQuery()
            .orderByDesc(DataServiceCallLogPO::getCreateTime)
            .orderByDesc(DataServiceCallLogPO::getId)
            .last("LIMIT 200"));
  }

  private void ensurePathAvailable(Long id, String path) {
    Long duplicateCount = apiMapper.selectCount(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getPath, path)
            .ne(id != null, DataServiceApiPO::getId, id));
    if (duplicateCount != null && duplicateCount > 0) {
      throw new IllegalArgumentException("服务路径已存在：" + path);
    }
  }

  private void applySettings(DataServiceApiPO api, ServiceSettingsInput input) {
    api.setName(input.name());
    api.setPath(input.path());
    api.setMaxRows(input.maxRows());
    api.setTimeoutSeconds(input.timeoutSeconds());
    api.setPaginationEnabled(Boolean.TRUE.equals(input.paginationEnabled()));
    api.setEnabled(input.enabled());
    api.setDescription(input.description());
  }

  private ServiceSettingsInput normalizeSettings(
      ServiceSettingsInput input,
      DataServiceApiPO existing) {
    if (input == null) throw new IllegalArgumentException("数据服务配置不能为空");
    if (!StringUtils.hasText(input.name())) throw new IllegalArgumentException("服务名称不能为空");
    String path = normalizePath(input.path());
    int maxRows = input.maxRows() == null && existing != null && existing.getMaxRows() != null
        ? existing.getMaxRows()
        : normalizeMaxRows(input.maxRows());
    int timeoutSeconds = input.timeoutSeconds() == null
        && existing != null
        && existing.getTimeoutSeconds() != null
            ? existing.getTimeoutSeconds()
            : normalizeTimeout(input.timeoutSeconds());
    boolean enabled = input.enabled() == null
        ? existing != null && Boolean.TRUE.equals(existing.getEnabled())
        : input.enabled();
    boolean paginationEnabled = input.paginationEnabled() == null
        ? existing != null && Boolean.TRUE.equals(existing.getPaginationEnabled())
        : input.paginationEnabled();
    String description = StringUtils.hasText(input.description()) ? input.description().trim() : null;
    return new ServiceSettingsInput(
        input.name().trim(),
        path,
        maxRows,
        timeoutSeconds,
        enabled,
        description,
        paginationEnabled);
  }

  private RuntimeDefinition normalizeRuntimeDefinition(RuntimeDefinition runtimeDefinition) {
    if (runtimeDefinition == null) throw new IllegalArgumentException("Runtime 定义不能为空");
    if (runtimeDefinition.dataSourceId() == null || runtimeDefinition.dataSourceId() <= 0) {
      throw new IllegalArgumentException("发布来源缺少有效的数据源");
    }
    if (!StringUtils.hasText(runtimeDefinition.sql())) {
      throw new IllegalArgumentException("发布来源 SQL 不能为空");
    }
    sqlCompiler.validateSelectOnly(runtimeDefinition.sql());
    return new RuntimeDefinition(runtimeDefinition.dataSourceId(), runtimeDefinition.sql().trim());
  }

  private QueryResponse execute(
      DataServiceApiPO api,
      Map<String, String> parameters,
      boolean writeLog,
      AccessContext access,
      boolean resilientRuntime) {
    long started = System.nanoTime();
    try {
      PaginationRequest pagination = paginationRequest(api, parameters);
      Map<String, String> sqlParameters = sqlParameters(parameters, pagination != null);
      DataServiceSqlCompiler.CompiledSql compiled = sqlCompiler.compile(api.getSqlText(), sqlParameters);
      QueryResponse response;
      if (resilientRuntime) {
        List<Object> cacheBindings = new ArrayList<>(compiled.parameters());
        if (pagination != null) {
          cacheBindings.add("__yak_page_num=" + pagination.pageNum());
          cacheBindings.add("__yak_page_size=" + pagination.pageSize());
          cacheBindings.add("__yak_return_total=" + pagination.returnTotalNum());
        }
        String cacheKey = runtimeService.cacheKey(compiled.sql(), cacheBindings);
        response = runtimeService.execute(
            api,
            cacheKey,
            () -> executeDatabase(api, compiled, pagination));
      } else {
        response = executeDatabase(api, compiled, pagination);
      }
      if (writeLog) {
        saveLog(api, parameters, true, response.durationMs(), response.rowCount(), null, access);
      }
      return response;
    } catch (RuntimeException exception) {
      long durationMs = elapsedMs(started);
      if (writeLog) saveLog(api, parameters, false, durationMs, 0, safeMessage(exception), access);
      throw exception;
    }
  }

  private QueryResponse executeDatabase(
      DataServiceApiPO api,
      DataServiceSqlCompiler.CompiledSql compiled,
      PaginationRequest pagination) {
    int fetchRows = api.getMaxRows();
    if (pagination != null && !pagination.returnTotalNum()) {
      long requiredRows = pagination.offset() + pagination.pageSize();
      fetchRows = (int) Math.min(api.getMaxRows(), Math.max(1L, requiredRows));
    }

    SqlExecutionResult result = sqlExecutionRuntime.execute(new SqlExecutionRequest(
        String.valueOf(api.getDataSourceId()),
        compiled.sql(),
        compiled.parameters(),
        fetchRows,
        api.getTimeoutSeconds(),
        SqlExecutionContext.of(SqlExecutionCaller.DATA_SERVICE, String.valueOf(api.getId()))));
    if (!result.resultSet()) {
      throw new IllegalStateException("数据服务仅允许返回 SELECT 查询结果");
    }
    return toResponse(result, pagination);
  }

  private QueryResponse toResponse(SqlExecutionResult result, PaginationRequest pagination) {
    List<String> columns = result.columns().stream().map(SqlExecutionColumn::label).toList();
    List<Map<String, Object>> allRows = new ArrayList<>(result.rows().size());
    for (List<Object> values : result.rows()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int index = 0; index < columns.size(); index++) {
        row.put(columns.get(index), index < values.size() ? values.get(index) : null);
      }
      allRows.add(row);
    }

    if (pagination == null) {
      return new QueryResponse(
          columns,
          allRows,
          result.truncated(),
          allRows.size(),
          result.timing().totalMillis());
    }

    int from = (int) Math.min(pagination.offset(), allRows.size());
    int to = Math.min(allRows.size(), from + pagination.pageSize());
    List<Map<String, Object>> pageRows = List.copyOf(allRows.subList(from, to));
    Integer totalNum = pagination.returnTotalNum() ? allRows.size() : null;
    return new QueryResponse(
        columns,
        pageRows,
        result.truncated(),
        pageRows.size(),
        result.timing().totalMillis(),
        totalNum,
        pagination.pageNum(),
        pagination.pageSize());
  }

  private PaginationRequest paginationRequest(
      DataServiceApiPO api,
      Map<String, String> parameters) {
    if (!Boolean.TRUE.equals(api.getPaginationEnabled())) return null;
    Map<String, String> values = parameters == null ? Map.of() : parameters;
    int pageNum = positiveInt(values.get("pageNum"), 1, "pageNum");
    int pageSize = positiveInt(values.get("pageSize"), DEFAULT_PAGE_SIZE, "pageSize");
    if (pageSize > api.getMaxRows()) {
      throw new IllegalArgumentException("pageSize 不能超过服务最大返回行数 " + api.getMaxRows());
    }
    boolean returnTotalNum = booleanValue(values.get("returnTotalNum"), true, "returnTotalNum");
    long offset = (long) (pageNum - 1) * pageSize;
    return new PaginationRequest(pageNum, pageSize, returnTotalNum, offset);
  }

  private Map<String, String> sqlParameters(
      Map<String, String> parameters,
      boolean paginationEnabled) {
    if (parameters == null || parameters.isEmpty()) return Map.of();
    if (!paginationEnabled) return parameters;
    Map<String, String> result = new LinkedHashMap<>(parameters);
    result.remove("pageNum");
    result.remove("pageSize");
    result.remove("returnTotalNum");
    return result;
  }

  private int positiveInt(String raw, int fallback, String name) {
    if (!StringUtils.hasText(raw)) return fallback;
    try {
      int value = Integer.parseInt(raw.trim());
      if (value <= 0) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(name + " 必须是大于 0 的整数");
    }
  }

  private boolean booleanValue(String raw, boolean fallback, String name) {
    if (!StringUtils.hasText(raw)) return fallback;
    if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) return true;
    if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) return false;
    throw new IllegalArgumentException(name + " 必须是 true/false 或 1/0");
  }

  private void saveLog(
      DataServiceApiPO api,
      Map<String, String> parameters,
      boolean success,
      long durationMs,
      int rowCount,
      String errorMessage,
      AccessContext access) {
    AccessContext caller = access == null ? AccessContext.publicAccess() : access;
    DataServiceCallLogPO log = new DataServiceCallLogPO();
    log.setApiId(api.getId());
    log.setServiceName(api.getName());
    log.setServicePath(api.getPath());
    log.setCallerType(caller.callerType());
    log.setApiKeyId(caller.apiKeyId());
    log.setApiKeyName(caller.apiKeyName());
    log.setApiKeyPrefix(caller.apiKeyPrefix());
    log.setParamsJson(limit(json(parameters == null ? Map.of() : parameters), 4_000));
    log.setSuccess(success);
    log.setDurationMs(durationMs);
    log.setRowCount(rowCount);
    log.setErrorMessage(limit(errorMessage, 1_000));
    log.setCreateTime(LocalDateTime.now());
    callLogMapper.insert(log);
  }

  private ApiView toView(DataServiceApiPO api) {
    if (api == null) return null;
    return new ApiView(
        api.getId(),
        api.getName(),
        api.getPath(),
        RUNTIME_PREFIX + api.getPath(),
        api.getDataSourceId(),
        api.getSqlText(),
        sqlCompiler.parameterNames(api.getSqlText()),
        api.getMaxRows(),
        api.getTimeoutSeconds(),
        Boolean.TRUE.equals(api.getEnabled()),
        StringUtils.hasText(api.getAuthMode()) ? api.getAuthMode() : "NONE",
        api.getDescription(),
        api.getSourceType(),
        api.getSourceRef(),
        api.getSourceRevisionId(),
        api.getSourceRevisionNo(),
        api.getCreateTime(),
        api.getUpdateTime(),
        Boolean.TRUE.equals(api.getPaginationEnabled()));
  }

  private DataServiceApiPO requireApi(Long id) {
    DataServiceApiPO api = id == null ? null : apiMapper.selectById(id);
    if (api == null) throw new IllegalArgumentException("数据服务不存在：" + id);
    return api;
  }

  private void initializeRuntimeDefaults(DataServiceApiPO api, boolean creating) {
    if (api.getPaginationEnabled() == null) api.setPaginationEnabled(Boolean.FALSE);
    if (api.getCacheEnabled() == null) api.setCacheEnabled(Boolean.FALSE);
    if (api.getCacheTtlSeconds() == null) api.setCacheTtlSeconds(DEFAULT_CACHE_TTL_SECONDS);
    if (api.getCacheMaxEntries() == null) api.setCacheMaxEntries(DEFAULT_CACHE_MAX_ENTRIES);
    if (api.getCircuitBreakerEnabled() == null) api.setCircuitBreakerEnabled(creating);
    if (api.getCircuitFailureThreshold() == null) {
      api.setCircuitFailureThreshold(DEFAULT_CIRCUIT_FAILURE_THRESHOLD);
    }
    if (api.getCircuitRecoverySeconds() == null) {
      api.setCircuitRecoverySeconds(DEFAULT_CIRCUIT_RECOVERY_SECONDS);
    }
  }

  private RuntimeConfigInput normalizeRuntimeConfig(RuntimeConfigInput input) {
    if (input == null) throw new IllegalArgumentException("Runtime 配置不能为空");
    boolean cacheEnabled = Boolean.TRUE.equals(input.cacheEnabled());
    boolean circuitEnabled = Boolean.TRUE.equals(input.circuitBreakerEnabled());
    int ttl = range(input.cacheTtlSeconds(), DEFAULT_CACHE_TTL_SECONDS, 1, 3_600, "缓存 TTL");
    int maxEntries = range(input.cacheMaxEntries(), DEFAULT_CACHE_MAX_ENTRIES, 1, 5_000, "缓存最大条目数");
    int failureThreshold = range(
        input.failureThreshold(), DEFAULT_CIRCUIT_FAILURE_THRESHOLD, 1, 20, "熔断失败阈值");
    int recoverySeconds = range(
        input.recoverySeconds(), DEFAULT_CIRCUIT_RECOVERY_SECONDS, 1, 300, "熔断恢复时间");
    return new RuntimeConfigInput(
        cacheEnabled, ttl, maxEntries, circuitEnabled, failureThreshold, recoverySeconds);
  }

  private int range(Integer value, int fallback, int min, int max, String name) {
    int normalized = value == null ? fallback : value;
    if (normalized < min || normalized > max) {
      throw new IllegalArgumentException(name + " 必须在 " + min + "~" + max + " 之间");
    }
    return normalized;
  }

  private SourceSnapshot normalizeSource(SourceSnapshot source) {
    if (source == null) throw new IllegalArgumentException("发布来源不能为空");
    SourceKey key = normalizeSourceKey(source.sourceType(), source.sourceRef());
    if (source.sourceRevisionId() == null || source.sourceRevisionId() <= 0L) {
      throw new IllegalArgumentException("来源版本 ID 非法");
    }
    if (source.sourceRevisionNo() == null || source.sourceRevisionNo() <= 0) {
      throw new IllegalArgumentException("来源版本号非法");
    }
    return new SourceSnapshot(
        key.sourceType(), key.sourceRef(), source.sourceRevisionId(), source.sourceRevisionNo());
  }

  private SourceKey normalizeSourceKey(String sourceType, String sourceRef) {
    if (!StringUtils.hasText(sourceType)) throw new IllegalArgumentException("发布来源类型不能为空");
    if (!StringUtils.hasText(sourceRef)) throw new IllegalArgumentException("发布来源引用不能为空");
    String type = sourceType.trim().toUpperCase();
    String ref = sourceRef.trim();
    if (type.length() > 64) throw new IllegalArgumentException("发布来源类型不能超过 64 个字符");
    if (ref.length() > 128) throw new IllegalArgumentException("发布来源引用不能超过 128 个字符");
    return new SourceKey(type, ref);
  }

  private String normalizePath(String path) {
    if (!StringUtils.hasText(path)) throw new IllegalArgumentException("服务路径不能为空");
    String value = path.trim();
    if (!value.startsWith("/")) value = "/" + value;
    value = value.replaceAll("/{2,}", "/");
    if (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
    if (!value.matches("/[A-Za-z0-9._~/-]+")) {
      throw new IllegalArgumentException("服务路径仅支持字母、数字、-、_、. 和 /：" + value);
    }
    return value;
  }

  private int normalizeMaxRows(Integer value) {
    int result = value == null ? DEFAULT_MAX_ROWS : value;
    if (result < 1 || result > 10_000) throw new IllegalArgumentException("最大返回行数必须在 1~10000 之间");
    return result;
  }

  private int normalizeTimeout(Integer value) {
    int result = value == null ? DEFAULT_TIMEOUT_SECONDS : value;
    if (result < 1 || result > 3_600) throw new IllegalArgumentException("超时时间必须在 1~3600 秒之间");
    return result;
  }

  private long elapsedMs(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ignored) {
      return "{}";
    }
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    return StringUtils.hasText(message) ? message : "数据服务调用失败";
  }

  private String limit(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
  }

  public record ServiceSettingsInput(
      String name,
      String path,
      Integer maxRows,
      Integer timeoutSeconds,
      Boolean enabled,
      String description,
      Boolean paginationEnabled) {
    public ServiceSettingsInput(
        String name,
        String path,
        Integer maxRows,
        Integer timeoutSeconds,
        Boolean enabled,
        String description) {
      this(name, path, maxRows, timeoutSeconds, enabled, description, null);
    }
  }

  public record RuntimeDefinition(Long dataSourceId, String sql) {}

  public record RuntimeConfigInput(
      Boolean cacheEnabled,
      Integer cacheTtlSeconds,
      Integer cacheMaxEntries,
      Boolean circuitBreakerEnabled,
      Integer failureThreshold,
      Integer recoverySeconds) {}

  public record SourceSnapshot(
      String sourceType,
      String sourceRef,
      Long sourceRevisionId,
      Integer sourceRevisionNo) {}

  public record ApiView(
      Long id,
      String name,
      String path,
      String runtimePath,
      Long dataSourceId,
      String sql,
      List<String> parameterNames,
      Integer maxRows,
      Integer timeoutSeconds,
      Boolean enabled,
      String authMode,
      String description,
      String sourceType,
      String sourceRef,
      Long sourceRevisionId,
      Integer sourceRevisionNo,
      LocalDateTime createTime,
      LocalDateTime updateTime,
      Boolean paginationEnabled) {}

  public record QueryResponse(
      List<String> columns,
      List<Map<String, Object>> rows,
      boolean truncated,
      int rowCount,
      long durationMs,
      Integer totalNum,
      Integer pageNum,
      Integer pageSize) {
    public QueryResponse(
        List<String> columns,
        List<Map<String, Object>> rows,
        boolean truncated,
        int rowCount,
        long durationMs) {
      this(columns, rows, truncated, rowCount, durationMs, null, null, null);
    }
  }

  private record PaginationRequest(
      int pageNum,
      int pageSize,
      boolean returnTotalNum,
      long offset) {}

  private record SourceKey(String sourceType, String sourceRef) {}
}
