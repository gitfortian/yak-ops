/**
 * Resolves which database a catalog picker opens on.
 *
 * <p>A datasource registers the database its JDBC URL points at, and that is what "数仓ods" means to
 * the person who named it. The candidate list is the whole instance, so its first entry is usually a
 * different database and a picker that defaults to it reads and writes somewhere else while the user
 * believes they are in the registered one. A registered name the instance does not offer is reported
 * instead of guessed around, because the very next action on that screen may write.
 */
export interface CatalogDatabaseDefault {
  database: string;
  /** Why nothing was chosen; empty when the registered database matched a candidate. */
  hint: string;
}

const NO_DATABASE_HINT = "该数据源未登记默认库，请手动选择目标数据库";

export function resolveDefaultDatabase(
  registered: string | null | undefined,
  candidates: string[],
): CatalogDatabaseDefault {
  const name = registered?.trim() ?? "";
  if (!name) return { database: "", hint: NO_DATABASE_HINT };
  if (candidates.includes(name)) return { database: name, hint: "" };
  return {
    database: "",
    hint: `数据源登记库「${name}」不在该实例的数据库列表中，请手动选择目标数据库`,
  };
}
