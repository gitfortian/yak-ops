package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class TableIdentityResolverTest {
  private final TableIdentityResolver resolver = new TableIdentityResolver();

  @Test
  void unqualifiedNameUsesExecutionContextAndDatasource() {
    var identity = resolve(table("orders", null, null, "orders"), "1", "sales", "public", "postgresql");
    assertEquals("table:1:sales.public.orders", identity.assetKey());
  }

  @Test
  void databasesSchemasAndDatasourcesCannotCollide() {
    var table = table("orders", null, null, "orders");
    assertNotEquals(resolve(table, "1", "sales", "public", "postgresql").assetKey(),
        resolve(table, "1", "marketing", "public", "postgresql").assetKey());
    assertNotEquals(resolve(table, "1", "sales", "public", "postgresql").assetKey(),
        resolve(table, "1", "sales", "private", "postgresql").assetKey());
    assertNotEquals(resolve(table, "1", "sales", "public", "postgresql").assetKey(),
        resolve(table, "2", "sales", "public", "postgresql").assetKey());
  }

  @Test
  void explicitTwoAndThreePartNamesAreNotOverwritten() {
    assertEquals("table:1:sales.archive.orders",
        resolve(table("archive.orders", null, "archive", "orders"),
            "1", "sales", "public", "postgresql").assetKey());
    assertEquals("table:1:warehouse.archive.orders",
        resolve(table("warehouse.archive.orders", "warehouse", "archive", "orders"),
            "1", "sales", "public", "postgresql").assetKey());
  }

  @Test
  void twoPartMeaningFollowsDialect() {
    var twoPart = table("archive.orders", null, "archive", "orders");
    assertEquals("table:1:sales.archive.orders",
        resolve(twoPart, "1", "sales", "public", "postgresql").assetKey());
    assertEquals("table:1:archive..orders",
        resolve(twoPart, "1", "sales", "public", "mysql").assetKey());
  }

  @Test
  void legacyContextIsIsolatedFromConfirmedAssets() {
    var identity = resolve(table("orders", null, null, "orders"), "1", null, null, null);
    assertEquals("table:unresolved:1:..orders", identity.assetKey());
  }

  @Test
  void canonicalizationIsStableForCaseAndSpecialCharacters() {
    var identity = resolve(table("\"Sales Schema\".\"Order-Items\"", null,
        "Sales Schema", "Order-Items"), "DS-A", null, null, "postgresql");
    assertEquals("table:ds-a:.sales schema.order-items", identity.assetKey());
  }

  private TableIdentityResolver.PhysicalTableIdentity resolve(
      SqlTableLineageParser.TableRef table, String ds, String database, String schema, String dialect) {
    return resolver.resolve(table, new TableIdentityResolver.ResolutionContext(
        ds, database, schema, TableIdentityResolver.SqlDialect.from(dialect)));
  }

  private static SqlTableLineageParser.TableRef table(
      String qualified, String database, String schema, String table) {
    return new SqlTableLineageParser.TableRef(
        qualified.toLowerCase(), qualified, database, schema, table);
  }
}
