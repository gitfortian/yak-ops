package io.yak.ops.business.development.domain;

import java.util.List;

public record DevelopmentReleaseDetail(
    DevelopmentReleaseSummary release,
    DevelopmentTaskRevision currentRevision,
    List<DevelopmentTaskRevisionSummary> revisions) {}
