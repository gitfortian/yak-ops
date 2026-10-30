import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";

import type { TagBindingOperation, TagKey, TagValue } from "./types";

export type * from "./types";
const API = "/api/v1/tags";

// Tag failures are rendered beside their controls. Authentication keeps the transport's handling.
async function tagRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

/** Returns the server business message for a tag failure, or a generic fallback. */
export function tagFailureMessage(error: unknown, fallback = "操作失败，请稍后重试") {
  return error instanceof BizError && error.message ? error.message : fallback;
}

/** True when the caller is not a Workspace manager; dictionary writes are manager-only. */
export function isTagWriteForbidden(error: unknown) {
  return error instanceof BizError && error.code === 19906;
}

export const listKeys = (signal?: AbortSignal): Promise<TagKey[]> =>
  tagRequest(HttpUtils.getData<TagKey[]>(`${API}/keys`, { signal, skipErrorHandler: true }));

export const createKey = (name: string, values: string[]): Promise<TagKey> =>
  tagRequest(
    HttpUtils.postData<TagKey>(`${API}/keys`, { name, values }, { skipErrorHandler: true }),
  );

export const renameKey = (keyId: string, name: string): Promise<TagKey> =>
  tagRequest(
    HttpUtils.putData<TagKey>(
      `${API}/keys/${encodeURIComponent(keyId)}`,
      { name },
      { skipErrorHandler: true },
    ),
  );

export const deleteKey = (keyId: string): Promise<boolean> =>
  tagRequest(
    HttpUtils.deleteData<boolean>(`${API}/keys/${encodeURIComponent(keyId)}`, undefined, {
      skipErrorHandler: true,
    }),
  );

export const addValue = (keyId: string, name: string): Promise<TagValue> =>
  tagRequest(
    HttpUtils.postData<TagValue>(
      `${API}/keys/${encodeURIComponent(keyId)}/values`,
      { name },
      { skipErrorHandler: true },
    ),
  );

export const renameValue = (valueId: string, name: string): Promise<TagValue> =>
  tagRequest(
    HttpUtils.putData<TagValue>(
      `${API}/values/${encodeURIComponent(valueId)}`,
      { name },
      { skipErrorHandler: true },
    ),
  );

export const deleteValue = (valueId: string): Promise<boolean> =>
  tagRequest(
    HttpUtils.deleteData<boolean>(`${API}/values/${encodeURIComponent(valueId)}`, undefined, {
      skipErrorHandler: true,
    }),
  );

/** Replaces the whole tag value set of one asset; an empty list clears it. */
export const replaceTableTags = (tableId: string, tagValueIds: string[]): Promise<boolean> =>
  tagRequest(
    HttpUtils.putData<boolean>(
      `${API}/tables/${encodeURIComponent(tableId)}`,
      { tagValueIds },
      { skipErrorHandler: true },
    ),
  );

/** Applies one ADD or REMOVE command over many assets in a single transaction. */
export const applyBindingBatch = (
  tableIds: string[],
  tagValueIds: string[],
  operation: TagBindingOperation,
): Promise<number> =>
  tagRequest(
    HttpUtils.postData<number>(
      `${API}/bindings/batch`,
      { tableIds, tagValueIds, operation },
      { skipErrorHandler: true },
    ),
  );
