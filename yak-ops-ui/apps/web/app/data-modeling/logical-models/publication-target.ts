/**
 * Client-side mirror of the target-address rules the publish endpoint enforces.
 *
 * <p>The publish dialog asks for an address before it calls the server, so the two must agree on
 * which engines take a schema and what a missing field means. Keeping the rules in a zero-import
 * module lets them be tested directly instead of only through a rendered dialog.
 */
import { isJdbcEngine, requiresSchema } from "../shared/jdbc-engines.ts";

/** One rejected target, keyed to the control the user must correct. */
export interface TargetProblem {
  field: "engine" | "database" | "schema";
  message: string;
}

export interface PublicationTargetInput {
  dbType?: string | null;
  database: string;
  schema: string | null;
}

/**
 * Validates one explicit target.
 *
 * <p>The address is never inferred: an empty database, a missing schema on an engine that needs one,
 * and a surplus schema on MySQL are all refused here rather than discovered after a round trip.
 */
export function validatePublicationTarget(input: PublicationTargetInput): TargetProblem | null {
  if (!isJdbcEngine(input.dbType)) {
    return {
      field: "engine",
      message: "该数据源类型不支持物理发布，仅支持 MySQL、Oracle、PostgreSQL",
    };
  }
  const database = input.database.trim();
  const schema = input.schema?.trim() ?? "";
  if (!database) return { field: "database", message: "请选择目标数据库" };
  if (requiresSchema(input.dbType) && !schema)
    return { field: "schema", message: "该引擎要求显式选择目标 Schema" };
  if (!requiresSchema(input.dbType) && schema)
    return { field: "schema", message: "该引擎没有独立 Schema，请勿选择目标 Schema" };
  return null;
}

/** Renders the resolved address as it will appear in the confirmation sentence. */
export function describeAddress(
  database: string,
  schema: string | null,
  dbType?: string | null,
): string {
  const parts = [database.trim()];
  const trimmed = schema?.trim() ?? "";
  if (requiresSchema(dbType) && trimmed) parts.push(trimmed);
  return parts.filter(Boolean).join(".");
}

/** Submits the target in the shape the endpoint expects; MySQL sends no schema at all. */
export function toTargetPayload(input: PublicationTargetInput) {
  const schema = input.schema?.trim() ?? "";
  return {
    database: input.database.trim(),
    schema: requiresSchema(input.dbType) && schema ? schema : null,
  };
}
