package io.yak.ops.common.enums.modeling;

/**
 * Logical model kinds accepted by dimension modeling.
 *
 * <p>The persisted column is a plain {@code VARCHAR} without a database {@code CHECK}: {@code DWS}
 * and {@code ADS} are scheduled extensions, and widening an enum value must not require a DDL
 * change on private deployments. Unknown stored values are surfaced as unreadable instead of being
 * coerced, so a hand-written row can never be edited through the API.
 */
public enum ModelType {
    /** Detail table hanging below a business process. */
    DWD,
    /** Dimension table describing a concept dimension. */
    DIM;

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
}
