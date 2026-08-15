package io.yak.ops.business.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.AnalysisReferenceService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns Dashboard identity, immutable draft versions, published pointer, layout and dashboard interactions. */
@Service
public class DashboardService {

  private static final int MAX_WIDGETS = 200;
  private static final int MAX_INLINE_JSON = 65535;
  private static final int MAX_GLOBAL_FILTERS = 20;
  private static final int MAX_FILTER_BINDINGS = 200;
  private static final int MAX_INTERACTIONS = 100;
  private static final int MAX_DEFAULT_VALUE_JSON = 4000;

  private final DashboardRepository repository;
  private final AnalysisReferenceService analysisReferences;
  private final ObjectMapper objectMapper;

  public DashboardService(
      DashboardRepository repository,
      AnalysisReferenceService analysisReferences,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.analysisReferences = analysisReferences;
    this.objectMapper = objectMapper;
  }

  public List<DashboardAsset> list() {
    return repository.listDashboards();
  }

  public DashboardDetail get(long dashboardId) {
    DashboardAsset dashboard = requireDashboard(dashboardId);
    DashboardVersion currentVersion = null;
    List<DashboardWidgetSnapshot> widgets = List.of();
    List<DashboardGlobalFilterSnapshot> filters = List.of();
    List<DashboardInteractionSnapshot> interactions = List.of();
    if (dashboard.currentVersionId() != null) {
      currentVersion = repository.findVersion(dashboard.currentVersionId())
          .orElseThrow(() -> new IllegalStateException("Dashboard 当前草稿版本不存在：" + dashboard.currentVersionId()));
      widgets = repository.listWidgets(currentVersion.id());
      filters = repository.listGlobalFilters(currentVersion.id());
      interactions = repository.listInteractions(currentVersion.id());
    }
    return new DashboardDetail(
        dashboard,
        currentVersion,
        repository.listVersions(dashboardId),
        widgets,
        filters,
        interactions);
  }

  public List<DashboardVersion> versions(long dashboardId) {
    requireDashboard(dashboardId);
    return repository.listVersions(dashboardId);
  }

  /** Reads one exact immutable historical snapshot without changing the editable draft pointer. */
  public DashboardVersionDetail version(long dashboardId, int versionNo) {
    DashboardAsset dashboard = requireDashboard(dashboardId);
    if (versionNo <= 0) throw new IllegalArgumentException("versionNo 必须大于 0");
    DashboardVersion version = repository.findVersionByNo(dashboardId, versionNo)
        .orElseThrow(() -> new IllegalArgumentException("DashboardVersion 不存在：V" + versionNo));
    return versionDetail(dashboard, version);
  }

  /** Reader-facing snapshot. Editing and saving drafts never changes this result until publish is called. */
  public DashboardVersionDetail published(long dashboardId) {
    DashboardAsset dashboard = requireDashboard(dashboardId);
    if (dashboard.publishedVersionId() == null) {
      throw new IllegalStateException("Dashboard 尚未发布：" + dashboardId);
    }
    DashboardVersion version = repository.findVersion(dashboard.publishedVersionId())
        .orElseThrow(() -> new IllegalStateException(
            "Dashboard 已发布版本不存在：" + dashboard.publishedVersionId()));
    return versionDetail(dashboard, version);
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail create(SaveCommand command) {
    Normalized normalized = normalize(command);
    long dashboardId = repository.insertDashboard(normalized.name(), normalized.description());
    appendVersion(dashboardId, 1, normalized);
    return get(dashboardId);
  }

  /** Saves an immutable draft snapshot only. It does not affect the published pointer. */
  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail saveVersion(long dashboardId, SaveCommand command) {
    requireDashboard(dashboardId);
    Normalized normalized = normalize(command);
    int versionNo = repository.nextVersionNo(dashboardId);
    appendVersion(dashboardId, versionNo, normalized);
    return get(dashboardId);
  }

  /** Publishes the current saved draft. Publishing is idempotent and does not create another version. */
  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail publish(long dashboardId) {
    DashboardAsset dashboard = requireDashboard(dashboardId);
    if (dashboard.currentVersionId() == null || dashboard.currentVersionNo() <= 0) {
      throw new IllegalStateException("Dashboard 没有可发布的草稿：" + dashboardId);
    }
    if (Objects.equals(dashboard.currentVersionId(), dashboard.publishedVersionId())) {
      return get(dashboardId);
    }
    DashboardVersion draft = repository.findVersion(dashboard.currentVersionId())
        .orElseThrow(() -> new IllegalStateException("Dashboard 当前草稿版本不存在：" + dashboard.currentVersionId()));
    repository.updatePublishedVersion(dashboardId, draft.id(), draft.versionNo());
    return get(dashboardId);
  }

  /**
   * Restores an historical snapshot as a new editable draft version.
   * The historical version remains immutable and the published pointer is deliberately unchanged.
   */
  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail restoreVersion(long dashboardId, int versionNo) {
    DashboardVersionDetail source = version(dashboardId, versionNo);
    Normalized normalized = normalize(commandFromVersion(source));
    appendVersion(dashboardId, repository.nextVersionNo(dashboardId), normalized);
    return get(dashboardId);
  }

  /** @deprecated Historical activation now restores the snapshot as a new draft instead of moving a pointer backwards. */
  @Deprecated
  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail activateVersion(long dashboardId, int versionNo) {
    return restoreVersion(dashboardId, versionNo);
  }

  @Transactional("yakBusinessTransactionManager")
  public void delete(long dashboardId) {
    requireDashboard(dashboardId);
    repository.deleteDashboard(dashboardId);
  }

  private DashboardAsset requireDashboard(long dashboardId) {
    if (dashboardId <= 0L) throw new IllegalArgumentException("dashboardId 必须大于 0");
    return repository.findDashboard(dashboardId)
        .orElseThrow(() -> new IllegalArgumentException("Dashboard 不存在：" + dashboardId));
  }

  private DashboardVersionDetail versionDetail(DashboardAsset dashboard, DashboardVersion version) {
    return new DashboardVersionDetail(
        dashboard,
        version,
        repository.listWidgets(version.id()),
        repository.listGlobalFilters(version.id()),
        repository.listInteractions(version.id()));
  }

  private SaveCommand commandFromVersion(DashboardVersionDetail detail) {
    List<WidgetSpec> widgets = detail.widgets().stream()
        .map(widget -> new WidgetSpec(
            widget.widgetKey(), widget.analysisId(), widget.title(), widget.inlineAnalysis(),
            widget.x(), widget.y(), widget.w(), widget.h(), widget.minW(), widget.minH()))
        .toList();
    List<GlobalFilterSpec> filters = detail.globalFilters().stream()
        .map(filter -> new GlobalFilterSpec(
            filter.filterKey(), filter.name(), filter.operator(), filter.defaultValue(),
            filter.bindings().stream()
                .map(binding -> new FilterBindingSpec(binding.widgetKey(), binding.fieldId()))
                .toList()))
        .toList();
    List<InteractionSpec> interactions = detail.interactions().stream()
        .map(interaction -> new InteractionSpec(
            interaction.interactionKey(), interaction.event(), interaction.sourceWidgetKey(),
            interaction.sourceFieldId(), interaction.targetFilterKey()))
        .toList();
    return new SaveCommand(
        detail.version().name(),
        detail.version().description(),
        detail.version().activeDatasetId(),
        widgets,
        filters,
        interactions);
  }

  private void appendVersion(long dashboardId, int versionNo, Normalized normalized) {
    long versionId = repository.insertVersion(
        dashboardId, versionNo, normalized.name(), normalized.description(), normalized.activeDatasetId());
    repository.insertWidgets(versionId, normalized.widgets(), normalized.inlineJson());
    repository.insertGlobalFilters(versionId, normalized.globalFilters(), normalized.defaultValueJson());
    repository.insertInteractions(versionId, normalized.interactions());
    repository.updateCurrentVersion(
        dashboardId, versionId, versionNo, normalized.name(), normalized.description());
  }

  private Normalized normalize(SaveCommand command) {
    Objects.requireNonNull(command, "command");
    String name = required(command.name(), "Dashboard 名称", 200);
    String description = optional(command.description(), "Dashboard 描述", 2000);
    Long activeDatasetId = command.activeDatasetId();
    if (activeDatasetId != null && activeDatasetId <= 0L) activeDatasetId = null;

    WidgetNormalization widgetNormalization = normalizeWidgets(command.widgets());
    FilterNormalization filterNormalization = normalizeGlobalFilters(
        command.globalFilters(), widgetNormalization.widgetKeys());
    List<InteractionSpec> interactions = normalizeInteractions(
        command.interactions(), widgetNormalization.widgetKeys(), filterNormalization.filterKeys());

    return new Normalized(
        name,
        description,
        activeDatasetId,
        widgetNormalization.widgets(),
        widgetNormalization.inlineJson(),
        filterNormalization.filters(),
        filterNormalization.defaultValueJson(),
        interactions);
  }

  private WidgetNormalization normalizeWidgets(List<WidgetSpec> values) {
    List<WidgetSpec> source = values == null ? List.of() : values;
    if (source.size() > MAX_WIDGETS) {
      throw new IllegalArgumentException("Dashboard 组件不能超过 " + MAX_WIDGETS + " 个");
    }
    List<WidgetSpec> widgets = new ArrayList<>(source.size());
    List<String> inlineJson = new ArrayList<>(source.size());
    Set<String> widgetKeys = new HashSet<>();

    for (WidgetSpec value : source) {
      if (value == null) throw new IllegalArgumentException("DashboardWidget 不能为空");
      String widgetKey = required(value.widgetKey(), "widgetKey", 64);
      if (!widgetKeys.add(widgetKey)) throw new IllegalArgumentException("widgetKey 重复：" + widgetKey);
      String title = optional(value.title(), "Widget 标题", 200);

      boolean linked = value.analysisId() != null;
      boolean inline = value.inlineAnalysis() != null;
      if (linked == inline) {
        throw new IllegalArgumentException("Widget 必须且只能选择 analysisId 或 inlineAnalysis：" + widgetKey);
      }
      if (linked) {
        if (value.analysisId() <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
        analysisReferences.requireExists(value.analysisId());
      }

      validateLayout(value, widgetKey);
      String json = inline ? inlineJson(value.inlineAnalysis(), widgetKey) : null;
      widgets.add(new WidgetSpec(
          widgetKey, value.analysisId(), title, value.inlineAnalysis(),
          value.x(), value.y(), value.w(), value.h(), value.minW(), value.minH()));
      inlineJson.add(json);
    }
    return new WidgetNormalization(
        List.copyOf(widgets),
        Collections.unmodifiableList(new ArrayList<>(inlineJson)),
        Set.copyOf(widgetKeys));
  }

  private FilterNormalization normalizeGlobalFilters(
      List<GlobalFilterSpec> values,
      Set<String> widgetKeys) {
    List<GlobalFilterSpec> source = values == null ? List.of() : values;
    if (source.size() > MAX_GLOBAL_FILTERS) {
      throw new IllegalArgumentException("Dashboard 全局筛选器不能超过 " + MAX_GLOBAL_FILTERS + " 个");
    }

    List<GlobalFilterSpec> filters = new ArrayList<>(source.size());
    List<String> defaultValueJson = new ArrayList<>(source.size());
    Set<String> filterKeys = new HashSet<>();
    int bindingCount = 0;

    for (GlobalFilterSpec value : source) {
      if (value == null) throw new IllegalArgumentException("Dashboard 全局筛选器不能为空");
      String filterKey = required(value.filterKey(), "filterKey", 64);
      if (!filterKeys.add(filterKey)) throw new IllegalArgumentException("filterKey 重复：" + filterKey);
      String filterName = required(value.name(), "筛选器名称", 200);
      DashboardGlobalFilterOperator operator = Objects.requireNonNull(value.operator(), "筛选器 operator");

      List<FilterBindingSpec> sourceBindings = value.bindings() == null ? List.of() : value.bindings();
      List<FilterBindingSpec> bindings = new ArrayList<>(sourceBindings.size());
      Set<String> boundWidgets = new HashSet<>();
      for (FilterBindingSpec binding : sourceBindings) {
        if (binding == null) throw new IllegalArgumentException("筛选器绑定不能为空：" + filterKey);
        String widgetKey = required(binding.widgetKey(), "筛选器 widgetKey", 64);
        if (!widgetKeys.contains(widgetKey)) {
          throw new IllegalArgumentException("筛选器绑定的 Widget 不存在：" + widgetKey);
        }
        if (!boundWidgets.add(widgetKey)) {
          throw new IllegalArgumentException("同一筛选器对单个 Widget 只能绑定一个字段：" + widgetKey);
        }
        String fieldId = required(binding.fieldId(), "筛选器 fieldId", 64);
        bindings.add(new FilterBindingSpec(widgetKey, fieldId));
        bindingCount++;
        if (bindingCount > MAX_FILTER_BINDINGS) {
          throw new IllegalArgumentException("Dashboard 筛选器字段映射不能超过 " + MAX_FILTER_BINDINGS + " 个");
        }
      }

      filters.add(new GlobalFilterSpec(
          filterKey, filterName, operator, value.defaultValue(), List.copyOf(bindings)));
      defaultValueJson.add(scalarJson(value.defaultValue(), filterKey));
    }

    return new FilterNormalization(
        List.copyOf(filters),
        Collections.unmodifiableList(new ArrayList<>(defaultValueJson)),
        Set.copyOf(filterKeys));
  }

  private List<InteractionSpec> normalizeInteractions(
      List<InteractionSpec> values,
      Set<String> widgetKeys,
      Set<String> filterKeys) {
    List<InteractionSpec> source = values == null ? List.of() : values;
    if (source.size() > MAX_INTERACTIONS) {
      throw new IllegalArgumentException("Dashboard 联动规则不能超过 " + MAX_INTERACTIONS + " 个");
    }
    List<InteractionSpec> result = new ArrayList<>(source.size());
    Set<String> interactionKeys = new HashSet<>();
    for (InteractionSpec value : source) {
      if (value == null) throw new IllegalArgumentException("Dashboard 联动规则不能为空");
      String interactionKey = required(value.interactionKey(), "interactionKey", 64);
      if (!interactionKeys.add(interactionKey)) {
        throw new IllegalArgumentException("interactionKey 重复：" + interactionKey);
      }
      DashboardInteractionEvent event = Objects.requireNonNull(value.event(), "联动 event");
      String sourceWidgetKey = required(value.sourceWidgetKey(), "联动 sourceWidgetKey", 64);
      if (!widgetKeys.contains(sourceWidgetKey)) {
        throw new IllegalArgumentException("联动来源 Widget 不存在：" + sourceWidgetKey);
      }
      String sourceFieldId = required(value.sourceFieldId(), "联动 sourceFieldId", 64);
      String targetFilterKey = required(value.targetFilterKey(), "联动 targetFilterKey", 64);
      if (!filterKeys.contains(targetFilterKey)) {
        throw new IllegalArgumentException("联动目标筛选器不存在：" + targetFilterKey);
      }
      result.add(new InteractionSpec(
          interactionKey, event, sourceWidgetKey, sourceFieldId, targetFilterKey));
    }
    return List.copyOf(result);
  }

  private void validateLayout(WidgetSpec value, String widgetKey) {
    if (value.x() < 0 || value.x() >= 24) throw new IllegalArgumentException("Widget x 必须在 0~23：" + widgetKey);
    if (value.y() < 0) throw new IllegalArgumentException("Widget y 不能小于 0：" + widgetKey);
    if (value.w() <= 0 || value.w() > 24) throw new IllegalArgumentException("Widget w 必须在 1~24：" + widgetKey);
    if (value.h() <= 0 || value.h() > 60) throw new IllegalArgumentException("Widget h 必须在 1~60：" + widgetKey);
    if (value.x() + value.w() > 24) throw new IllegalArgumentException("Widget 超出 24 栅格：" + widgetKey);
    if (value.minW() != null && (value.minW() <= 0 || value.minW() > value.w())) {
      throw new IllegalArgumentException("Widget minW 必须大于 0 且不能超过 w：" + widgetKey);
    }
    if (value.minH() != null && (value.minH() <= 0 || value.minH() > value.h())) {
      throw new IllegalArgumentException("Widget minH 必须大于 0 且不能超过 h：" + widgetKey);
    }
  }

  private String inlineJson(Object value, String widgetKey) {
    JsonNode node = objectMapper.valueToTree(value);
    if (!node.isObject()) throw new IllegalArgumentException("inlineAnalysis 必须是 JSON 对象：" + widgetKey);
    try {
      String json = objectMapper.writeValueAsString(value);
      if (json.length() > MAX_INLINE_JSON) throw new IllegalArgumentException("inlineAnalysis 配置过大：" + widgetKey);
      return json;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("inlineAnalysis 无法序列化：" + widgetKey, exception);
    }
  }

  private String scalarJson(Object value, String filterKey) {
    if (value == null) return null;
    JsonNode node = objectMapper.valueToTree(value);
    if (!node.isValueNode()) {
      throw new IllegalArgumentException("全局筛选器默认值必须是标量：" + filterKey);
    }
    try {
      String json = objectMapper.writeValueAsString(value);
      if (json.length() > MAX_DEFAULT_VALUE_JSON) {
        throw new IllegalArgumentException("全局筛选器默认值过大：" + filterKey);
      }
      return json;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("全局筛选器默认值无法序列化：" + filterKey, exception);
    }
  }

  private String required(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    return normalized;
  }

  private String optional(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    return normalized;
  }

  public record SaveCommand(
      String name,
      String description,
      Long activeDatasetId,
      List<WidgetSpec> widgets,
      List<GlobalFilterSpec> globalFilters,
      List<InteractionSpec> interactions) {
  }

  public record WidgetSpec(
      String widgetKey,
      Long analysisId,
      String title,
      Object inlineAnalysis,
      int x, int y, int w, int h,
      Integer minW, Integer minH) {
  }

  public record GlobalFilterSpec(
      String filterKey,
      String name,
      DashboardGlobalFilterOperator operator,
      Object defaultValue,
      List<FilterBindingSpec> bindings) {
  }

  public record FilterBindingSpec(String widgetKey, String fieldId) {
  }

  public record InteractionSpec(
      String interactionKey,
      DashboardInteractionEvent event,
      String sourceWidgetKey,
      String sourceFieldId,
      String targetFilterKey) {
  }

  private record WidgetNormalization(
      List<WidgetSpec> widgets,
      List<String> inlineJson,
      Set<String> widgetKeys) {
  }

  private record FilterNormalization(
      List<GlobalFilterSpec> filters,
      List<String> defaultValueJson,
      Set<String> filterKeys) {
  }

  private record Normalized(
      String name,
      String description,
      Long activeDatasetId,
      List<WidgetSpec> widgets,
      List<String> inlineJson,
      List<GlobalFilterSpec> globalFilters,
      List<String> defaultValueJson,
      List<InteractionSpec> interactions) {
  }
}
