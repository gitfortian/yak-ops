package io.yak.ops.common.enums.tag;

/** Direction of a batch binding command. */
public enum TagBindingOperation {
    /** Attach the listed values to every listed asset. */
    ADD,
    /** Detach the listed values from every listed asset. */
    REMOVE
}
