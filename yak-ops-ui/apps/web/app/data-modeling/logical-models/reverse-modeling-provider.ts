/**
 * JDBC implementation of the reverse-modeling seam declared in YOIP-9 and delivered under YOIP-12.
 *
 * <p>The provider reads only metadata the datasource Catalog already exposes, so credentials stay
 * inside the datasource boundary and the caller sees identifiers alone. Type translation is
 * delegated to {@link describeFieldDrafts}, which is the same matrix the field import uses.
 *
 * <p>Two projections share that translation: {@link JdbcReverseModelingProvider.describeDrafts}
 * keeps every physical column so the wizard can show a reason instead of losing a field, while
 * {@link ReverseModelingProvider.describe} returns only complete fields because the agreed
 * {@code ModelField} shape has no way to express a half-mapped column.
 */
import {
  listDataSourceColumns,
  listDataSourceTables,
  type DataSourceCatalogColumn,
  type DataSourceCatalogTable,
} from "@/service/datasource";
import type {
  ModelField,
  ReverseModelingCandidate,
  ReverseModelingProvider,
} from "@/service/data-modeling/dimension-modeling/reverse-modeling";
import type { FieldDraft } from "./fields";
import { qualifiedTableName } from "./field-mapping";
import { isJdbcReverseModelingEngine } from "./reverse-modeling-engine";
import { describeFieldDrafts, toModelField } from "./reverse-modeling-draft";

/** The catalog table listing has no cursor yet; this is the bound it accepts today. */
export const DISCOVER_LIMIT = 200;

// One reference for both projections: a method calling `this` would break as soon as a caller
// destructures the provider, which is exactly how a React component would consume it.
const describeDrafts = (columns: readonly DataSourceCatalogColumn[], sourceTable?: string) =>
  describeFieldDrafts(columns, sourceTable ?? null);

export interface JdbcReverseModelingProvider extends ReverseModelingProvider {
  /**
   * Physical columns to editable drafts, without dropping the rows the type matrix cannot map.
   *
   * <p>This is the projection the wizard renders: a dropped column would silently produce a model
   * that does not match the table it was reversed from. Passing the table name keeps that origin on
   * the draft so a later standard binding can warn before the code leaves the physical column.
   */
  describeDrafts(columns: readonly DataSourceCatalogColumn[], sourceTable?: string): FieldDraft[];
}

export const jdbcReverseModelingProvider: JdbcReverseModelingProvider = {
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
    return describeDrafts(columns, qualifiedTableName({ schema, name: table }))
      .map(toModelField)
      .filter((field): field is ModelField => field !== null);
  },

  describeDrafts(columns, sourceTable) {
    return describeDrafts(columns, sourceTable);
  },
};

/**
 * Resolves the provider that serves a datasource engine, or null when none can.
 *
 * <p>Every datasource type reachable today is a JDBC engine, so one provider serves all of them and
 * the engine check lives in {@link isJdbcReverseModelingEngine}. A lake or compute engine is refused
 * here rather than failing later inside the Catalog call, and gains a branch here when it becomes a
 * datasource type — the wizard and the seam stay unchanged.
 */
export function reverseModelingProvider(
  dbType?: string | null,
): JdbcReverseModelingProvider | null {
  return isJdbcReverseModelingEngine(dbType) ? jdbcReverseModelingProvider : null;
}
