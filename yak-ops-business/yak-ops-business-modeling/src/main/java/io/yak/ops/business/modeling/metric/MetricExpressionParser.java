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
 *
 * <p>Every failure names the 1-based character position of the offending token instead of a
 * generic parse failure, so the API response can point at the exact spot. Positions refer to the
 * submitted text; when the input runs out, the position is one past the last character.
 */
public final class MetricExpressionParser {
    private MetricExpressionParser() {}

    private record Token(String text, int start) {}

    private static final class State {
        final List<Token> tokens;
        final int end;
        int cursor;

        State(List<Token> tokens, int end) {
            this.tokens = tokens;
            this.end = end;
        }
    }

    /**
     * Parses one expression and returns the referenced codes in appearance order.
     *
     * @param expression submitted expression text
     * @return referenced codes; duplicates preserved, the service deduplicates for storage
     * @throws IllegalArgumentException when the expression is blank, malformed, or carries a token
     *         outside the grammar; the message always names the 1-based character position
     */
    public static List<String> references(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is empty");
        }
        var references = new ArrayList<String>();
        var state = new State(tokenize(expression), expression.length() + 1);
        parseExpression(state, references);
        if (state.cursor != state.tokens.size()) {
            throw new IllegalArgumentException("unexpected token at position " + position(state, state.cursor));
        }
        return List.copyOf(references);
    }

    private static List<Token> tokenize(String expression) {
        var tokens = new ArrayList<Token>();
        var index = 0;
        var length = expression.length();
        while (index < length) {
            var current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (isOperator(current) || current == '(' || current == ')') {
                tokens.add(new Token(String.valueOf(current), index));
                index++;
                continue;
            }
            if (Character.isDigit(current) || current == '.') {
                var start = index;
                var dots = 0;
                while (index < length
                        && (Character.isDigit(expression.charAt(index)) || expression.charAt(index) == '.')) {
                    if (expression.charAt(index) == '.' && ++dots > 1) {
                        throw new IllegalArgumentException("malformed number at position " + (index + 1));
                    }
                    index++;
                }
                var literal = expression.substring(start, index);
                if (literal.equals(".")) {
                    throw new IllegalArgumentException("malformed number at position " + (start + 1));
                }
                tokens.add(new Token(literal, start));
                continue;
            }
            if (isIdentifierHead(current)) {
                var start = index;
                while (index < length && isIdentifierPart(expression.charAt(index))) index++;
                tokens.add(new Token(expression.substring(start, index), start));
                continue;
            }
            throw new IllegalArgumentException("illegal character at position " + (index + 1));
        }
        if (tokens.isEmpty()) throw new IllegalArgumentException("expression is empty");
        return tokens;
    }

    private static void parseExpression(State state, List<String> references) {
        parseTerm(state, references);
        while (state.cursor < state.tokens.size()
                && isSign(state.tokens.get(state.cursor).text())) {
            state.cursor++;
            parseTerm(state, references);
        }
    }

    private static void parseTerm(State state, List<String> references) {
        parseFactor(state, references);
        while (state.cursor < state.tokens.size()
                && isProduct(state.tokens.get(state.cursor).text())) {
            state.cursor++;
            parseFactor(state, references);
        }
    }

    private static void parseFactor(State state, List<String> references) {
        if (state.cursor >= state.tokens.size()) {
            throw new IllegalArgumentException("expression ends at position " + state.end);
        }
        var token = state.tokens.get(state.cursor);
        if (isSign(token.text())) {
            state.cursor++;
            parseFactor(state, references);
            return;
        }
        if (token.text().equals("(")) {
            state.cursor++;
            parseExpression(state, references);
            if (state.cursor >= state.tokens.size()
                    || !state.tokens.get(state.cursor).text().equals(")")) {
                throw new IllegalArgumentException(
                        "unbalanced parenthesis at position " + position(state, state.cursor));
            }
            state.cursor++;
            return;
        }
        if (token.text().matches("\\d+(\\.\\d+)?")) {
            state.cursor++;
            return;
        }
        if (token.text().matches("[a-z][a-z0-9_]{0,63}")) {
            references.add(token.text());
            state.cursor++;
            return;
        }
        throw new IllegalArgumentException("illegal token at position " + (token.start() + 1));
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

    /** 1-based character position of the token, or one past the end when the input ran out. */
    private static int position(State state, int index) {
        if (index >= state.tokens.size()) return state.end;
        return state.tokens.get(index).start() + 1;
    }
}
