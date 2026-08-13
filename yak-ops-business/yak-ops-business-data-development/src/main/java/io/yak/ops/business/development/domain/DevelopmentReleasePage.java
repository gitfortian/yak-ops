package io.yak.ops.business.development.domain;

import java.util.List;

public record DevelopmentReleasePage(
    List<DevelopmentReleaseSummary> records,
    long total,
    int pageNo,
    int pageSize,
    long onlineCount,
    long offlineCount,
    long disabledCount) {}
