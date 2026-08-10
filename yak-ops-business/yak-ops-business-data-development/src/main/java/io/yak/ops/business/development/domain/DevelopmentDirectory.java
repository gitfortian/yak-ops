package io.yak.ops.business.development.domain;

import java.time.Instant;

/** Hierarchical directory used to organize data-development tasks. */
public record DevelopmentDirectory(
    Long id,
    Long parentId,
    String name,
    String path,
    Instant createTime,
    Instant updateTime) {
}
