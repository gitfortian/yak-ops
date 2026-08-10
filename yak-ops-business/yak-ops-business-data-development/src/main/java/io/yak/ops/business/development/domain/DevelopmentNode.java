package io.yak.ops.business.development.domain;

import java.time.Instant;

/** Lightweight tree node metadata for data-development resources. */
public record DevelopmentNode(
    Long id,
    String name,
    String type,
    Long projectId,
    Long directoryId,
    boolean configured,
    Instant createTime,
    Instant updateTime) {
}
