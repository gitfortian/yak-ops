package io.yak.ops.business.dataset;

import java.time.Instant;

/** Stable BI-consumption asset. Dataset versions freeze their own executable source contract. */
public record Dataset(
    long id,
    String name,
    String description,
    DatasetStatus status,
    Long currentVersionId,
    Instant createTime,
    Instant updateTime) {
}
