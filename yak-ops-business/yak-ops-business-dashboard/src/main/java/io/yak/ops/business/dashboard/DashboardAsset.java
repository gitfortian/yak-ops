package io.yak.ops.business.dashboard;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** Stable Dashboard identity. Layout/query composition lives in immutable versions. */
public record DashboardAsset(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    String name,
    String description,
    @JsonSerialize(using = ToStringSerializer.class) Long currentVersionId,
    int currentVersionNo,
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

record DashboardDetail(
    DashboardAsset dashboard,
    DashboardVersion currentVersion,
    List<DashboardVersion> versions,
    List<DashboardWidgetSnapshot> widgets) {
}
