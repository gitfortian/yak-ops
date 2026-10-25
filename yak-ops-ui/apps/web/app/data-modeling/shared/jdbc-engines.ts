/**
 * The datasource engines this build can reach through a JDBC connection.
 *
 * <p>A lake or compute engine is deliberately excluded: neither reverse modeling nor physical
 * publishing can talk to it, so offering either entry would promise an outcome the backend refuses.
 *
 * <p>The values are the canonical `dbType` a datasource stores, which is the descriptor type of the
 * registered plugin (`POSTGRE_SQL`, not `POSTGRESQL`). The aliases are accepted as well because the
 * plugin registry resolves them to the same descriptor, so a datasource created through an alias
 * must not become unsupported later.
 */
const JDBC_ENGINES = new Set(["MYSQL", "ORACLE", "POSTGRE_SQL", "POSTGRES", "POSTGRESQL"]);

/** True when the engine is a JDBC relational engine this build can connect to. */
export function isJdbcEngine(dbType?: string | null): boolean {
  const normalized = dbType?.trim().toUpperCase();
  return Boolean(normalized && JDBC_ENGINES.has(normalized));
}

/**
 * Whether the engine namespaces tables by a schema that is separate from the database.
 *
 * <p>MySQL packs schema and database together, so a target address there is the database alone and
 * an extra schema is rejected; Oracle and PostgreSQL both require one.
 */
export function requiresSchema(dbType?: string | null): boolean {
  const normalized = dbType?.trim().toUpperCase();
  return Boolean(normalized) && normalized !== "MYSQL";
}
