import { queryAnalysisDataset } from './dataset-service';
import type {
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
} from './model';

const QUERY_RESULT_TTL_MS = 1_500;
const MAX_QUERY_ENTRIES = 64;

type AnalysisQueryLoader = (
  datasetId: string,
  payload: DatasetQueryPayload,
) => Promise<DatasetQueryResult>;

interface QueryEntry {
  promise: Promise<DatasetQueryResult>;
  settled: boolean;
  expiresAt: number;
}

const queryEntries = new Map<string, QueryEntry>();

export const analysisQueryCacheKey = (
  dataset: Pick<PublishedDataset, 'id' | 'currentVersionNo'>,
  payload: DatasetQueryPayload,
) => JSON.stringify({
  datasetId: dataset.id,
  datasetVersionNo: dataset.currentVersionNo ?? 0,
  payload,
});

const pruneQueryEntries = (now: number) => {
  queryEntries.forEach((entry, key) => {
    if (entry.settled && entry.expiresAt <= now) queryEntries.delete(key);
  });
  while (queryEntries.size >= MAX_QUERY_ENTRIES) {
    const oldest = queryEntries.keys().next().value as string | undefined;
    if (!oldest) break;
    queryEntries.delete(oldest);
  }
};

/**
 * Dashboard canvases frequently contain duplicated or closely related charts. Share an
 * identical in-flight Dataset request and keep a very small success cache so mounting,
 * resizing or switching Sheet state does not immediately issue the same query again.
 *
 * Dataset version is part of the key, so publishing a new Dataset version cannot reuse a
 * result from the previous version. Failures are never cached.
 */
export const queryAnalysisDatasetShared = (
  dataset: Pick<PublishedDataset, 'id' | 'currentVersionNo'>,
  payload: DatasetQueryPayload,
  loader: AnalysisQueryLoader = queryAnalysisDataset,
): Promise<DatasetQueryResult> => {
  const now = Date.now();
  const key = analysisQueryCacheKey(dataset, payload);
  const current = queryEntries.get(key);
  if (current && (!current.settled || current.expiresAt > now)) return current.promise;
  if (current) queryEntries.delete(key);

  pruneQueryEntries(now);
  const entry: QueryEntry = {
    promise: Promise.resolve(undefined as unknown as DatasetQueryResult),
    settled: false,
    expiresAt: Number.POSITIVE_INFINITY,
  };
  const promise = loader(dataset.id, payload)
    .then((result) => {
      entry.settled = true;
      entry.expiresAt = Date.now() + QUERY_RESULT_TTL_MS;
      return result;
    })
    .catch((error) => {
      if (queryEntries.get(key) === entry) queryEntries.delete(key);
      throw error;
    });
  entry.promise = promise;
  queryEntries.set(key, entry);
  return promise;
};

export const clearAnalysisQueryRuntime = () => queryEntries.clear();

export const analysisQueryRuntimeSize = () => queryEntries.size;
