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
  FAILED: "引擎拒绝或执行中断；详见执行日志",
  UNKNOWN: "连接中断，无法确认引擎结果；请对账",
};

/** One publish request: an environment plus the data-loss acknowledgement. */
export interface PublicationTarget {
  environment: string;
  acknowledgeDataLoss?: boolean;
}

export const PUBLICATION_OPERATIONS = ["CREATE", "ALTER", "NONE"] as const;
export type PublicationOperation = (typeof PUBLICATION_OPERATIONS)[number];

export const PUBLICATION_OPERATION_LABEL: Record<PublicationOperation, string> = {
  CREATE: "新建表",
  ALTER: "变更表",
  NONE: "无需发布",
};

/** One schema difference shown in the publish dialog. */
export interface SchemaChange {
  kind: "ADD" | "CHANGE" | "DROP";
  column: string;
  fromType: string | null;
  toType: string | null;
  detail: string;
}

export interface PublicationPreview {
  database: string;
  schema: string | null;
  table: string;
  operation: PublicationOperation | null;
  changes: SchemaChange[];
  ddl: string | null;
  requiresDataLossAck: boolean;
}

/**
 * One publication record. {@code status} is null when the stored value is not known to this build:
 * the row stays readable and is never guessed at. {@code environment}/{@code operation} are null on
 * records written before those columns existed.
 */
export interface PublicationRecord {
  id: string;
  modelId: string | null;
  datasourceId: string;
  datasourceRevision: number;
  environment: "DEVELOP" | "PROD" | null;
  database: string;
  schema: string | null;
  table: string;
  operation: PublicationOperation | null;
  status: PublicationStatus | null;
  changes: SchemaChange[];
  ddl: string;
  durationMs: number | null;
  executionLog: string | null;
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
 * <p>19603 is the dangerous-operation guard (DROP COLUMN without acknowledgement) and 19604 is an
 * unbound layer target; both come back with the server's own sentence and are passed through.
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
    case 19601:
    case 19602:
    case 19603:
    case 19604:
      return { kind: "target", message: message(error) };
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
