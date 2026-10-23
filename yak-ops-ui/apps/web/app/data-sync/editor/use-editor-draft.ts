import { useEffect, type MutableRefObject } from "react";
import { writeTaskDraft } from "@/service/data-sync/draft";
import type { DataSyncDraft, DataSyncTaskRecord, DataSyncTableMapping } from "@/service/data-sync";
import type { EditorForm } from "./types";
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
        ...form,
        solutionType: draft.solutionType,
        steps: draft.steps,
        tableMappings: batch ? mappings : undefined,
        incremental: !batch && useIncremental ? form.incremental : undefined,
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
