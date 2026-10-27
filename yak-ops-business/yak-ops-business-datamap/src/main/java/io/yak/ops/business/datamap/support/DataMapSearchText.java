package io.yak.ops.business.datamap.support;

/**
 * Escapes user text for the catalog's {@code LIKE} predicates.
 *
 * <p>The statements use the explicit {@code ESCAPE '!'} clause, so a literal percent sign
 * searches for a percent sign instead of matching everything.
 */
public final class DataMapSearchText {

    private static final char ESCAPE = '!';

    private DataMapSearchText() {}

    /**
     * Wraps a trimmed keyword into a contains pattern.
     *
     * @param keyword trimmed keyword, or null when the caller submitted none
     * @return the escaped pattern, or null when no keyword was given
     */
    public static String containsPattern(String keyword) {
        if (keyword == null) return null;
        StringBuilder pattern = new StringBuilder(keyword.length() + 8).append('%');
        for (int index = 0; index < keyword.length(); index++) {
            char value = keyword.charAt(index);
            if (value == ESCAPE || value == '%' || value == '_') pattern.append(ESCAPE);
            pattern.append(value);
        }
        return pattern.append('%').toString();
    }
}
