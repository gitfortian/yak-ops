package io.yak.ops.business.modeling.metric;

import java.util.List;

/**
 * Pure composition rules of a derived metric.
 *
 * <p>The composed English code joins the period abbreviation, the modifier abbreviations in
 * submitted order and the atomic code with {@code _}; the composed Chinese name concatenates the
 * period name, the modifier names and the atomic name without separators. The composition is the
 * mechanism that keeps the caliber registry free of duplicates: identical parts always produce
 * identical values, and a collision is refused instead of stored twice.
 */
public final class DerivedComposition {
    /** Column limit of the composed code, shared with every other metric code. */
    public static final int MAX_CODE = 64;
    /** Column limit of the composed name, shared with every other metric name. */
    public static final int MAX_NAME = 128;

    private DerivedComposition() {}

    /**
     * One composed identity pair.
     *
     * @param enCode composed English code
     * @param cnName composed Chinese name
     */
    public record Composed(String enCode, String cnName) {}

    /**
     * Composes the code and name of one derived metric from its parts.
     *
     * @param periodCode period abbreviation, first code segment
     * @param modifierCodes modifier abbreviations in submitted order
     * @param atomicCode atomic code, last code segment
     * @param periodName period name, first name segment
     * @param modifierNames modifier names in the same order
     * @param atomicName atomic name, last name segment
     * @return the composed pair
     */
    public static Composed compose(
            String periodCode,
            List<String> modifierCodes,
            String atomicCode,
            String periodName,
            List<String> modifierNames,
            String atomicName) {
        var code = new StringBuilder(periodCode);
        for (var modifierCode : modifierCodes) {
            code.append('_').append(modifierCode);
        }
        code.append('_').append(atomicCode);
        var name = new StringBuilder(periodName);
        for (var modifierName : modifierNames) {
            name.append(modifierName);
        }
        name.append(atomicName);
        return new Composed(code.toString(), name.toString());
    }

    /**
     * Whether a composed pair fits its column limits.
     *
     * @param composed composed pair
     * @return true when both values fit
     */
    public static boolean fits(Composed composed) {
        return composed.enCode().length() <= MAX_CODE && composed.cnName().length() <= MAX_NAME;
    }
}
