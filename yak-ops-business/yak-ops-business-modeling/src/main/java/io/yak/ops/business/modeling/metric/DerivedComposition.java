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
            appendCode(code, modifierCode);
        }
        appendCode(code, atomicCode);
        var name = new StringBuilder(periodName);
        for (var modifierName : modifierNames) {
            appendName(name, modifierName);
        }
        appendName(name, atomicName);
        return new Composed(code.toString(), name.toString());
    }

    /**
     * Appends one code segment, skipping a segment identical to the one before it.
     *
     * <p>Two parts can carry the same abbreviation — a "支付" modifier over a "支付" atomic part
     * both resolve to {@code pay} — and repeating it in the code would only suggest two segments.
     */
    private static void appendCode(StringBuilder code, String segment) {
        if (segment == null || segment.isBlank()) return;
        var lastSeparator = code.lastIndexOf("_");
        var last = code.substring(lastSeparator + 1);
        if (!last.isEmpty() && last.equals(segment)) return;
        if (code.length() > 0) code.append('_');
        code.append(segment);
    }

    /**
     * Appends one name segment, collapsing the overlap it shares with the name built so far.
     *
     * <p>A "支付" modifier over a "支付金额" atomic part would otherwise read "支付支付金额": the
     * longest suffix of the current name that is also a prefix of the segment is kept once.
     */
    private static void appendName(StringBuilder name, String segment) {
        if (segment == null || segment.isEmpty()) return;
        var current = name.toString();
        var overlap = Math.min(current.length(), segment.length());
        while (overlap > 0 && !current.regionMatches(current.length() - overlap, segment, 0, overlap)) {
            overlap--;
        }
        name.append(segment, overlap, segment.length());
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
