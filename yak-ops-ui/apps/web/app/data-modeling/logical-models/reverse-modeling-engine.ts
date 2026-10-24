/**
 * Which datasource engine a reverse-modeling request can be served by.
 *
 * <p>Kept in its own module with no imports so the rule can be tested directly: the provider module
 * reaches the network, and a caller must not have to load it to answer "can this datasource be
 * reverse modeled at all".
 *
 * <p>The values are the canonical `dbType` a datasource stores, which is the descriptor type of the
 * registered plugin (`POSTGRE_SQL`, not `POSTGRESQL`). The aliases are accepted as well because the
 * plugin registry resolves them to the same descriptor, so a datasource created through an alias
 * must not become unsupported later.
 */
const JDBC_ENGINES = new Set(["MYSQL", "ORACLE", "POSTGRE_SQL", "POSTGRES", "POSTGRESQL"]);

/**
 * True when the engine exposes its metadata through the JDBC Catalog the provider reads.
 *
 * <p>A lake or compute engine is deliberately excluded: its metadata is not reachable over JDBC, so
 * offering the wizard would promise a draft it cannot produce.
 */
export function isJdbcReverseModelingEngine(dbType?: string | null): boolean {
  const normalized = dbType?.trim().toUpperCase();
  return Boolean(normalized && JDBC_ENGINES.has(normalized));
}
