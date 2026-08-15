package io.yak.ops.business.dataservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.service.support.DataServiceSqlCompiler;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.service.support.BusinessDataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Phase-1 data service: persist SELECT definitions and expose them through a safe REST runtime. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceService {

  private static final int DEFAULT_MAX_ROWS = 1_000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final String RUNTIME_PREFIX = "/api/v1/data-service/runtime";

  private final DataServiceApiMapper apiMapper;
  private final DataServiceCallLogMapper callLogMapper;
  private final BusinessDataSourceExecutionProvider executionProvider;
  private final DataServiceSqlCompiler sqlCompiler;
  private final ObjectMapper objectMapper;

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

  @Transactional
  public ApiView save(Long id, ApiInput input) {
    validateInput(input);
    String path = normalizePath(input.path());
    Long duplicateCount = apiMapper.selectCount(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getPath, path)
            .ne(id != null, DataServiceApiPO::getId, id));
    if (duplicateCount != null && duplicateCount > 0) {
      throw new IllegalArgumentException("服务路径已存在：" + path);
    }

    LocalDateTime now = LocalDateTime.now();
    DataServiceApiPO api = id == null ? new DataServiceApiPO() : requireApi(id);
    api.setName(input.name().trim());
    api.setPath(path);
    api.setDataSourceId(input.dataSourceId());
    api.setSqlText(input.sql().trim());
    api.setMaxRows(normalizeMaxRows(input.maxRows()));
    api.setTimeoutSeconds(normalizeTimeout(input.timeoutSeconds()));
    api.setEnabled(input.enabled() == null ? Boolean.FALSE : input.enabled());
    api.setDescription(StringUtils.hasText(input.description()) ? input.description().trim() : null);
    api.setUpdateTime(now);
    if (id == null) {
      api.setCreateTime(now);
      apiMapper.insert(api);
    } else {
      apiMapper.updateById(api);
    }
    return toView(api);
  }

  @Transactional
  public void delete(Long id) {
    if (apiMapper.deleteById(id) == 0) {
      throw new IllegalArgumentException("数据服务不存在：" + id);
    }
  }

  @Transactional
  public ApiView setEnabled(Long id, boolean enabled) {
    DataServiceApiPO api = requireApi(id);
    api.setEnabled(enabled);
    api.setUpdateTime(LocalDateTime.now());
    apiMapper.updateById(api);
    return toView(api);
  }

  public QueryResponse test(Long id, Map<String, String> parameters) {
    return execute(requireApi(id), parameters, true);
  }

  public QueryResponse invoke(String servicePath, Map<String, String> parameters) {
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
    return execute(api, parameters, true);
  }

  public List<DataServiceCallLogPO> logs() {
    return callLogMapper.selectList(
        Wrappers.<DataServiceCallLogPO>lambdaQuery()
            .orderByDesc(DataServiceCallLogPO::getCreateTime)
            .orderByDesc(DataServiceCallLogPO::getId)
            .last("LIMIT 200"));
  }

  private QueryResponse execute(
      DataServiceApiPO api, Map<String, String> parameters, boolean writeLog) {
    long started = System.nanoTime();
    try {
      DataServiceSqlCompiler.CompiledSql compiled = sqlCompiler.compile(api.getSqlText(), parameters);
      DataSourceSqlResult result;
      try (DataSourceSqlExecutor executor = executionProvider.open(String.valueOf(api.getDataSourceId()))) {
        result = executor.execute(
            new DataSourceSqlRequest(
                compiled.sql(), api.getMaxRows(), api.getTimeoutSeconds(), compiled.parameters()));
      }
      if (!result.resultSet()) {
        throw new IllegalStateException("数据服务仅允许返回 SELECT 查询结果");
      }
      long durationMs = elapsedMs(started);
      QueryResponse response = toResponse(result, durationMs);
      if (writeLog) saveLog(api, parameters, true, durationMs, response.rowCount(), null);
      return response;
    } catch (RuntimeException exception) {
      long durationMs = elapsedMs(started);
      if (writeLog) saveLog(api, parameters, false, durationMs, 0, safeMessage(exception));
      throw exception;
    }
  }

  private QueryResponse toResponse(DataSourceSqlResult result, long durationMs) {
    List<String> columns = result.columns().stream().map(DataSourceSqlColumn::label).toList();
    List<Map<String, Object>> rows = new ArrayList<>(result.rows().size());
    for (List<Object> values : result.rows()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int index = 0; index < columns.size(); index++) {
        row.put(columns.get(index), index < values.size() ? values.get(index) : null);
      }
      rows.add(row);
    }
    return new QueryResponse(columns, rows, result.truncated(), rows.size(), durationMs);
  }

  private void saveLog(
      DataServiceApiPO api,
      Map<String, String> parameters,
      boolean success,
      long durationMs,
      int rowCount,
      String errorMessage) {
    DataServiceCallLogPO log = new DataServiceCallLogPO();
    log.setApiId(api.getId());
    log.setServiceName(api.getName());
    log.setServicePath(api.getPath());
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
        api.getId(), api.getName(), api.getPath(), RUNTIME_PREFIX + api.getPath(),
        api.getDataSourceId(), api.getSqlText(), sqlCompiler.parameterNames(api.getSqlText()),
        api.getMaxRows(), api.getTimeoutSeconds(), Boolean.TRUE.equals(api.getEnabled()),
        api.getDescription(), api.getCreateTime(), api.getUpdateTime());
  }

  private DataServiceApiPO requireApi(Long id) {
    DataServiceApiPO api = id == null ? null : apiMapper.selectById(id);
    if (api == null) throw new IllegalArgumentException("数据服务不存在：" + id);
    return api;
  }

  private void validateInput(ApiInput input) {
    if (input == null) throw new IllegalArgumentException("数据服务配置不能为空");
    if (!StringUtils.hasText(input.name())) throw new IllegalArgumentException("服务名称不能为空");
    if (input.dataSourceId() == null || input.dataSourceId() <= 0) {
      throw new IllegalArgumentException("请选择数据源");
    }
    sqlCompiler.validateSelectOnly(input.sql());
    normalizePath(input.path());
    normalizeMaxRows(input.maxRows());
    normalizeTimeout(input.timeoutSeconds());
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

  public record ApiInput(
      String name,
      String path,
      Long dataSourceId,
      String sql,
      Integer maxRows,
      Integer timeoutSeconds,
      Boolean enabled,
      String description) {}

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
      String description,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  public record QueryResponse(
      List<String> columns,
      List<Map<String, Object>> rows,
      boolean truncated,
      int rowCount,
      long durationMs) {}
}
