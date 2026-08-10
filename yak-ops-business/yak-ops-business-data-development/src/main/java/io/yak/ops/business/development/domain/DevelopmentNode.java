package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Lightweight tree node metadata for data-development resources. */
public record DevelopmentNode(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    String name,
    String type,
    @JsonSerialize(using = ToStringSerializer.class) Long projectId,
    @JsonSerialize(using = ToStringSerializer.class) Long directoryId,
    boolean configured,
    Instant createTime,
    Instant updateTime) {
}
