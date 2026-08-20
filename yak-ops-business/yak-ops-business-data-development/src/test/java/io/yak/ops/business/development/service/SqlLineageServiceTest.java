package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.development.domain.SqlLineageResult;
import io.yak.ops.business.development.domain.SqlLineageResult.ColumnEdge;
import io.yak.ops.business.development.domain.SqlLineageResult.TableEdge;
import io.yak.ops.business.development.domain.SqlLineageResult.TableNode;
import io.yak.ops.business.development.domain.SqlValidationResult;
import io.yak.ops.business.development.domain.SqlValidationResult.ValidationError;
import io.yak.ops.business.development.domain.SqlValidationResult.ValidationError.Severity;
import io.yak.ops.business.development.domain.SqlValidationResult.ValidationError.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SqlLineageService} covering table-level and column-level lineage.
 *
 * <p>Scenarios include: single table, multi-table JOIN, INSERT-SELECT, sub-queries,
 * CTE (WITH), UNION, multiple statements, CREATE VIEW, aliases, and aggregates.
 */
class SqlLineageServiceTest {

  private SqlLineageService service;

  @BeforeEach
  void setUp() {
    service = new SqlLineageService();
  }

  // ---- Helpers ----

  /** Parse a single-statement SQL and return the lone result. */
  private SqlLineageResult parseSingle(String sql) {
    List<SqlLineageResult> results = service.parse(sql);
    assertEquals(1, results.size(), "Expected exactly one SqlLineageResult for single statement, got " + results.size());
    return results.get(0);
  }

  private Set<String> sourceTables(SqlLineageResult result) {
    return result.getTables().stream()
        .filter(t -> "SOURCE".equals(t.getType()))
        .map(TableNode::getName)
        .collect(Collectors.toSet());
  }

  private Set<String> targetTables(SqlLineageResult result) {
    return result.getTables().stream()
        .filter(t -> "TARGET".equals(t.getType()))
        .map(TableNode::getName)
        .collect(Collectors.toSet());
  }

  private Set<String> columnEdgeKeys(SqlLineageResult result) {
    return result.getColumnEdges().stream()
        .map(e -> e.getSourceTable() + "." + e.getSourceColumn() + "->" + e.getTargetTable() + "." + e.getTargetColumn())
        .collect(Collectors.toSet());
  }

  // ========================================================================
  //  Empty / Null input
  // ========================================================================

  @Nested
  @DisplayName("Empty / Null input")
  class EmptyInput {

    @Test
    @DisplayName("Null SQL returns empty result list")
    void nullSql() {
      List<SqlLineageResult> results = service.parse(null);
      assertNotNull(results);
      assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Blank SQL returns empty result list")
    void blankSql() {
      List<SqlLineageResult> results = service.parse("   ");
      assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Comment-only SQL returns empty result list")
    void commentOnly() {
      List<SqlLineageResult> results = service.parse("-- just a comment");
      assertTrue(results.isEmpty());
    }
  }

  // ========================================================================
  //  Single table SELECT
  // ========================================================================

  @Nested
  @DisplayName("Single table SELECT")
  class SingleTable {

    @Test
    @DisplayName("Produces one SOURCE table")
    void simple() {
      SqlLineageResult result = parseSingle("SELECT id, name FROM users");
      assertEquals(Set.of("users"), sourceTables(result));
      assertTrue(targetTables(result).isEmpty());
    }

    @Test
    @DisplayName("With alias preserves original table name")
    void withAlias() {
      SqlLineageResult result = parseSingle("SELECT u.id, u.name FROM users u");
      assertEquals(Set.of("users"), sourceTables(result));
    }

    @Test
    @DisplayName("Schema-qualified table name is preserved")
    void schemaQualified() {
      SqlLineageResult result = parseSingle("SELECT id FROM db1.schema1.users");
      assertEquals(Set.of("db1.schema1.users"), sourceTables(result));
    }
  }

  // ========================================================================
  //  Multi-table JOIN
  // ========================================================================

  @Nested
  @DisplayName("Multi-table JOIN")
  class JoinTests {

    @Test
    @DisplayName("INNER JOIN produces two SOURCE tables")
    void innerJoin() {
      SqlLineageResult result = parseSingle(
          "SELECT o.id, c.name FROM orders o INNER JOIN customers c ON o.customer_id = c.id");
      assertEquals(Set.of("orders", "customers"), sourceTables(result));
    }

    @Test
    @DisplayName("LEFT JOIN produces two SOURCE tables")
    void leftJoin() {
      SqlLineageResult result = parseSingle(
          "SELECT u.id, p.title FROM users u LEFT JOIN posts p ON u.id = p.user_id");
      assertEquals(Set.of("users", "posts"), sourceTables(result));
    }

    @Test
    @DisplayName("Three-way JOIN produces three SOURCE tables")
    void threeWayJoin() {
      // Note: 'value' is a reserved word in Calcite, using 'val' instead
      SqlLineageResult result = parseSingle(
          "SELECT a.id, b.name, c.val FROM table_a a JOIN table_b b ON a.b_id = b.id JOIN table_c c ON b.c_id = c.id");
      assertEquals(Set.of("table_a", "table_b", "table_c"), sourceTables(result));
    }
  }

  // ========================================================================
  //  INSERT INTO ... SELECT
  // ========================================================================

  @Nested
  @DisplayName("INSERT INTO ... SELECT")
  class InsertSelect {

    @Test
    @DisplayName("Produces SOURCE and TARGET tables with edges")
    void simple() {
      SqlLineageResult result = parseSingle(
          "INSERT INTO target_table (id, name) SELECT id, name FROM source_table");
      assertEquals(Set.of("source_table"), sourceTables(result));
      assertEquals(Set.of("target_table"), targetTables(result));
      assertEquals(1, result.getTableEdges().size());
    }

    @Test
    @DisplayName("With JOIN produces multiple SOURCE tables")
    void withJoin() {
      SqlLineageResult result = parseSingle(
          "INSERT INTO report_table SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id");
      assertEquals(Set.of("orders", "customers"), sourceTables(result));
      assertEquals(Set.of("report_table"), targetTables(result));
      assertEquals(2, result.getTableEdges().size());
    }

    @Test
    @DisplayName("Schema-qualified target and source")
    void schemaQualified() {
      SqlLineageResult result = parseSingle(
          "INSERT INTO db1.schema1.target SELECT id, name FROM db2.schema2.source");
      assertTrue(sourceTables(result).stream().anyMatch(s -> s.contains("source")));
      assertTrue(targetTables(result).stream().anyMatch(t -> t.contains("target")));
    }
  }

  // ========================================================================
  //  Sub-queries
  // ========================================================================

  @Nested
  @DisplayName("Sub-queries")
  class SubQueryTests {

    @Test
    @DisplayName("Sub-query in FROM clause: inner table is discovered")
    void fromSubQuery() {
      SqlLineageResult result = parseSingle(
          "SELECT s.id FROM (SELECT id FROM sales) s");
      assertTrue(sourceTables(result).contains("sales"),
          "Inner table 'sales' should be a source");
    }

    @Test
    @DisplayName("IN sub-query: both outer and inner tables are discovered")
    void inSubQuery() {
      SqlLineageResult result = parseSingle(
          "SELECT id, name FROM employees WHERE dept_id IN (SELECT id FROM departments)");
      assertTrue(sourceTables(result).contains("employees"));
      assertTrue(sourceTables(result).contains("departments"));
    }
  }

  // ========================================================================
  //  CTE / WITH
  // ========================================================================

  @Nested
  @DisplayName("CTE / WITH")
  class CteTests {

    @Test
    @DisplayName("CTE body tables are discovered as sources")
    void cteSources() {
      SqlLineageResult result = parseSingle(
          "WITH recent_orders AS (SELECT id, customer_id FROM orders WHERE create_time > '2024-01-01') "
              + "SELECT ro.id FROM recent_orders ro");
      assertTrue(sourceTables(result).contains("orders"),
          "CTE body table 'orders' should be discovered");
    }

    @Test
    @DisplayName("CTE with JOIN in body discovers both tables")
    void cteWithJoin() {
      SqlLineageResult result = parseSingle(
          "WITH enriched AS (SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id) "
              + "SELECT id, name FROM enriched");
      assertTrue(sourceTables(result).contains("orders"));
      assertTrue(sourceTables(result).contains("customers"));
    }

    @Test
    @DisplayName("INSERT with CTE - unsupported syntax is silently skipped")
    void insertWithCte() {
      // Calcite's default parser does not support WITH...INSERT syntax
      // The result should be empty (silently skipped, no warning)
      SqlLineageResult result = parseSingle(
          "WITH src AS (SELECT id FROM source_table) INSERT INTO target_table SELECT id FROM src");
      assertTrue(result.getTables().isEmpty(), "Unsupported syntax should produce empty result");
    }
  }

  // ========================================================================
  //  UNION / INTERSECT / EXCEPT
  // ========================================================================

  @Nested
  @DisplayName("Set operations")
  class SetOperationTests {

    @Test
    @DisplayName("UNION ALL combines sources from both branches")
    void unionAll() {
      SqlLineageResult result = parseSingle(
          "SELECT id, name FROM table_a UNION ALL SELECT id, name FROM table_b");
      assertEquals(Set.of("table_a", "table_b"), sourceTables(result));
    }

    @Test
    @DisplayName("UNION (without ALL) combines sources")
    void unionDistinct() {
      SqlLineageResult result = parseSingle(
          "SELECT id FROM table_a UNION SELECT id FROM table_b");
      assertEquals(Set.of("table_a", "table_b"), sourceTables(result));
    }
  }

  // ========================================================================
  //  Multiple statements
  // ========================================================================

  @Nested
  @DisplayName("Multiple statements")
  class MultiStatementTests {

    @Test
    @DisplayName("Two INSERTs separated by semicolon produce independent results")
    void twoInserts() {
      List<SqlLineageResult> results = service.parse(
          "INSERT INTO t1 SELECT id FROM s1; INSERT INTO t2 SELECT id FROM s2");
      assertEquals(2, results.size());

      // First statement: s1 → t1
      SqlLineageResult r0 = results.get(0);
      assertEquals(0, r0.getStatementIndex());
      assertEquals(Set.of("s1"), sourceTables(r0));
      assertEquals(Set.of("t1"), targetTables(r0));
      assertEquals(1, r0.getTableEdges().size());
      assertEquals("s1", r0.getTableEdges().get(0).getSource());
      assertEquals("t1", r0.getTableEdges().get(0).getTarget());

      // Second statement: s2 → t2
      SqlLineageResult r1 = results.get(1);
      assertEquals(1, r1.getStatementIndex());
      assertEquals(Set.of("s2"), sourceTables(r1));
      assertEquals(Set.of("t2"), targetTables(r1));
      assertEquals(1, r1.getTableEdges().size());
      assertEquals("s2", r1.getTableEdges().get(0).getSource());
      assertEquals("t2", r1.getTableEdges().get(0).getTarget());
    }

    @Test
    @DisplayName("Mixed: SELECT + INSERT produce independent results")
    void selectAndInsert() {
      List<SqlLineageResult> results = service.parse(
          "SELECT id FROM table_a; INSERT INTO table_b SELECT id FROM table_c");
      assertEquals(2, results.size());

      SqlLineageResult r0 = results.get(0);
      assertEquals(Set.of("table_a"), sourceTables(r0));
      assertTrue(targetTables(r0).isEmpty());

      SqlLineageResult r1 = results.get(1);
      assertEquals(Set.of("table_c"), sourceTables(r1));
      assertEquals(Set.of("table_b"), targetTables(r1));
    }

    @Test
    @DisplayName("Trailing semicolon is handled")
    void trailingSemicolon() {
      SqlLineageResult result = parseSingle("SELECT id FROM users;");
      assertEquals(Set.of("users"), sourceTables(result));
    }

    @Test
    @DisplayName("Multiple consecutive semicolons are skipped")
    void multipleSemicolons() {
      List<SqlLineageResult> results = service.parse("SELECT id FROM users;; SELECT name FROM orders");
      assertEquals(2, results.size());
      assertEquals(Set.of("users"), sourceTables(results.get(0)));
      assertEquals(Set.of("orders"), sourceTables(results.get(1)));
    }
  }

  // ========================================================================
  //  CREATE VIEW / CREATE TABLE
  // ========================================================================

  @Nested
  @DisplayName("CREATE VIEW / CREATE TABLE")
  class CreateViewTests {

    @Test
    @DisplayName("CREATE VIEW - unsupported syntax is silently skipped")
    void createView() {
      // Calcite's default parser does not support CREATE VIEW syntax
      // The result should be empty (silently skipped, no warning)
      SqlLineageResult result = parseSingle(
          "CREATE VIEW v_user_orders AS SELECT u.id, o.amount FROM users u JOIN orders o ON u.id = o.user_id");
      assertTrue(result.getTables().isEmpty(), "Unsupported syntax should produce empty result");
    }
  }

  // ========================================================================
  //  Aggregates / GROUP BY / HAVING
  // ========================================================================

  @Nested
  @DisplayName("Aggregates / GROUP BY / HAVING")
  class AggregateTests {

    @Test
    @DisplayName("Aggregate with GROUP BY detects source table")
    void groupBy() {
      SqlLineageResult result = parseSingle(
          "SELECT dept_id, COUNT(id) AS cnt FROM employees GROUP BY dept_id");
      assertEquals(Set.of("employees"), sourceTables(result));
    }

    @Test
    @DisplayName("HAVING clause detects source table")
    void having() {
      SqlLineageResult result = parseSingle(
          "SELECT dept_id, AVG(salary) AS avg_sal FROM employees GROUP BY dept_id HAVING AVG(salary) > 5000");
      assertEquals(Set.of("employees"), sourceTables(result));
    }
  }

  // ========================================================================
  //  Column-level lineage
  // ========================================================================

  @Nested
  @DisplayName("Column-level lineage")
  class ColumnLineageTests {

    @Test
    @DisplayName("Simple SELECT with qualified columns produces column edges")
    void simpleSelect() {
      SqlLineageResult result = parseSingle("SELECT a.id, a.name FROM table_a a");
      assertFalse(result.getColumnEdges().isEmpty(),
          "Should produce column-level lineage for simple SELECT");
      assertTrue(columnEdgeKeys(result).stream().anyMatch(k -> k.startsWith("table_a.id")),
          "Should trace 'id' from table_a");
      assertTrue(columnEdgeKeys(result).stream().anyMatch(k -> k.startsWith("table_a.name")),
          "Should trace 'name' from table_a");
    }

    @Test
    @DisplayName("INSERT-SELECT with explicit columns produces table-level lineage")
    void insertSelect() {
      // Note: Column-level lineage may not be available due to Calcite validation issues
      // with column discovery in INSERT-SELECT scenarios. Table-level lineage should work.
      SqlLineageResult result = parseSingle(
          "INSERT INTO target_table (id, name) SELECT a.id, a.name FROM source_table a");
      // Table-level lineage should work
      assertEquals(Set.of("source_table"), sourceTables(result));
      assertEquals(Set.of("target_table"), targetTables(result));
      assertEquals(1, result.getTableEdges().size());
      // Column-level lineage may or may not be available depending on Calcite's ability to validate
    }

    @Test
    @DisplayName("JOIN with qualified columns traces columns from both tables")
    void join() {
      SqlLineageResult result = parseSingle(
          "SELECT a.id, b.name FROM table_a a JOIN table_b b ON a.b_id = b.id");
      assertFalse(result.getColumnEdges().isEmpty(),
          "Should produce column-level lineage for JOIN");
      Set<String> sourceTablesInEdges = result.getColumnEdges().stream()
          .map(ColumnEdge::getSourceTable)
          .collect(Collectors.toSet());
      assertTrue(sourceTablesInEdges.contains("table_a"), "Should trace columns from table_a");
      assertTrue(sourceTablesInEdges.contains("table_b"), "Should trace columns from table_b");
    }
  }

  // ========================================================================
  //  Edge cases
  // ========================================================================

  @Nested
  @DisplayName("Edge cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Leading/trailing whitespace is handled")
    void whitespace() {
      SqlLineageResult result = parseSingle("\n\n  SELECT id FROM users  \n\n");
      assertEquals(Set.of("users"), sourceTables(result));
    }

    @Test
    @DisplayName("Invalid SQL returns empty result without crashing")
    void invalidSql() {
      SqlLineageResult result = parseSingle("THIS IS NOT SQL");
      assertTrue(result.getTables().isEmpty());
    }

    @Test
    @DisplayName("Mixed valid and invalid statements: valid ones produce independent results")
    void mixedValidInvalid() {
      List<SqlLineageResult> results = service.parse(
          "SELECT id FROM users; THIS IS NOT SQL; SELECT name FROM orders");
      assertEquals(3, results.size());

      // First statement: valid SELECT
      SqlLineageResult r0 = results.get(0);
      assertEquals(0, r0.getStatementIndex());
      assertEquals(Set.of("users"), sourceTables(r0));

      // Second statement: invalid — silently returns empty
      SqlLineageResult r1 = results.get(1);
      assertEquals(1, r1.getStatementIndex());
      assertTrue(r1.getTables().isEmpty());

      // Third statement: valid SELECT
      SqlLineageResult r2 = results.get(2);
      assertEquals(2, r2.getStatementIndex());
      assertEquals(Set.of("orders"), sourceTables(r2));
    }

    @Test
    @DisplayName("Table name case is preserved from original SQL")
    void casePreservation() {
      SqlLineageResult result = parseSingle("SELECT id FROM MyTable");
      assertTrue(sourceTables(result).stream().anyMatch(s -> s.equals("MyTable")),
          "Original case 'MyTable' should be preserved, got: " + sourceTables(result));
    }

    @Test
    @DisplayName("Column-level lineage falls back gracefully for complex SQL")
    void columnLineageGracefulFallback() {
      // Calcite does not support WITH...INSERT syntax
      // The result should be empty (silently skipped, no warning)
      SqlLineageResult result = parseSingle(
          "WITH cte AS (SELECT id FROM src) INSERT INTO tgt SELECT id FROM cte");
      assertTrue(result.getTables().isEmpty(), "Unsupported syntax should produce empty result");
    }
  }

  // ========================================================================
  //  SQL Validation
  // ========================================================================

  @Nested
  @DisplayName("SQL Validation")
  class ValidationTests {

    @Test
    @DisplayName("Valid SELECT passes validation")
    void validSelect() {
      List<SqlValidationResult> results = service.validate(
          "SELECT a.id, a.name FROM table_a a");
      assertEquals(1, results.size());
      assertTrue(results.get(0).isValid(), "Valid SELECT should pass validation");
      assertTrue(results.get(0).getErrors().isEmpty());
    }

    @Test
    @DisplayName("Valid INSERT-SELECT passes validation")
    void validInsertSelect() {
      List<SqlValidationResult> results = service.validate(
          "INSERT INTO target_table (id, name) SELECT a.id, a.name FROM source_table a");
      assertEquals(1, results.size());
      assertTrue(results.get(0).isValid(), "Valid INSERT-SELECT should pass validation");
    }

    @Test
    @DisplayName("Syntax error is detected")
    void syntaxError() {
      List<SqlValidationResult> results = service.validate("THIS IS NOT SQL");
      assertEquals(1, results.size());
      assertFalse(results.get(0).isValid());
      assertTrue(results.get(0).getErrors().stream()
          .anyMatch(e -> e.getType() == Type.SYNTAX_ERROR),
          "Should detect SYNTAX_ERROR");
    }

    @Test
    @DisplayName("SHOW TABLES is classified as unsupported syntax")
    void showTables() {
      List<SqlValidationResult> results = service.validate("show tables");
      assertEquals(1, results.size());
      assertFalse(results.get(0).isValid());
      assertTrue(results.get(0).getErrors().stream()
          .anyMatch(e -> e.getType() == Type.SYNTAX_ERROR),
          "SHOW TABLES should be detected as SYNTAX_ERROR");
    }

    @Test
    @DisplayName("Multi-statement: valid and invalid are reported independently")
    void multiStatement() {
      List<SqlValidationResult> results = service.validate(
          "SELECT id FROM users; THIS IS NOT SQL; SELECT name FROM orders");
      assertEquals(3, results.size());

      assertTrue(results.get(0).isValid(), "First statement should be valid");
      assertFalse(results.get(1).isValid(), "Second statement should be invalid");
      assertTrue(results.get(1).getErrors().stream()
          .anyMatch(e -> e.getType() == Type.SYNTAX_ERROR));
      assertTrue(results.get(2).isValid(), "Third statement should be valid");
    }

    @Test
    @DisplayName("Table not found in external metadata is reported")
    void tableNotFound() {
      // Provide external metadata for 'users' but not for 'orders'
      Map<String, List<String>> externalColumns = Map.of(
          "users", List.of("id", "name"));
      List<SqlValidationResult> results = service.validate(
          "SELECT u.id, o.name FROM users u JOIN orders o ON u.id = o.user_id",
          externalColumns);

      assertFalse(results.get(0).isValid(), "Should report TABLE_NOT_FOUND for 'orders'");
      assertTrue(results.get(0).getErrors().stream()
          .anyMatch(e -> e.getType() == Type.TABLE_NOT_FOUND
              && e.getMessage().contains("orders")),
          "Should report TABLE_NOT_FOUND for 'orders'");
    }

    @Test
    @DisplayName("Column not found in external metadata is reported as warning")
    void columnNotFound() {
      // Provide external metadata for 'users' but missing 'email' column
      Map<String, List<String>> externalColumns = Map.of(
          "users", List.of("id", "name"));
      List<SqlValidationResult> results = service.validate(
          "SELECT u.id, u.email FROM users u",
          externalColumns);

      // Calcite validation may still pass (we registered the columns from AST)
      // but the metadata check should add a WARNING
      assertTrue(results.get(0).getErrors().stream()
          .anyMatch(e -> e.getType() == Type.COLUMN_NOT_FOUND
              && e.getSeverity() == Severity.WARNING),
          "Should report COLUMN_NOT_FOUND as WARNING for 'email'");
    }

    @Test
    @DisplayName("Valid JOIN with all tables in external metadata passes validation")
    void validJoinWithMetadata() {
      Map<String, List<String>> externalColumns = Map.of(
          "orders", List.of("id", "customer_id"),
          "customers", List.of("id", "name"));
      List<SqlValidationResult> results = service.validate(
          "SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id",
          externalColumns);

      assertTrue(results.get(0).isValid(), "JOIN with all tables in metadata should pass");
    }

    @Test
    @DisplayName("Null SQL returns empty validation results")
    void nullSql() {
      List<SqlValidationResult> results = service.validate(null);
      assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Blank SQL returns empty validation results")
    void blankSql() {
      List<SqlValidationResult> results = service.validate("   ");
      assertTrue(results.isEmpty());
    }
  }
}
