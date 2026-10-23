import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";
import type {
  AssociatedModels,
  EditorDraft,
  NodeInput,
  NodeKind,
  OwnerOption,
  PlanningNode,
  PlanningTree,
  SavedNode,
} from "./types";

const API = "/api/v1/data-modeling/warehouse-planning";

/**
 * Keeps the session-expiry redirect that the shared handler would otherwise perform, and leaves
 * every other failure to the caller so one failure renders one message.
 */
async function planningRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

export function planningFailureField(error: unknown) {
  if (!(error instanceof BizError)) return null;
  switch (error.code) {
    case 19002:
      return "code";
    case 19005:
      return "ownerId";
    case 19004:
      return "revision";
    default:
      return null;
  }
}

/** Returns the server business message for planning failures, or a generic fallback. */
export function planningFailureMessage(error: unknown) {
  return error instanceof BizError && error.message ? error.message : "操作失败，请稍后重试";
}

/** Path segment owned by each level; data marts and subjects have dedicated resources. */
const resources: Record<NodeKind, string> = {
  CATEGORY: "categories",
  DOMAIN: "domains",
  PROCESS: "processes",
  DATASET: "datamarts",
  SUBJECT: "subjects",
};

/**
 * Projects the editable fields onto the contract of one level: subjects carry no owner, and only
 * application-layer nodes carry a business English name.
 */
function payload(kind: NodeKind, input: NodeInput) {
  const base = { name: input.name, code: input.code, description: input.description };
  if (kind === "SUBJECT") return { ...base, enName: input.enName };
  if (kind === "DATASET") return { ...base, enName: input.enName, ownerId: input.ownerId };
  return { ...base, ownerId: input.ownerId };
}

export const getPlanningTree = (signal?: AbortSignal) =>
  planningRequest(
    HttpUtils.getData<PlanningTree>(`${API}/tree`, { signal, skipErrorHandler: true }),
  );

export const getOwners = (signal?: AbortSignal) =>
  planningRequest(
    HttpUtils.getData<OwnerOption[]>(`${API}/owners`, { signal, skipErrorHandler: true }),
  );

export const getAssociatedModels = (
  id: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  planningRequest(
    HttpUtils.getData<AssociatedModels>(
      `${API}/processes/${encodeURIComponent(id)}/logical-models?pageNo=${pageNo}&pageSize=${pageSize}`,
      { signal, skipErrorHandler: true },
    ),
  );

export function checkCode(code: string, node?: PlanningNode, signal?: AbortSignal) {
  const query = new URLSearchParams({ code });
  if (node) {
    query.set("excludeKind", node.kind);
    query.set("excludeId", node.id);
  }
  return HttpUtils.getData<{ available: boolean }>(`${API}/codes/availability?${query}`, {
    signal,
    skipErrorHandler: true,
  });
}

export function saveNode(draft: EditorDraft, input: NodeInput) {
  if (draft.mode === "edit") {
    return planningRequest(
      HttpUtils.putData<SavedNode>(
        `${API}/${resources[draft.node.kind]}/${encodeURIComponent(draft.node.id)}`,
        { ...payload(draft.node.kind, input), revision: draft.node.revision },
        { skipErrorHandler: true },
      ),
    );
  }
  const body = payload(draft.kind, input);
  const create = (path: string) =>
    planningRequest(HttpUtils.postData<SavedNode>(API + path, body, { skipErrorHandler: true }));
  switch (draft.kind) {
    case "CATEGORY":
      return create("/categories");
    case "DOMAIN":
      return create(`/categories/${encodeURIComponent(draft.parent.id)}/domains`);
    case "PROCESS":
      return create(`/domains/${encodeURIComponent(draft.parent.id)}/processes`);
    case "DATASET":
      return create("/datamarts");
    case "SUBJECT":
      return create(`/datamarts/${encodeURIComponent(draft.parent.id)}/subjects`);
  }
}

export const deleteNode = (node: PlanningNode) =>
  planningRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/${resources[node.kind]}/${encodeURIComponent(node.id)}?revision=${node.revision}`,
      undefined,
      { skipErrorHandler: true },
    ),
  );
