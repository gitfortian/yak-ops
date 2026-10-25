import { useEffect, type MutableRefObject } from "react";
import { writeTaskDraft } from "@/service/data-sync/draft";
import type { DataSyncDraft, DataSyncTaskRecord, DataSyncTableMapping } from "@/service/data-sync";
import type { EditorForm } from "./types";
import { buildEditorPayload } from "./definition";
export function useEditorDraft(
  id: string | undefined,
  draftKey: string,
  draft: DataSyncDraft,
  form: EditorForm,
  batch: boolean,
  mappings: DataSyncTableMapping[],
  useIncremental: boolean,
  savedTask: MutableRefObject<DataSyncTaskRecord | undefined>,
) {
  useEffect(() => {
    if (id || savedTask.current) return;
    writeTaskDraft(draftKey, {
      sourceType: draft.sourceType,
      targetType: draft.targetType,
      solutionType: draft.solutionType,
      steps: draft.steps,
      name: form.name,
      form: {
        ...buildEditorPayload(
          form,
          draft,
          batch,
          mappings,
          draft.solutionType.endsWith("_REALTIME") && draft.steps.includes("INCREMENTAL"),
        ),
        incremental: !batch && useIncremental ? form.source.incremental : undefined,
      },
    });
  }, [
    id,
    draftKey,
    draft.sourceType,
    draft.targetType,
    draft.solutionType,
    draft.steps,
    form,
    batch,
    mappings,
  ]);
}
