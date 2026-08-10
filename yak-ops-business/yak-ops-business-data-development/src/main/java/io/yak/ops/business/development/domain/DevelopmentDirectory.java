package io.yak.ops.business.development.domain;

import java.time.Instant;

/** Hierarchical directory used to organize data-development tasks inside a project. */
public record DevelopmentDirectory(
    Long id,
    Long projectId,
    Long parentId,
    String name,
    String path,
    Instant createTime,
    Instant updateTime) {
}
