package io.yak.ops.business.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Builds reusable/inline Chart -> Dashboard lineage from one authoritative Dashboard snapshot. */
@Service
class DashboardLineageService {

  static final String EVIDENCE_SOURCE_TYPE = "DASHBOARD_BINDING";

  private final LineageService lineageService;
  private final LineageMaintenanceService maintenanceService;
  private final ObjectMapper objectMapper;

  DashboardLineageService(
      LineageService lineageService,
      LineageMaintenanceService maintenanceService,
      ObjectMapper objectMapper) {
    this.lineageService = lineageService;
    this.maintenanceService = maintenanceService;
    this.objectMapper = objectMapper;
  }

  void syncVersion(
      DashboardAsset dashboard,
      DashboardVersion version,
      List<DashboardWidgetSnapshot> widgets,
      boolean published) {
    if (dashboard == null || dashboard.id() <= 0L) return;

    String evidenceId = String.valueOf(dashboard.id());
    maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);

    LineageAsset dashboardAsset = registerDashboardAsset(dashboard, version, widgets, published);
    if (version == null) return;

    List<DashboardWidgetSnapshot> source = widgets == null ? List.of() : widgets;
    Instant observedAt = version.createTime() == null ? Instant.now() : version.createTime();
    for (DashboardWidgetSnapshot widget : source) {
      if (widget == null || widget.widgetKey() == null || widget.widgetKey().isBlank()) continue;
      if (widget.analysisId() != null && widget.analysisId() > 0L) {
        LineageAsset chart = resolveAnalysisChart(widget.analysisId(), widget.title());
        registerContainment(
            chart,
            dashboardAsset,
            dashboard,
            version,
            widget,
            published,
            "LINKED_ANALYSIS",
            observedAt);
        continue;
      }
      if (widget.inlineAnalysis() != null) {
        InlineBinding inline = parseInline(widget.inlineAnalysis());
        LineageAsset chart = registerInlineChart(
            dashboardAsset, dashboard, version, widget, inline, published);
        registerContainment(
            chart,
            dashboardAsset,
            dashboard,
            version,
            widget,
            published,
            "INLINE_ANALYSIS",
            observedAt);
        registerInlineUpstream(chart, dashboard, version, widget, inline, published, observedAt);
      }
    }
  }

  void clear(long dashboardId) {
    if (dashboardId <= 0L) return;
    maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, String.valueOf(dashboardId));
  }

  private LineageAsset registerDashboardAsset(
      DashboardAsset dashboard,
      DashboardVersion version,
      List<DashboardWidgetSnapshot> widgets,
      boolean published) {
    ObjectNode properties = objectMapper.createObjectNode();
    if (dashboard.currentVersionId() != null) {
      properties.put("currentVersionId", String.valueOf(dashboard.currentVersionId()));
      properties.put("currentVersionNo", dashboard.currentVersionNo());
    }
    if (dashboard.publishedVersionId() != null) {
      properties.put("publishedVersionId", String.valueOf(dashboard.publishedVersionId()));
      properties.put("publishedVersionNo", dashboard.publishedVersionNo());
    }
    if (version != null) {
      properties.put("effectiveVersionId", String.valueOf(version.id()));
      properties.put("effectiveVersionNo", version.versionNo());
    }
    properties.put("effectiveSnapshot", published ? "PUBLISHED" : "DRAFT");
    properties.put("widgetCount", widgets == null ? 0 : widgets.size());

    String name = version != null && version.name() != null && !version.name().isBlank()
        ? version.name()
        : dashboard.name();
    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        dashboardAssetKey(dashboard.id()),
        LineageAssetType.DASHBOARD,
        name,
        "DASHBOARD",
        String.valueOf(dashboard.id()),
        null,
        null,
        null,
        null,
        null,
        null,
        properties));
  }

  private LineageAsset resolveAnalysisChart(long analysisId, String title) {
    String key = analysisChartAssetKey(analysisId);
    try {
      return lineageService.getAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("analysisId", String.valueOf(analysisId));
      properties.put("lineageRegistration", "DASHBOARD_FALLBACK");
      properties.put("bindingMode", "REUSABLE_ANALYSIS");
      return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
          key,
          LineageAssetType.CHART,
          title == null || title.isBlank() ? "analysis:" + analysisId : title,
          "ANALYSIS",
          String.valueOf(analysisId),
          null,
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private LineageAsset registerInlineChart(
      LineageAsset dashboardAsset,
      DashboardAsset dashboard,
      DashboardVersion version,
      DashboardWidgetSnapshot widget,
      InlineBinding inline,
      boolean published) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("dashboardId", String.valueOf(dashboard.id()));
    properties.put("dashboardVersionId", String.valueOf(version.id()));
    properties.put("dashboardVersionNo", version.versionNo());
    properties.put("widgetKey", widget.widgetKey());
    properties.put("bindingMode", "INLINE_ANALYSIS");
    properties.put("effectiveSnapshot", published ? "PUBLISHED" : "DRAFT");
    properties.put("lineageParseStatus", inline.parseStatus());
    if (inline.datasetId() != null) properties.put("datasetId", String.valueOf(inline.datasetId()));
    if (inline.chartType() != null) properties.put("chartType", inline.chartType());
    properties.put("referencedFieldCount", inline.fieldUsages().size());

    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        inlineChartAssetKey(dashboard.id(), widget.widgetKey()),
        LineageAssetType.CHART,
        widget.title() == null || widget.title().isBlank() ? widget.widgetKey() : widget.title(),
        "DASHBOARD_INLINE_ANALYSIS",
        dashboard.id() + ":" + widget.widgetKey(),
        dashboardAsset.id(),
        null,
        null,
        null,
        null,
        null,
        properties));
  }

  private void registerContainment(
      LineageAsset chart,
      LineageAsset dashboard,
      DashboardAsset dashboardSource,
      DashboardVersion version,
      DashboardWidgetSnapshot widget,
      boolean published,
      String bindingMode,
      Instant observedAt) {
    ObjectNode properties = versionProperties(dashboardSource, version, published);
    properties.put("lineageLevel", "CHART");
    properties.put("widgetKey", widget.widgetKey());
    properties.put("bindingMode", bindingMode);
    if (widget.title() != null) properties.put("widgetTitle", widget.title());
    if (widget.analysisId() != null) properties.put("analysisId", String.valueOf(widget.analysisId()));
    properties.put("gridX", widget.x());
    properties.put("gridY", widget.y());
    properties.put("gridW", widget.w());
    properties.put("gridH", widget.h());

    lineageService.registerRelation(new LineageService.RegisterRelationCommand(
        chart.id(),
        dashboard.id(),
        LineageRelationType.CONTAINS,
        EVIDENCE_SOURCE_TYPE,
        String.valueOf(dashboardSource.id()),
        null,
        BigDecimal.ONE,
        relationVersion(version, widget.widgetKey(), "contains"),
        observedAt,
        properties));
  }

  private void registerInlineUpstream(
      LineageAsset chart,
      DashboardAsset dashboard,
      DashboardVersion version,
      DashboardWidgetSnapshot widget,
      InlineBinding inline,
      boolean published,
      Instant observedAt) {
    if (inline.datasetId() == null || inline.datasetId() <= 0L) return;

    LineageAsset dataset = resolveDatasetAsset(inline.datasetId());
    ObjectNode datasetProperties = versionProperties(dashboard, version, published);
    datasetProperties.put("lineageLevel", "DATASET");
    datasetProperties.put("widgetKey", widget.widgetKey());
    datasetProperties.put("bindingMode", "INLINE_ANALYSIS");
    datasetProperties.put("datasetId", String.valueOf(inline.datasetId()));
    lineageService.registerRelation(new LineageService.RegisterRelationCommand(
        dataset.id(),
        chart.id(),
        LineageRelationType.CONSUMES,
        EVIDENCE_SOURCE_TYPE,
        String.valueOf(dashboard.id()),
        null,
        BigDecimal.ONE,
        relationVersion(version, widget.widgetKey(), "dataset"),
        observedAt,
        datasetProperties));

    int index = 0;
    for (Map.Entry<String, Set<String>> entry : inline.fieldUsages().entrySet()) {
      index++;
      LineageAsset field = resolveDatasetFieldAsset(dataset, inline.datasetId(), entry.getKey());
      ObjectNode properties = versionProperties(dashboard, version, published);
      properties.put("lineageLevel", "DATASET_FIELD");
      properties.put("widgetKey", widget.widgetKey());
      properties.put("bindingMode", "INLINE_ANALYSIS");
      properties.put("datasetId", String.valueOf(inline.datasetId()));
      properties.put("fieldId", entry.getKey());
      ArrayNode roles = properties.putArray("usageRoles");
      entry.getValue().forEach(roles::add);
      lineageService.registerRelation(new LineageService.RegisterRelationCommand(
          field.id(),
          chart.id(),
          LineageRelationType.CONSUMES,
          EVIDENCE_SOURCE_TYPE,
          String.valueOf(dashboard.id()),
          null,
          BigDecimal.ONE,
          relationVersion(version, widget.widgetKey(), "field:" + index),
          observedAt,
          properties));
    }
  }

  private LineageAsset resolveDatasetAsset(long datasetId) {
    String key = datasetAssetKey(datasetId);
    try {
      return lineageService.getAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("lineageRegistration", "DASHBOARD_INLINE_FALLBACK");
      return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
          key,
          LineageAssetType.DATASET,
          key,
          "DATASET",
          String.valueOf(datasetId),
          null,
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private LineageAsset resolveDatasetFieldAsset(
      LineageAsset dataset,
      long datasetId,
      String fieldId) {
    String key = datasetFieldAssetKey(datasetId, fieldId);
    try {
      return lineageService.getAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("fieldId", fieldId);
      properties.put("lineageRegistration", "DASHBOARD_INLINE_FALLBACK");
      return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
          key,
          LineageAssetType.DATASET_FIELD,
          fieldId,
          "DATASET",
          datasetId + ":" + fieldId,
          dataset.id(),
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private InlineBinding parseInline(Object value) {
    try {
      JsonNode root = objectMapper.valueToTree(value);
      if (root == null || !root.isObject()) {
        return new InlineBinding(null, null, Map.of(), "UNRESOLVED");
      }
      Long datasetId = positiveLong(root.get("datasetId"));
      String chartType = text(root.get("chartType"));
      JsonNode query = root.has("querySpec") && root.get("querySpec").isObject()
          ? root.get("querySpec")
          : root;

      Map<String, Set<String>> usages = new LinkedHashMap<>();
      addStringArray(usages, query.get("dimensions"), "DIMENSION");
      addFieldBindings(usages, query.get("metrics"), "METRIC");
      addFieldBindings(usages, query.get("filters"), "FILTER");
      addFieldBindings(usages, query.get("sorts"), "SORT");

      String status;
      if (datasetId == null) status = "UNRESOLVED";
      else if (usages.isEmpty()) status = "PARTIAL";
      else status = "SUCCESS";
      return new InlineBinding(datasetId, chartType, immutableUsages(usages), status);
    } catch (RuntimeException exception) {
      return new InlineBinding(null, null, Map.of(), "UNRESOLVED");
    }
  }

  private Map<String, Set<String>> immutableUsages(Map<String, Set<String>> usages) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    usages.forEach((fieldId, roles) -> result.put(fieldId, Set.copyOf(roles)));
    return Map.copyOf(result);
  }

  private void addStringArray(Map<String, Set<String>> usages, JsonNode array, String role) {
    if (array == null || !array.isArray()) return;
    for (JsonNode item : array) {
      String fieldId = text(item);
      if (fieldId != null) usage(usages, fieldId).add(role);
    }
  }

  private void addFieldBindings(Map<String, Set<String>> usages, JsonNode array, String role) {
    if (array == null || !array.isArray()) return;
    for (JsonNode item : array) {
      if (item == null || !item.isObject()) continue;
      String fieldId = text(item.get("fieldId"));
      if (fieldId != null) usage(usages, fieldId).add(role);
    }
  }

  private Set<String> usage(Map<String, Set<String>> usages, String fieldId) {
    return usages.computeIfAbsent(fieldId, ignored -> new LinkedHashSet<>());
  }

  private Long positiveLong(JsonNode value) {
    if (value == null || value.isNull()) return null;
    try {
      long parsed = value.isNumber() ? value.longValue() : Long.parseLong(value.asText().trim());
      return parsed > 0L ? parsed : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private String text(JsonNode value) {
    if (value == null || value.isNull() || !value.isValueNode()) return null;
    String text = value.asText();
    return text == null || text.isBlank() ? null : text.trim();
  }

  private ObjectNode versionProperties(
      DashboardAsset dashboard,
      DashboardVersion version,
      boolean published) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("dashboardId", String.valueOf(dashboard.id()));
    properties.put("dashboardVersionId", String.valueOf(version.id()));
    properties.put("dashboardVersionNo", version.versionNo());
    properties.put("effectiveSnapshot", published ? "PUBLISHED" : "DRAFT");
    return properties;
  }

  static String dashboardAssetKey(long dashboardId) {
    return "dashboard:" + dashboardId;
  }

  static String analysisChartAssetKey(long analysisId) {
    return "chart:analysis:" + analysisId;
  }

  static String inlineChartAssetKey(long dashboardId, String widgetKey) {
    return "chart:dashboard:" + dashboardId + ":widget:" + widgetKey;
  }

  static String datasetAssetKey(long datasetId) {
    return "dataset:" + datasetId;
  }

  static String datasetFieldAssetKey(long datasetId, String fieldId) {
    return "dataset-field:" + datasetId + ":" + fieldId;
  }

  private String relationVersion(DashboardVersion version, String widgetKey, String suffix) {
    String value = "dashboard-v" + version.versionNo() + ":" + widgetKey + ":" + suffix;
    return value.length() <= 128 ? value : value.substring(0, 128);
  }

  private record InlineBinding(
      Long datasetId,
      String chartType,
      Map<String, Set<String>> fieldUsages,
      String parseStatus) {
  }
}
