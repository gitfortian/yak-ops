package io.yak.ops.business.modeling.metric;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure parser of the composite-metric expression grammar.
 *
 * <p>The grammar is arithmetic only: {@code +}, {@code -}, {@code *}, {@code /}, parentheses,
 * numeric literals, whitespace and derived-metric codes. The parser validates syntax and collects
 * the referenced codes in appearance order; it never evaluates and knows nothing about
 * Workspace membership, which the service resolves afterwards.
 */
public final class MetricExpressionParser {
    private MetricExpressionParser() {}

    /**
     * Parses one expression and returns the referenced codes in appearance order.
     *
     * @param expression submitted expression text
     * @return referenced codes; duplicates preserved, the service deduplicates for storage
     * @throws IllegalArgumentException when the expression is blank, malformed, or carries a token
     *         outside the grammar
     */
    public static List<String> references(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is empty");
        }
        var tokens = tokenize(expression);
        var cursor = new int[] {0};
        var references = new ArrayList<String>();
        parseExpression(tokens, cursor, references);
        if (cursor[0] != tokens.size()) {
            throw new IllegalArgumentException("unexpected token at position " + position(tokens, cursor[0]));
        }
        return List.copyOf(references);
    }

    private static List<String> tokenize(String expression) {
        var tokens = new ArrayList<String>();
        var index = 0;
        var length = expression.length();
        while (index < length) {
            var current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (isOperator(current) || current == '(' || current == ')') {
                tokens.add(String.valueOf(current));
                index++;
                continue;
            }
            if (Character.isDigit(current) || current == '.') {
                var start = index;
                var dots = 0;
                while (index < length
                        && (Character.isDigit(expression.charAt(index)) || expression.charAt(index) == '.')) {
                    if (expression.charAt(index) == '.' && ++dots > 1) {
                        throw new IllegalArgumentException("malformed number at position " + index);
                    }
                    index++;
                }
                var literal = expression.substring(start, index);
                if (literal.equals(".")) throw new IllegalArgumentException("malformed number at position " + start);
                tokens.add(literal);
                continue;
            }
            if (isIdentifierHead(current)) {
                var start = index;
                while (index < length && isIdentifierPart(expression.charAt(index))) index++;
                tokens.add(expression.substring(start, index));
                continue;
            }
            throw new IllegalArgumentException("illegal character at position " + index);
        }
        if (tokens.isEmpty()) throw new IllegalArgumentException("expression is empty");
        return tokens;
    }

    private static void parseExpression(List<String> tokens, int[] cursor, List<String> references) {
        parseTerm(tokens, cursor, references);
        while (cursor[0] < tokens.size() && isSign(tokens.get(cursor[0]))) {
            cursor[0]++;
            parseTerm(tokens, cursor, references);
        }
    }

    private static void parseTerm(List<String> tokens, int[] cursor, List<String> references) {
        parseFactor(tokens, cursor, references);
        while (cursor[0] < tokens.size() && isProduct(tokens.get(cursor[0]))) {
            cursor[0]++;
            parseFactor(tokens, cursor, references);
        }
    }

    private static void parseFactor(List<String> tokens, int[] cursor, List<String> references) {
        if (cursor[0] >= tokens.size()) {
            throw new IllegalArgumentException("expression ends unexpectedly");
        }
        var token = tokens.get(cursor[0]);
        if (isSign(token) || token.equals("+")) {
            cursor[0]++;
            parseFactor(tokens, cursor, references);
            return;
        }
        if (token.equals("(")) {
            cursor[0]++;
            parseExpression(tokens, cursor, references);
            if (cursor[0] >= tokens.size() || !tokens.get(cursor[0]).equals(")")) {
                throw new IllegalArgumentException("unbalanced parenthesis");
            }
            cursor[0]++;
            return;
        }
        if (token.matches("\\d+(\\.\\d+)?")) {
            cursor[0]++;
            return;
        }
        if (token.matches("[a-z][a-z0-9_]{0,63}")) {
            references.add(token);
            cursor[0]++;
            return;
        }
        throw new IllegalArgumentException("illegal token at position " + position(tokens, cursor[0]));
    }

    private static boolean isSign(String token) {
        return token.equals("+") || token.equals("-");
    }

    private static boolean isProduct(String token) {
        return token.equals("*") || token.equals("/");
    }

    private static boolean isOperator(char value) {
        return value == '+' || value == '-' || value == '*' || value == '/';
    }

    private static boolean isIdentifierHead(char value) {
        return value >= 'a' && value <= 'z';
    }

    private static boolean isIdentifierPart(char value) {
        return isIdentifierHead(value) || (value >= '0' && value <= '9') || value == '_';
    }

    private static int position(List<String> tokens, int index) {
        if (index >= tokens.size()) return -1;
        return index;
    }
}
