import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";

const API = "/api/v1/data-modeling/dimension-modeling";

export const PUBLICATION_STATUSES = ["PENDING", "SUCCEEDED", "FAILED", "UNKNOWN"] as const;
export type PublicationStatus = (typeof PUBLICATION_STATUSES)[number];

export const PUBLICATION_STATUS_LABEL: Record<PublicationStatus, string> = {
  PENDING: "执行中",
  SUCCEEDED: "发布成功",
  FAILED: "发布失败",
  UNKNOWN: "结果未知",
};

export const PUBLICATION_STATUS_HINT: Record<PublicationStatus, string> = {
  PENDING: "记录已落库但未收到引擎结果；长时间停留时可对账",
  SUCCEEDED: "表已创建或已与模型一致",
  FAILED: "引擎拒绝或表与模型不一致；目标表未被修改",
  UNKNOWN: "连接中断，无法确认表是否已创建；请对账",
};

/** One explicit target. {@code schema} is null for engines that have no separate schema. */
export interface PublicationTarget {
  database: string;
  schema: string | null;
}

export interface PublicationPreview {
  database: string;
  schema: string | null;
  table: string;
  ddl: string;
}

/**
 * One publication record. {@code status} is null when the stored value is not known to this build:
 * the row stays readable and is never guessed at.
 */
export interface PublicationRecord {
  id: string;
  modelId: string | null;
  datasourceId: string;
  datasourceRevision: number;
  database: string;
  schema: string | null;
  table: string;
  status: PublicationStatus | null;
  ddl: string;
  error: string | null;
  createdTime: string;
  finishedTime: string | null;
  createBy: string;
}

export interface MetadataField {
  ordinal: number;
  name: string;
  typeName: string;
  jdbcType: number;
  size: number | null;
  scale: number | null;
  nullable: boolean;
  primaryKey: boolean;
  primaryKeyPosition: number | null;
  remarks: string | null;
}

export interface MetadataTable {
  database: string;
  schema: string | null;
  name: string;
  type: string | null;
  remarks: string | null;
  fields: MetadataField[];
}

export interface MetadataSnapshot {
  collectionId: string;
  publicationId: string;
  status: string | null;
  tableCount: number;
  fieldCount: number;
  error: string | null;
  createdTime: string;
  finishedTime: string | null;
  tables: MetadataTable[];
}

export type PublicationFailureKind = "target" | "unavailable" | "missing" | "unknown";

export interface PublicationFailure {
  kind: PublicationFailureKind;
  /** Server message when it carries one; the dialog shows it verbatim. */
  message?: string;
}

/** Keeps the session-expiry redirect and leaves every other failure to the caller. */
async function publicationRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

const message = (error: unknown) =>
  error instanceof BizError && error.message ? error.message : "操作失败，请稍后重试";

/**
 * Maps stable publication error codes to the part of the flow that failed.
 *
 * <p>Target validation comes back as a plain `PARAM_NOT_VALID` carrying the server's own sentence,
 * so it is passed through instead of being re-derived on the client.
 */
export function classifyPublicationFailure(error: unknown): PublicationFailure {
  if (!(error instanceof BizError)) return { kind: "unknown", message: "网络异常，请稍后重试" };
  switch (error.code) {
    case 1001:
      return { kind: "target", message: message(error) };
    case 10010:
      return { kind: "missing", message: message(error) };
    case 19509:
      return { kind: "unavailable", message: message(error) };
    case 19514:
      return { kind: "unknown", message: message(error) };
    default:
      return { kind: "unknown", message: message(error) };
  }
}

export function previewPublication(modelId: string, target: PublicationTarget) {
  return publicationRequest(
    HttpUtils.postData<PublicationPreview>(
      `${API}/models/${encodeURIComponent(modelId)}/publications/preview`,
      target,
      { skipErrorHandler: true },
    ),
  );
}

export function publishModel(modelId: string, target: PublicationTarget) {
  return publicationRequest(
    HttpUtils.postData<PublicationRecord>(
      `${API}/models/${encodeURIComponent(modelId)}/publications`,
      target,
      { skipErrorHandler: true },
    ),
  );
}

export const getPublications = (modelId: string, signal?: AbortSignal) =>
  publicationRequest(
    HttpUtils.getData<PublicationRecord[]>(
      `${API}/models/${encodeURIComponent(modelId)}/publications`,
      { signal, skipErrorHandler: true },
    ),
  );

export const getPublication = (publicationId: string, signal?: AbortSignal) =>
  publicationRequest(
    HttpUtils.getData<PublicationRecord>(
      `${API}/publications/${encodeURIComponent(publicationId)}`,
      {
        signal,
        skipErrorHandler: true,
      },
    ),
  );

export const reconcilePublication = (publicationId: string) =>
  publicationRequest(
    HttpUtils.postData<PublicationRecord>(
      `${API}/publications/${encodeURIComponent(publicationId)}/reconcile`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

export const getPublicationMetadata = (publicationId: string, signal?: AbortSignal) =>
  publicationRequest(
    HttpUtils.getData<MetadataSnapshot>(
      `${API}/publications/${encodeURIComponent(publicationId)}/metadata`,
      { signal, skipErrorHandler: true },
    ),
  );

export const refreshPublicationMetadata = (publicationId: string) =>
  publicationRequest(
    HttpUtils.postData<MetadataSnapshot>(
      `${API}/publications/${encodeURIComponent(publicationId)}/metadata/refresh`,
      undefined,
      { skipErrorHandler: true },
    ),
  );
