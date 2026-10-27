import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "./request";
import type { Tag } from "./types";

const API = "/api/v1/data-map";

export const listTags = (signal?: AbortSignal) =>
  dataMapRequest(HttpUtils.getData<Tag[]>(`${API}/tags`, { signal, skipErrorHandler: true }));

export const createTag = (name: string) =>
  dataMapRequest(HttpUtils.postData<Tag>(`${API}/tags`, { name }, { skipErrorHandler: true }));

export const renameTag = (tagId: string, name: string) =>
  dataMapRequest(
    HttpUtils.putData<Tag>(
      `${API}/tags/${encodeURIComponent(tagId)}`,
      { name },
      {
        skipErrorHandler: true,
      },
    ),
  );

export const deleteTag = (tagId: string) =>
  dataMapRequest(
    HttpUtils.deleteData<boolean>(`${API}/tags/${encodeURIComponent(tagId)}`, undefined, {
      skipErrorHandler: true,
    }),
  );

/** Replaces the whole tag set of one asset; an empty list clears it. */
export const assignTags = (tableId: string, tagIds: string[]) =>
  dataMapRequest(
    HttpUtils.putData<boolean>(
      `${API}/tables/${encodeURIComponent(tableId)}/tags`,
      { tagIds },
      { skipErrorHandler: true },
    ),
  );
