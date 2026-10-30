/**
 * The datasource engines this build can reach through a JDBC connection.
 *
 * <p>Doris is included because it speaks the MySQL wire protocol and publishes through the same
 * JDBC path. A lake or compute engine is deliberately excluded: neither reverse modeling nor
 * physical publishing can talk to it, so offering either entry would promise an outcome the
 * backend refuses.
 *
 * <p>The values are the canonical `dbType` a datasource stores, which is the descriptor type of the
 * registered plugin (`POSTGRE_SQL`, not `POSTGRESQL`). The aliases are accepted as well because the
 * plugin registry resolves them to the same descriptor, so a datasource created through an alias
 * must not become unsupported later.
 */
const JDBC_ENGINES = new Set(["MYSQL", "DORIS", "ORACLE", "POSTGRE_SQL", "POSTGRES", "POSTGRESQL"]);

/** True when the engine is a JDBC relational engine this build can connect to. */
export function isJdbcEngine(dbType?: string | null): boolean {
  const normalized = dbType?.trim().toUpperCase();
  return Boolean(normalized && JDBC_ENGINES.has(normalized));
}

/**
 * Whether the engine namespaces tables by a schema that is separate from the database.
 *
 * <p>MySQL and Doris pack schema and database together, so a target address there is the database
 * alone and an extra schema is rejected; Oracle and PostgreSQL both require one.
 */
export function requiresSchema(dbType?: string | null): boolean {
  const normalized = dbType?.trim().toUpperCase();
  return Boolean(normalized) && normalized !== "MYSQL" && normalized !== "DORIS";
}

/**
 * Engine-specific physical table properties the model editor renders.
 *
 * <p>Each entry lists the fields the storage-strategy form shows for that engine; engines without
 * an entry get no extra form at all. The keys are exactly what the backend accepts inside the
 * model's `physicalProperties` JSON object — anything else is rejected at save and publish time.
 */
export interface PhysicalPropertyField {
  key: string;
  label: string;
  kind: "number" | "select";
  options?: { value: string; label: string }[];
  required?: boolean;
  hint?: string;
}

export const ENGINE_PHYSICAL_PROPERTIES: Record<string, PhysicalPropertyField[]> = {
  DORIS: [
    {
      key: "dataModel",
      label: "数据模型",
      kind: "select",
      options: [
        { value: "UNIQUE", label: "UNIQUE（主键唯一）" },
        { value: "DUPLICATE", label: "DUPLICATE（明细）" },
      ],
      required: true,
      hint: "UNIQUE 需要模型定义主键字段",
    },
    { key: "bucketCount", label: "分桶数", kind: "number", required: true, hint: "1 - 1024" },
    { key: "replicaCount", label: "副本数", kind: "number", hint: "1 - 32，留空使用引擎默认" },
  ],
};

export const enginePhysicalProperties = (dbType?: string | null): PhysicalPropertyField[] =>
  ENGINE_PHYSICAL_PROPERTIES[dbType?.trim().toUpperCase() ?? ""] ?? [];
