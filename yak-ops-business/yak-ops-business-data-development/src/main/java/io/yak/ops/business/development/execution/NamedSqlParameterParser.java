package io.yak.ops.business.development.execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts value placeholders such as {@code :biz_date} to JDBC placeholders while preserving
 * quoted strings, comments and PostgreSQL-style {@code ::} casts.
 */
public final class NamedSqlParameterParser {

  private NamedSqlParameterParser() {}

  public static ParsedSql parse(String source) {
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }

    StringBuilder jdbcSql = new StringBuilder(source.length());
    List<String> parameters = new ArrayList<>();
    State state = State.NORMAL;
    boolean statementEnded = false;

    for (int i = 0; i < source.length(); i++) {
      char current = source.charAt(i);
      char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

      if (state == State.LINE_COMMENT) {
        jdbcSql.append(current);
        if (current == '\n' || current == '\r') state = State.NORMAL;
        continue;
      }
      if (state == State.BLOCK_COMMENT) {
        jdbcSql.append(current);
        if (current == '*' && next == '/') {
          jdbcSql.append(next);
          i++;
          state = State.NORMAL;
        }
        continue;
      }
      if (state == State.SINGLE_QUOTE) {
        jdbcSql.append(current);
        if (current == '\'' && next == '\'') {
          jdbcSql.append(next);
          i++;
        } else if (current == '\'') {
          state = State.NORMAL;
        }
        continue;
      }
      if (state == State.DOUBLE_QUOTE) {
        jdbcSql.append(current);
        if (current == '"' && next == '"') {
          jdbcSql.append(next);
          i++;
        } else if (current == '"') {
          state = State.NORMAL;
        }
        continue;
      }
      if (state == State.BACKTICK) {
        jdbcSql.append(current);
        if (current == '`') state = State.NORMAL;
        continue;
      }

      if (current == '-' && next == '-') {
        jdbcSql.append(current).append(next);
        i++;
        state = State.LINE_COMMENT;
        continue;
      }
      if (current == '/' && next == '*') {
        jdbcSql.append(current).append(next);
        i++;
        state = State.BLOCK_COMMENT;
        continue;
      }
      if (current == '\'') {
        if (statementEnded) throw multipleStatements();
        jdbcSql.append(current);
        state = State.SINGLE_QUOTE;
        continue;
      }
      if (current == '"') {
        if (statementEnded) throw multipleStatements();
        jdbcSql.append(current);
        state = State.DOUBLE_QUOTE;
        continue;
      }
      if (current == '`') {
        if (statementEnded) throw multipleStatements();
        jdbcSql.append(current);
        state = State.BACKTICK;
        continue;
      }
      if (current == ';') {
        if (statementEnded) throw multipleStatements();
        statementEnded = true;
        continue;
      }
      if (statementEnded) {
        if (!Character.isWhitespace(current)) throw multipleStatements();
        jdbcSql.append(current);
        continue;
      }
      if (current == ':' && next == ':') {
        jdbcSql.append(current).append(next);
        i++;
        continue;
      }
      if (current == ':' && isParameterStart(next)) {
        int end = i + 2;
        while (end < source.length() && isParameterPart(source.charAt(end))) end++;
        String name = source.substring(i + 1, end);
        parameters.add(name);
        jdbcSql.append('?');
        i = end - 1;
        continue;
      }
      jdbcSql.append(current);
    }

    if (state == State.SINGLE_QUOTE || state == State.DOUBLE_QUOTE || state == State.BACKTICK
        || state == State.BLOCK_COMMENT) {
      throw new IllegalArgumentException("SQL 中存在未闭合的引号或注释");
    }
    String normalized = jdbcSql.toString().trim();
    if (normalized.isEmpty()) throw new IllegalArgumentException("SQL 不能为空");
    return new ParsedSql(normalized, List.copyOf(parameters));
  }

  private static boolean isParameterStart(char value) {
    return value == '_' || Character.isLetter(value);
  }

  private static boolean isParameterPart(char value) {
    return value == '_' || Character.isLetterOrDigit(value);
  }

  private static IllegalArgumentException multipleStatements() {
    return new IllegalArgumentException("第一阶段 SQL 数据开发仅允许单条 SQL");
  }

  public record ParsedSql(String jdbcSql, List<String> parameterNames) {}

  private enum State {
    NORMAL,
    SINGLE_QUOTE,
    DOUBLE_QUOTE,
    BACKTICK,
    LINE_COMMENT,
    BLOCK_COMMENT
  }
}
