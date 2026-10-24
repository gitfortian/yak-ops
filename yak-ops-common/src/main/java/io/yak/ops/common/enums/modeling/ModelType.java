package io.yak.ops.common.enums.modeling;

/**
 * Logical model kinds accepted by dimension modeling.
 *
 * <p>The persisted column is a plain {@code VARCHAR} without a database {@code CHECK}, so widening
 * this enum never requires a DDL change on private deployments. Unknown stored values are surfaced
 * as unreadable instead of being coerced, so a hand-written row can never be edited through the API.
 */
public enum ModelType {
    /** Detail table hanging below a business process. */
    DWD,
    /** Dimension table describing a concept dimension. */
    DIM,
    /** Summary table hanging below a business process; carries at least one measure. */
    DWS,
    /** Application table hanging below a data mart; may project dimensions only. */
    ADS;

    /**
     * Resolves a stored value without guessing.
     *
     * @param value persisted type text
     * @return matching kind, or null when the row carries an unknown type
     */
    public static ModelType from(String value) {
        if (value == null) return null;
        for (var type : values()) {
            if (type.name().equals(value)) return type;
        }
        return null;
    }

    /** Whether the kind hangs below a business process in the public layer. */
    public boolean isPublicLayer() {
        return this == DWD || this == DWS;
    }

    /** Whether fields of this kind may carry an aggregate function. */
    public boolean allowsAggregate() {
        return this == DWS || this == ADS;
    }
}
