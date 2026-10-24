/**
 * Reverse-modeling seam agreed in YOIP-9 §"Frontend internal contract".
 *
 * <p>Declared, deliberately not implemented: no page imports this module and the menu has no
 * reverse-modeling entry. It exists so a later Paimon/Doris provider can plug in without reshaping
 * the callers, which is why {@code discover} and {@code describe} are defined as a pair.
 */

export interface ReverseModelingCandidate {
  datasourceId: string;
  schema?: string;
  table: string;
}

/** Same shape as a logical model field; engine dialects stay inside the provider. */
export interface ModelField {
  enCode: string;
  cnName: string;
  dataType: "STRING" | "BIGINT" | "DECIMAL" | "DATE" | "TIMESTAMP";
  lengthPrecision?: string;
  isPrimaryKey: boolean;
  isNotNull: boolean;
  description?: string;
}

export interface ReverseModelingProvider {
  readonly engine: string;
  discover(input: {
    datasourceId: string;
    pattern: string;
    exact: boolean;
  }): Promise<ReverseModelingCandidate[]>;
  describe(input: { datasourceId: string; schema?: string; table: string }): Promise<ModelField[]>;
}
