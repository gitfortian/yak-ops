package io.yak.ops.common.enums.modeling;

/** Fixed planning levels; parent identity is immutable after creation. */
public enum NodeKind {
    CATEGORY,
    DOMAIN,
    PROCESS;

    public NodeKind parentKind() {
        return switch (this) {
            case CATEGORY -> null;
            case DOMAIN -> CATEGORY;
            case PROCESS -> DOMAIN;
        };
    }
}
