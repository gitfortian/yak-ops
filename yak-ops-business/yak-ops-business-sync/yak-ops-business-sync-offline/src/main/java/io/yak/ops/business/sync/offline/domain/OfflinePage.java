package io.yak.ops.business.sync.offline.domain;

import java.util.List;

/** 与具体分页框架无关的业务分页结果。 */
public record OfflinePage<T>(
    List<T> records,
    long total,
    long pages,
    long current,
    long pageSize) {}
