package io.yak.ops.common.enums.modeling;

/** Join style of a semantic model relation; LEFT keeps fact rows when a dimension row is missing. */
public enum RelationJoinType {
    INNER,
    LEFT
}
