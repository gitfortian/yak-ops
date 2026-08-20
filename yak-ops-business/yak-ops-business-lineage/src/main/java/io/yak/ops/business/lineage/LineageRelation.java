package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.Instant;

/** Directed upstream-to-downstream edge with optional provenance evidence. */
public record LineageRelation(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    @JsonSerialize(using = ToStringSerializer.class) long sourceAssetId,
    @JsonSerialize(using = ToStringSerializer.class) long targetAssetId,
    LineageRelationType relationType,
    String sourceType,
    String sourceId,
    String expression,
    BigDecimal confidence,
    String version,
    Instant observedAt,
    JsonNode properties,
    Instant createTime,
    Instant updateTime) {
}
