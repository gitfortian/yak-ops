package io.yak.ops.business.dashboard;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** Stable Dashboard identity. The current version is the editable draft; published version is reader-facing. */
public record DashboardAsset(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    String name,
    String description,
    @JsonSerialize(using = ToStringSerializer.class) Long currentVersionId,
    int currentVersionNo,
    @JsonSerialize(using = ToStringSerializer.class) Long publishedVersionId,
    int publishedVersionNo,
    Instant publishedTime,
    Instant createTime,
    Instant updateTime) {
}

record DashboardVersion(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    @JsonSerialize(using = ToStringSerializer.class) long dashboardId,
    int versionNo,
    String name,
    String description,
    @JsonSerialize(using = ToStringSerializer.class) Long activeDatasetId,
    Instant createTime) {
}

record DashboardWidgetSnapshot(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    @JsonSerialize(using = ToStringSerializer.class) long dashboardVersionId,
    String widgetKey,
    @JsonSerialize(using = ToStringSerializer.class) Long analysisId,
    String title,
    Object inlineAnalysis,
    int x,
    int y,
    int w,
    int h,
    Integer minW,
    Integer minH,
    int sortOrder) {
}

enum DashboardGlobalFilterOperator {
  EQ,
  NE,
  CONTAINS,
  GT,
  GTE,
  LT,
  LTE
}

record DashboardGlobalFilterBindingSnapshot(
    String widgetKey,
    String fieldId,
    int sortOrder) {
}

record DashboardGlobalFilterSnapshot(
    String filterKey,
    String name,
    DashboardGlobalFilterOperator operator,
    Object defaultValue,
    List<DashboardGlobalFilterBindingSnapshot> bindings,
    int sortOrder) {
}

enum DashboardInteractionEvent {
  SELECT
}

record DashboardInteractionSnapshot(
    String interactionKey,
    DashboardInteractionEvent event,
    String sourceWidgetKey,
    String sourceFieldId,
    String targetFilterKey,
    int sortOrder) {
}

record DashboardDetail(
    DashboardAsset dashboard,
    DashboardVersion currentVersion,
    List<DashboardVersion> versions,
    List<DashboardWidgetSnapshot> widgets,
    List<DashboardGlobalFilterSnapshot> globalFilters,
    List<DashboardInteractionSnapshot> interactions) {
}

/** Exact immutable version snapshot, used for history preview and published reads. */
record DashboardVersionDetail(
    DashboardAsset dashboard,
    DashboardVersion version,
    List<DashboardWidgetSnapshot> widgets,
    List<DashboardGlobalFilterSnapshot> globalFilters,
    List<DashboardInteractionSnapshot> interactions) {
}
