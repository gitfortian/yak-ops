import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "@/service/data-map/request";

import type { Paging } from "./types";

const API = "/api/v1/data-map/comments";

export interface CommentVO {
  id: string;
  parentId: string | null;
  body: string;
  authorId: string;
  authorName: string;
  replyCount: number;
  createTime: string;
  replies: CommentVO[];
}

export const pageComments = (
  targetId: string,
  pageNo: number,
  pageSize: number,
  signal?: AbortSignal,
) =>
  dataMapRequest(
    HttpUtils.getData<Paging<CommentVO>>(
      `${API}?targetType=DATA_MAP_TABLE&targetId=${encodeURIComponent(targetId)}&pageNo=${pageNo}&pageSize=${pageSize}`,
      { signal, skipErrorHandler: true },
    ),
  );

export const unreadCommentCount = (targetId: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<number>(
      `${API}/unread-count?targetType=DATA_MAP_TABLE&targetId=${encodeURIComponent(targetId)}`,
      { signal, skipErrorHandler: true },
    ),
  );

export const createComment = (targetId: string, body: string, parentId?: string) =>
  dataMapRequest(
    HttpUtils.postData<CommentVO>(
      API,
      { targetType: "DATA_MAP_TABLE", targetId, body, parentId: parentId ?? null },
      { skipErrorHandler: true },
    ),
  );

export const deleteComment = (commentId: string) =>
  dataMapRequest(
    HttpUtils.deleteData<boolean>(`${API}/${encodeURIComponent(commentId)}`, undefined, {
      skipErrorHandler: true,
    }),
  );

export const markCommentsRead = (targetId: string) =>
  dataMapRequest(
    HttpUtils.postData<boolean>(`${API}/read?targetId=${encodeURIComponent(targetId)}`, undefined, {
      skipErrorHandler: true,
    }),
  );
