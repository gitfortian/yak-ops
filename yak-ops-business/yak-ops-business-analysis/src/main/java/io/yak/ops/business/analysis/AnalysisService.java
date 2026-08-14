package io.yak.ops.business.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.DatasetService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns reusable ChartSpec assets. Dashboard layout and Dataset execution stay in their domains. */
@Service
public class AnalysisService {

  private static final int DEFAULT_LIMIT = 500;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private final AnalysisRepository repository;
  private final DatasetService datasetService;
  private final ObjectMapper objectMapper;

  public AnalysisService(
      AnalysisRepository repository,
      DatasetService datasetService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.datasetService = datasetService;
    this.objectMapper = objectMapper;
  }

  public List<AnalysisAsset> list() {
    return repository.list().stream().map(this::toAsset).toList();
  }

  public AnalysisAsset get(long analysisId) {
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
    return repository.findById(analysisId)
        .map(this::toAsset)
        .orElseThrow(() -> new IllegalArgumentException("Analysis 不存在：" + analysisId));
  }

  @Transactional("yakBusinessTransactionManager")
  public AnalysisAsset create(SaveCommand command) {
    Normalized normalized = normalize(command);
    long analysisId = repository.insert(
        normalized.name(),
        normalized.description(),
        normalized.datasetId(),
        normalized.chartType(),
        json(normalized.querySpec()),
        json(normalized.visualConfig()));
    return get(analysisId);
  }

  @Transactional("yakBusinessTransactionManager")
  public AnalysisAsset update(long analysisId, SaveCommand command) {
    get(analysisId);
    Normalized normalized = normalize(command);
    repository.update(
        analysisId,
        normalized.name(),
        normalized.description(),
        normalized.datasetId(),
        normalized.chartType(),
        json(normalized.querySpec()),
        json(normalized.visualConfig()));
    return get(analysisId);
  }

  @Transactional("yakBusinessTransactionManager")
  public void delete(long analysisId) {
    get(analysisId);
    repository.delete(analysisId);
  }

  private Normalized normalize(SaveCommand command) {
    Objects.requireNonNull(command, "command");
    String name = required(command.name(), "Analysis 名称", 200);
    String description = optional(command.description(), 2000, "Analysis 描述");
    if (command.datasetId() <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
    AnalysisChartType chartType = Objects.requireNonNull(command.chartType(), "chartType");

    AnalysisQuerySpec querySpec = normalizeQuery(command.querySpec(), chartType);
    AnalysisVisualConfig visualConfig = command.visualConfig() == null
        ? defaultVisualConfig(chartType)
        : command.visualConfig();

    Set<String> referencedFields = referencedFields(querySpec);
    datasetService.validateAnalysisBinding(command.datasetId(), referencedFields);
    return new Normalized(
        name,
        description,
        command.datasetId(),
        chartType,
        querySpec,
        visualConfig);
  }

  private AnalysisQuerySpec normalizeQuery(
      AnalysisQuerySpec querySpec,
      AnalysisChartType chartType) {
    AnalysisQuerySpec value = querySpec == null
        ? new AnalysisQuerySpec(List.of(), List.of(), List.of(), List.of(), DEFAULT_LIMIT, DEFAULT_TIMEOUT_SECONDS)
        : querySpec;

    List<String> dimensions = uniqueFieldIds(value.dimensions(), "维度");
    List<AnalysisMetricBinding> metrics = normalizeMetrics(value.metrics());
    List<AnalysisFilterBinding> filters = normalizeFilters(value.filters());
    List<AnalysisSortBinding> sorts = normalizeSorts(value.sorts(), dimensions, metrics);
    int limit = value.limit() <= 0 ? DEFAULT_LIMIT : value.limit();
    int timeoutSeconds = value.timeoutSeconds() <= 0 ? DEFAULT_TIMEOUT_SECONDS : value.timeoutSeconds();
    if (limit > 1000) throw new IllegalArgumentException("Analysis limit 不能超过 1000");
    if (timeoutSeconds > 120) throw new IllegalArgumentException("Analysis timeoutSeconds 不能超过 120");

    validateChartBindings(chartType, dimensions, metrics);
    return new AnalysisQuerySpec(
        dimensions,
        metrics,
        filters,
        sorts,
        limit,
        timeoutSeconds);
  }

  private List<AnalysisMetricBinding> normalizeMetrics(List<AnalysisMetricBinding> values) {
    if (values == null || values.isEmpty()) return List.of();
    List<AnalysisMetricBinding> normalized = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (AnalysisMetricBinding metric : values) {
      if (metric == null || metric.aggregation() == null) {
        throw new IllegalArgumentException("指标字段和聚合方式不能为空");
      }
      String fieldId = required(metric.fieldId(), "指标 fieldId", 64);
      String key = fieldId + "|" + metric.aggregation().name();
      if (!seen.add(key)) throw new IllegalArgumentException("指标重复：" + key);
      normalized.add(new AnalysisMetricBinding(fieldId, metric.aggregation()));
    }
    return List.copyOf(normalized);
  }

  private List<AnalysisFilterBinding> normalizeFilters(List<AnalysisFilterBinding> values) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > 50) throw new IllegalArgumentException("Analysis 过滤条件不能超过 50 个");
    List<AnalysisFilterBinding> normalized = new ArrayList<>();
    for (AnalysisFilterBinding filter : values) {
      if (filter == null || filter.operator() == null) {
        throw new IllegalArgumentException("过滤字段和操作符不能为空");
      }
      String fieldId = required(filter.fieldId(), "过滤 fieldId", 64);
      if (filter.value() instanceof String text && text.length() > 4000) {
        throw new IllegalArgumentException("单个过滤值不能超过 4000 个字符");
      }
      normalized.add(new AnalysisFilterBinding(fieldId, filter.operator(), filter.value()));
    }
    return List.copyOf(normalized);
  }

  private List<AnalysisSortBinding> normalizeSorts(
      List<AnalysisSortBinding> values,
      List<String> dimensions,
      List<AnalysisMetricBinding> metrics) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > 5) throw new IllegalArgumentException("Analysis 排序条件不能超过 5 个");
    Set<String> dimensionSet = Set.copyOf(dimensions);
    List<AnalysisSortBinding> normalized = new ArrayList<>();
    for (AnalysisSortBinding sort : values) {
      if (sort == null) throw new IllegalArgumentException("排序条件不能为空");
      String fieldId = required(sort.fieldId(), "排序 fieldId", 64);
      AnalysisSortDirection direction = sort.direction() == null ? AnalysisSortDirection.ASC : sort.direction();
      if (dimensionSet.contains(fieldId)) {
        if (sort.aggregation() != null) {
          throw new IllegalArgumentException("维度排序不能指定 aggregation：" + fieldId);
        }
        normalized.add(new AnalysisSortBinding(fieldId, null, direction));
        continue;
      }
      AnalysisMetricBinding metric = metrics.stream()
          .filter(candidate -> candidate.fieldId().equals(fieldId)
              && (sort.aggregation() == null || candidate.aggregation() == sort.aggregation()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("排序字段必须先出现在 dimensions 或 metrics 中：" + fieldId));
      normalized.add(new AnalysisSortBinding(fieldId, metric.aggregation(), direction));
    }
    return List.copyOf(normalized);
  }

  private void validateChartBindings(
      AnalysisChartType chartType,
      List<String> dimensions,
      List<AnalysisMetricBinding> metrics) {
    switch (chartType) {
      case METRIC -> {
        if (!dimensions.isEmpty()) throw new IllegalArgumentException("指标卡不能配置维度");
        if (metrics.size() != 1) throw new IllegalArgumentException("指标卡必须且只能配置 1 个指标");
      }
      case PIE -> {
        if (dimensions.size() != 1) throw new IllegalArgumentException("饼图必须且只能配置 1 个维度");
        if (metrics.size() != 1) throw new IllegalArgumentException("饼图必须且只能配置 1 个指标");
      }
      case BAR, LINE -> {
        if (dimensions.isEmpty()) throw new IllegalArgumentException("柱状图/折线图至少需要 1 个维度");
        if (metrics.isEmpty()) throw new IllegalArgumentException("柱状图/折线图至少需要 1 个指标");
      }
      case TABLE -> {
        if (dimensions.isEmpty() && metrics.isEmpty()) {
          throw new IllegalArgumentException("表格至少需要 1 个维度或指标");
        }
      }
    }
  }

  private Set<String> referencedFields(AnalysisQuerySpec spec) {
    Set<String> values = new LinkedHashSet<>();
    values.addAll(spec.dimensions());
    spec.metrics().forEach(metric -> values.add(metric.fieldId()));
    spec.filters().forEach(filter -> values.add(filter.fieldId()));
    spec.sorts().forEach(sort -> values.add(sort.fieldId()));
    return Set.copyOf(values);
  }

  private List<String> uniqueFieldIds(List<String> values, String label) {
    if (values == null || values.isEmpty()) return List.of();
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      String fieldId = required(value, label + " fieldId", 64);
      if (!result.add(fieldId)) throw new IllegalArgumentException(label + "字段重复：" + fieldId);
    }
    return List.copyOf(result);
  }

  private AnalysisVisualConfig defaultVisualConfig(AnalysisChartType type) {
    return new AnalysisVisualConfig(
        type == AnalysisChartType.PIE,
        false,
        type == AnalysisChartType.LINE,
        type == AnalysisChartType.BAR || type == AnalysisChartType.LINE);
  }

  private AnalysisAsset toAsset(AnalysisRepository.AnalysisRow row) {
    try {
      return new AnalysisAsset(
          row.id(),
          row.name(),
          row.description(),
          row.datasetId(),
          row.chartType(),
          objectMapper.readValue(row.querySpecJson(), AnalysisQuerySpec.class),
          objectMapper.readValue(row.visualConfigJson(), AnalysisVisualConfig.class),
          row.createTime(),
          row.updateTime());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Analysis 配置反序列化失败：" + row.id(), exception);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Analysis 配置序列化失败", exception);
    }
  }

  private String required(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    return normalized;
  }

  private String optional(String value, int maxLength, String label) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    return normalized;
  }

  public record SaveCommand(
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      AnalysisQuerySpec querySpec,
      AnalysisVisualConfig visualConfig) {
  }

  private record Normalized(
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      AnalysisQuerySpec querySpec,
      AnalysisVisualConfig visualConfig) {
  }
}
