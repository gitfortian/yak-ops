package io.yak.ops.business.modeling.publication;

/**
 * Canonical physical shape of one logical field, used by both generation and verification.
 *
 * <p>{@code family} and the optional precision pair are the comparison basis: two engines spell
 * {@code BIGINT} differently, and Oracle spells it as {@code number(19)}, so the verifier compares
 * this canonical shape rather than the raw type name.
 *
 * @param family logical family the physical type belongs to
 * @param sqlType rendered type for the dialect that produced it
 * @param precision declared precision or string length, null when the type carries none
 * @param scale declared decimal scale, null outside numeric types
 */
public record PhysicalTypeSpec(String family, String sqlType, Integer precision, Integer scale) {

    /** Families a physical column can be compared by. */
    public static final String STRING = "STRING";

    public static final String BIGINT = "BIGINT";
    public static final String DECIMAL = "DECIMAL";
    public static final String DATE = "DATE";
    public static final String TIMESTAMP = "TIMESTAMP";
}
