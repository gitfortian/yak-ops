package io.yak.ops.business.modeling.naming;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Pure matching rules for a layer's table-name checker.
 *
 * <p>A conforming suffix begins with {@code <root word>_<process code>} and may carry one
 * {@code _<qualifier>} segment after it, so a summary layer can hold {@code trade_order_1d} and
 * {@code trade_order_1h} side by side. The head consumes the same 41 characters the suffix pattern
 * allows, because {@code fullName} applies that pattern before any naming rule runs.
 */
public final class NamingRuleMatcher {
    /** Longest suffix accepted by {@code ModelServiceImpl.SUFFIX}. */
    public static final int MAX_SUFFIX_LENGTH = 41;

    private NamingRuleMatcher() {}

    /**
     * Finds the dictionary entry whose head prefixes the suffix.
     *
     * <p>The longest match wins, so a dictionary that holds both {@code trade} and
     * {@code trade_order} attributes {@code trade_order_x} to {@code trade} deterministically
     * instead of depending on iteration order.
     *
     * @param suffix submitted model suffix without the layer prefix
     * @param processCode code of the model's own business process
     * @param roots abbreviations of the Workspace naming dictionary
     * @return the matched root word, or empty when the suffix does not conform
     */
    public static Optional<String> match(String suffix, String processCode, Collection<String> roots) {
        if (suffix == null || processCode == null) return Optional.empty();
        return roots.stream()
                .filter(root -> conforms(suffix, head(root, processCode)))
                .max(Comparator.comparingInt(String::length));
    }

    /**
     * Reports whether a head fits the suffix budget.
     * @param root root-word abbreviation
     * @param processCode code of the model's own business process
     * @return true when the composed head is at most 41 characters
     */
    public static boolean fits(String root, String processCode) {
        return head(root, processCode).length() <= MAX_SUFFIX_LENGTH;
    }

    /**
     * Composes the required head.
     * @param root root-word abbreviation
     * @param processCode code of the model's own business process
     * @return {@code <root>_<process>}
     */
    public static String head(String root, String processCode) {
        return root + "_" + processCode;
    }

    /** Requires the head as a whole segment: either the suffix ends there or a qualifier follows. */
    private static boolean conforms(String suffix, String head) {
        return suffix.equals(head) || suffix.startsWith(head + "_");
    }
}
