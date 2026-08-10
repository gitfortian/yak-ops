package io.yak.ops.business.development.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NamedSqlParameterParserTest {

  @Test
  void shouldParseNamedValueParametersWithoutTouchingQuotesCommentsOrCasts() {
    NamedSqlParameterParser.ParsedSql parsed = NamedSqlParameterParser.parse("""
        SELECT ':literal' AS literal,
               created_at::date AS created_date
        FROM orders
        WHERE biz_date = :biz_date
          AND tenant_id = :tenant_id
          -- :ignored
        """);

    assertThat(parsed.parameterNames()).containsExactly("biz_date", "tenant_id");
    assertThat(parsed.jdbcSql()).contains("biz_date = ?", "tenant_id = ?", "created_at::date");
    assertThat(parsed.jdbcSql()).contains("':literal'", "-- :ignored");
  }

  @Test
  void shouldRejectMultipleStatements() {
    assertThatThrownBy(() -> NamedSqlParameterParser.parse("select 1; delete from t"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("单条 SQL");
  }
}
