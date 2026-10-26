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
    source: { ...form.source, schema, table: "", keylessEnabled: false, chunkKeyColumn: "" },
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
      keylessEnabled: false,
      chunkKeyColumn: "",
    },
    sink: clearTargetWriteIdentity(form, usesChangelog),
  };
}

export function setKeylessSource(form: EditorForm, enabled: boolean): EditorForm {
  return {
    ...form,
    source: {
      ...form.source,
      keylessEnabled: enabled,
      chunkKeyColumn: enabled ? form.source.chunkKeyColumn : "",
    },
    sink: {
      ...form.sink,
      targetPrimaryKeys: [],
      writeMode: enabled ? "RETRACT" : "UPSERT",
      schemaChangesEnabled: enabled ? false : form.sink.schemaChangesEnabled,
      allowDropColumns: enabled ? false : form.sink.allowDropColumns,
    },
  };
}
