package io.yak.ops.common.enums.modeling.standard;

/** Shape of one node in the standard catalog: a plain directory or a typed standard set. */
public enum StandardNodeType {
    /** Holds directories, standard sets and records of either kind. */
    DIRECTORY,
    /** Holds records of exactly one kind and can never be a parent. */
    STANDARD_SET
}
