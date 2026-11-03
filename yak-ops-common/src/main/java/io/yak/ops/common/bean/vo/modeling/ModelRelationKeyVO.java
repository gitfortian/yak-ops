package io.yak.ops.common.bean.vo.modeling;

/**
 * One ordered join key pair after resolution against the current model fields. {@code resolved}
 * is false when a field code no longer exists on its model ("字段已移除"); {@code standardBound}
 * is false when the field exists but carries no data standard.
 */
public record ModelRelationKeyVO(
        int ordinal,
        String fromFieldCode,
        String toFieldCode,
        String fromFieldName,
        String toFieldName,
        boolean resolved,
        boolean standardBound) {}
