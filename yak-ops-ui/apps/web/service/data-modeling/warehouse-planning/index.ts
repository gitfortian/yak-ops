import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";
import type {
  AssociatedModels,
  CategoryNode,
  EditorDraft,
  NodeInput,
  NodeKind,
  OwnerOption,
  PlanningNode,
  SavedNode,
} from "./types";

const API = "/api/v1/data-modeling/warehouse-planning";

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
const resources: Record<NodeKind, string> = {
  CATEGORY: "categories",
  DOMAIN: "domains",
  PROCESS: "processes",
};
export const getPlanningTree = (signal?: AbortSignal) =>
  HttpUtils.getData<CategoryNode[]>(`${API}/tree`, { signal });
export const getOwners = (signal?: AbortSignal) =>
  HttpUtils.getData<OwnerOption[]>(`${API}/owners`, { signal });
export const getAssociatedModels = (
  id: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  HttpUtils.getData<AssociatedModels>(
    `${API}/processes/${encodeURIComponent(id)}/logical-models?pageNo=${pageNo}&pageSize=${pageSize}`,
    { signal },
  );
export function checkCode(code: string, node?: PlanningNode, signal?: AbortSignal) {
  const query = new URLSearchParams({ code });
  if (node) {
    query.set("excludeKind", node.kind);
    query.set("excludeId", node.id);
  }
  return HttpUtils.getData<{ available: boolean }>(`${API}/codes/availability?${query}`, {
    signal,
  });
}
export function saveNode(draft: EditorDraft, input: NodeInput) {
  if (draft.mode === "edit") {
    return HttpUtils.putData<SavedNode>(
      `${API}/${resources[draft.node.kind]}/${encodeURIComponent(draft.node.id)}`,
      { ...input, revision: draft.node.revision },
    );
  }
  const path =
    draft.kind === "CATEGORY"
      ? "/categories"
      : draft.kind === "DOMAIN"
        ? `/categories/${encodeURIComponent(draft.parent.id)}/domains`
        : `/domains/${encodeURIComponent(draft.parent.id)}/processes`;
  return HttpUtils.postData<SavedNode>(API + path, input);
}
export const deleteNode = (node: PlanningNode) =>
  HttpUtils.deleteData<boolean>(
    `${API}/${resources[node.kind]}/${encodeURIComponent(node.id)}?revision=${node.revision}`,
  );
