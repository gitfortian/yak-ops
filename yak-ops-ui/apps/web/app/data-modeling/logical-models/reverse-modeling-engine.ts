/**
 * Which datasource engine a reverse-modeling request can be served by.
 *
 * <p>Kept in its own module so the rule can be tested directly: the provider module reaches the
 * network, and a caller must not have to load it to answer "can this datasource be reverse modeled
 * at all". The engine list itself is shared with physical publishing, which supports the same three
 * engines and must not drift from this one.
 */
import { isJdbcEngine } from "../shared/jdbc-engines.ts";

/**
 * True when the engine exposes its metadata through the JDBC Catalog the provider reads.
 *
 * <p>A lake or compute engine is deliberately excluded: its metadata is not reachable over JDBC, so
 * offering the wizard would promise a draft it cannot produce.
 */
export function isJdbcReverseModelingEngine(dbType?: string | null): boolean {
  return isJdbcEngine(dbType);
}
