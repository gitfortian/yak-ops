import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";

const API = "/api/v1/data-modeling";

// Authentication keeps the transport's handling; everything else surfaces to the caller.
async function semanticRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

export interface RelationKey {
  ordinal: number;
  fromFieldCode: string;
  toFieldCode: string;
  fromFieldName: string | null;
  toFieldName: string | null;
  resolved: boolean;
  standardBound: boolean;
}

export interface ModelRelation {
  id: string;
  name: string;
  fromModelId: string;
  fromModelName: string;
  fromModelType: string;
  toModelId: string;
  toModelName: string;
  toModelType: string;
  joinType: string;
  description: string | null;
  keys: RelationKey[];
  status: "READY" | "KEY_UNSTANDARDIZED" | "FIELD_REMOVED";
  revision: number;
}

export interface RelationKeyPair {
  fromFieldCode: string;
  toFieldCode: string;
}

export interface RelationProposal {
  fromFieldCode: string;
  toFieldCode: string;
  fromStandardEnCode: string | null;
  toStandardEnCode: string | null;
  sameStandard: boolean;
  typesCompatible: boolean;
  selectable: boolean;
  reason: string | null;
}

export interface SemanticExportCheck {
  level: "ERROR" | "WARN" | "OK";
  code: string;
  message: string;
}

export interface SemanticExport {
  checks: SemanticExportCheck[];
  yaml: string;
}

export function listRelations(modelId: string, signal?: AbortSignal) {
  return semanticRequest(
    HttpUtils.getData<ModelRelation[]>(`${API}/models/${encodeURIComponent(modelId)}/relations`, {
      signal,
    }),
  );
}

export function createRelation(
  modelId: string,
  input: {
    targetModelId: string;
    name?: string;
    joinType?: string;
    description?: string;
    keys: RelationKeyPair[];
  },
) {
  return semanticRequest(
    HttpUtils.postData<ModelRelation>(
      `${API}/models/${encodeURIComponent(modelId)}/relations`,
      input,
    ),
  );
}

export function updateRelation(
  relationId: string,
  input: {
    name: string;
    joinType: string;
    description?: string;
    keys: RelationKeyPair[];
    revision: number;
  },
) {
  return semanticRequest(
    HttpUtils.putData<ModelRelation>(`${API}/relations/${encodeURIComponent(relationId)}`, input),
  );
}

export function deleteRelation(relationId: string, revision: number) {
  return semanticRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/relations/${encodeURIComponent(relationId)}?revision=${revision}`,
    ),
  );
}

export function proposeRelationKeys(modelId: string, targetModelId: string, signal?: AbortSignal) {
  return semanticRequest(
    HttpUtils.postData<RelationProposal[]>(
      `${API}/models/${encodeURIComponent(modelId)}/relations/proposals`,
      { targetModelId },
      { signal },
    ),
  );
}

export function updateGrain(modelId: string, grainNote: string | null, revision: number) {
  return semanticRequest(
    HttpUtils.putData<boolean>(`${API}/models/${encodeURIComponent(modelId)}/grain`, {
      grainNote,
      revision,
    }),
  );
}

export function listFieldSynonyms(modelId: string, fieldCode: string, signal?: AbortSignal) {
  return semanticRequest(
    HttpUtils.getData<string[]>(
      `${API}/models/${encodeURIComponent(modelId)}/fields/${encodeURIComponent(fieldCode)}/synonyms`,
      { signal },
    ),
  );
}

export function replaceFieldSynonyms(modelId: string, fieldCode: string, terms: string[]) {
  return semanticRequest(
    HttpUtils.putData<string[]>(
      `${API}/models/${encodeURIComponent(modelId)}/fields/${encodeURIComponent(fieldCode)}/synonyms`,
      { terms },
    ),
  );
}

export function exportSemanticModel(modelId: string, signal?: AbortSignal) {
  return semanticRequest(
    HttpUtils.postData<SemanticExport>(
      `${API}/models/${encodeURIComponent(modelId)}/semantic-export`,
      {},
      { signal },
    ),
  );
}
