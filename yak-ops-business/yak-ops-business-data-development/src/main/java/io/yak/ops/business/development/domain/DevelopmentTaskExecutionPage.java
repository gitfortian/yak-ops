package io.yak.ops.business.development.domain;

import java.util.List;

public record DevelopmentTaskExecutionPage(
    List<DevelopmentTaskExecutionSummary> records,
    long total,
    int pageNo,
    int pageSize) {}
