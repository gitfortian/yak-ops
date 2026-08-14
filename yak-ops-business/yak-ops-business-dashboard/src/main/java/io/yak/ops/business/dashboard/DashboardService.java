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

/** Owns Dashboard identity, immutable versions and layout. Analysis owns reusable ChartSpec definitions. */
@Service
public class DashboardService {

  private static final int MAX_WIDGETS = 200;
  private static final int MAX_INLINE_JSON = 65535;

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
    if (dashboardId <= 0L) throw new IllegalArgumentException("dashboardId 必须大于 0");
    DashboardAsset dashboard = repository.findDashboard(dashboardId)
        .orElseThrow(() -> new IllegalArgumentException("Dashboard 不存在：" + dashboardId));
    DashboardVersion currentVersion = null;
    List<DashboardWidgetSnapshot> widgets = List.of();
    if (dashboard.currentVersionId() != null) {
      currentVersion = repository.findVersion(dashboard.currentVersionId())
          .orElseThrow(() -> new IllegalStateException("Dashboard 当前版本不存在：" + dashboard.currentVersionId()));
      widgets = repository.listWidgets(currentVersion.id());
    }
    return new DashboardDetail(dashboard, currentVersion, repository.listVersions(dashboardId), widgets);
  }

  public List<DashboardVersion> versions(long dashboardId) {
    get(dashboardId);
    return repository.listVersions(dashboardId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail create(SaveCommand command) {
    Normalized normalized = normalize(command);
    long dashboardId = repository.insertDashboard(normalized.name(), normalized.description());
    appendVersion(dashboardId, 1, normalized);
    return get(dashboardId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail saveVersion(long dashboardId, SaveCommand command) {
    get(dashboardId);
    Normalized normalized = normalize(command);
    int versionNo = repository.nextVersionNo(dashboardId);
    appendVersion(dashboardId, versionNo, normalized);
    return get(dashboardId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DashboardDetail activateVersion(long dashboardId, int versionNo) {
    get(dashboardId);
    if (versionNo <= 0) throw new IllegalArgumentException("versionNo 必须大于 0");
    DashboardVersion version = repository.findVersionByNo(dashboardId, versionNo)
        .orElseThrow(() -> new IllegalArgumentException("DashboardVersion 不存在：V" + versionNo));
    repository.updateCurrentVersion(
        dashboardId, version.id(), version.versionNo(), version.name(), version.description());
    return get(dashboardId);
  }

  @Transactional("yakBusinessTransactionManager")
  public void delete(long dashboardId) {
    get(dashboardId);
    repository.deleteDashboard(dashboardId);
  }

  private void appendVersion(long dashboardId, int versionNo, Normalized normalized) {
    long versionId = repository.insertVersion(
        dashboardId, versionNo, normalized.name(), normalized.description(), normalized.activeDatasetId());
    repository.insertWidgets(versionId, normalized.widgets(), normalized.inlineJson());
    repository.updateCurrentVersion(
        dashboardId, versionId, versionNo, normalized.name(), normalized.description());
  }

  private Normalized normalize(SaveCommand command) {
    Objects.requireNonNull(command, "command");
    String name = required(command.name(), "Dashboard 名称", 200);
    String description = optional(command.description(), "Dashboard 描述", 2000);
    Long activeDatasetId = command.activeDatasetId();
    if (activeDatasetId != null && activeDatasetId <= 0L) activeDatasetId = null;

    List<WidgetSpec> source = command.widgets() == null ? List.of() : command.widgets();
    if (source.size() > MAX_WIDGETS) throw new IllegalArgumentException("Dashboard 组件不能超过 " + MAX_WIDGETS + " 个");
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
    return new Normalized(
        name,
        description,
        activeDatasetId,
        List.copyOf(widgets),
        Collections.unmodifiableList(new ArrayList<>(inlineJson)));
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

  public record SaveCommand(String name, String description, Long activeDatasetId, List<WidgetSpec> widgets) {}

  public record WidgetSpec(
      String widgetKey,
      Long analysisId,
      String title,
      Object inlineAnalysis,
      int x, int y, int w, int h,
      Integer minW, Integer minH) {}

  private record Normalized(
      String name,
      String description,
      Long activeDatasetId,
      List<WidgetSpec> widgets,
      List<String> inlineJson) {}
}
