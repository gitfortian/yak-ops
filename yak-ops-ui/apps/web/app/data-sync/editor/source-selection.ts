import type { DataSourceCatalogTable } from "@/service/datasource";
import type { EditorForm } from "./types";

/** Source selection resets dependent write intent atomically; it never creates physical options. */
function clearTargetWriteIdentity(form: EditorForm, usesChangelog: boolean): EditorForm["sink"] {
  return {
    ...form.sink,
    targetPrimaryKeys: [],
    writeMode: usesChangelog ? "UPSERT" : form.sink.writeMode,
  };
}

export function selectSourceSchema(
  form: EditorForm,
  schema: string,
  usesChangelog: boolean,
): EditorForm {
  return {
    ...form,
    source: { ...form.source, schema, table: "", sourceHasPrimaryKey: undefined, chunkKeyColumn: "" },
    sink: clearTargetWriteIdentity(form, usesChangelog),
  };
}

export function selectSourceTable(
  form: EditorForm,
  table: DataSourceCatalogTable,
  usesChangelog: boolean,
): EditorForm {
  return {
    ...form,
    source: {
      ...form.source,
      database: form.source.database || table.database || "",
      schema: form.source.schema || table.schema || "",
      table: table.name,
      sourceHasPrimaryKey: undefined,
      chunkKeyColumn: "",
    },
    sink: clearTargetWriteIdentity(form, usesChangelog),
  };
}

/** Real source metadata selects the write intent without a product-level keyless flag. */
export function setSourcePrimaryKeys(form: EditorForm, hasPrimaryKey: boolean): EditorForm {
  if (form.source.sourceHasPrimaryKey === hasPrimaryKey) return form;
  const retract = !hasPrimaryKey && form.sink.targetPrimaryKeys.length === 0;
  return {
    ...form,
    source: { ...form.source, sourceHasPrimaryKey: hasPrimaryKey },
    sink: {
      ...form.sink,
      writeMode: retract ? "RETRACT" : "UPSERT",
      schemaChangesEnabled: hasPrimaryKey ? form.sink.schemaChangesEnabled : false,
      allowDropColumns: hasPrimaryKey ? form.sink.allowDropColumns : false,
    },
  };
}
