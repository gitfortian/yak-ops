package io.yak.ops.common.enums.modeling;

/**
 * Fixed planning levels; parent identity is immutable after creation.
 *
 * <p>{@link #CATEGORY}, {@link #DOMAIN} and {@link #PROCESS} form the public-layer business tree.
 * {@link #DATASET} and {@link #SUBJECT} form the parallel application-layer tree, where a data mart
 * is a root and subject areas hang below it. Both trees reserve codes in the same global namespace.
 */
public enum NodeKind {
    CATEGORY,
    DOMAIN,
    PROCESS,
    DATASET,
    SUBJECT;

    /**
     * Returns the level owning this level.
     *
     * @return parent level, or null for a root level
     */
    public NodeKind parentKind() {
        return switch (this) {
            case CATEGORY, DATASET -> null;
            case DOMAIN -> CATEGORY;
            case PROCESS -> DOMAIN;
            case SUBJECT -> DATASET;
        };
    }

    /** @return whether a node of this level carries an assigned owner */
    public boolean hasOwner() {
        return this != SUBJECT;
    }

    /** @return whether a node of this level carries a business English name beside its abbreviation */
    public boolean hasEnglishName() {
        return this == DATASET || this == SUBJECT;
    }
}
