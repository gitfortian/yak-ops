/**
 * JDBC implementation of the reverse-modeling seam declared in YOIP-9 and delivered under YOIP-12.
 *
 * <p>The provider reads only metadata the datasource Catalog already exposes, so credentials stay
 * inside the datasource boundary and the caller sees identifiers alone. Type translation is
 * delegated to {@link describeFieldDrafts}, which is the same matrix the field import uses.
 */
import {
  listDataSourceColumns,
  listDataSourceTables,
  type DataSourceCatalogTable,
} from "@/service/datasource";
import type {
  ModelField,
  ReverseModelingCandidate,
  ReverseModelingProvider,
} from "@/service/data-modeling/dimension-modeling/reverse-modeling";
import { describeFieldDrafts, toModelField } from "./reverse-modeling-draft";

/** The catalog table listing has no cursor yet; this is the bound it accepts today. */
export const DISCOVER_LIMIT = 200;

export const jdbcReverseModelingProvider: ReverseModelingProvider = {
  engine: "jdbc",

  async discover({ datasourceId, pattern, exact }): Promise<ReverseModelingCandidate[]> {
    const keyword = pattern.trim();
    const tables: DataSourceCatalogTable[] = await listDataSourceTables(datasourceId, {
      keyword: keyword || undefined,
      limit: DISCOVER_LIMIT,
    });
    const wanted = keyword.toLowerCase();
    return tables
      .filter((table) => !exact || table.name.toLowerCase() === wanted)
      .map((table) => ({ datasourceId, schema: table.schema ?? undefined, table: table.name }));
  },

  async describe({ datasourceId, schema, table }): Promise<ModelField[]> {
    const columns = await listDataSourceColumns(datasourceId, {
      schema: schema || undefined,
      table,
    });
    return describeFieldDrafts(columns)
      .map(toModelField)
      .filter((field): field is ModelField => field !== null);
  },
};

/**
 * Resolves the provider that serves a datasource engine.
 *
 * <p>Every datasource type reachable today is a JDBC engine, so one provider serves all of them.
 * The engine is still an explicit input: a lake or compute engine routes here once it becomes a
 * datasource type, without changing the wizard or the seam.
 */
export function reverseModelingProvider(dbType?: string | null): ReverseModelingProvider | null {
  if (!dbType?.trim()) return null;
  return jdbcReverseModelingProvider;
}
