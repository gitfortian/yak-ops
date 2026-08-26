import HttpUtils from '@/utils/HttpUtils';

import {
  DATA_SERVICE_API_PREFIX,
  DATA_SOURCE_OPTION_API,
} from './constants';
import type {
  CreatedDataServiceApiKey,
  DataServiceApi,
  DataServiceApiKey,
  DataServiceApiKeyInput,
  DataServiceApiKeyUpdate,
  DataServiceAuthMode,
  DataServiceCallLog,
  DataServiceDocumentation,
  DataServiceDocumentationInput,
  DataServicePublishPayload,
  DataServiceQueryResult,
  DataServiceRuntimeConfig,
  DataServiceRuntimeStatus,
  DataServiceSourcePage,
  DataServiceSourceQuery,
  DataServiceUpdatePayload,
  DataSourceOption,
} from './types';

const queryString = (params: object) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : '';
};

export const listDataServices = (): Promise<DataServiceApi[]> =>
  HttpUtils.getData<DataServiceApi[]>(DATA_SERVICE_API_PREFIX);

export const listDataServiceSources = ({
  sourceType,
  pageNo = 1,
  pageSize = 100,
  keyword,
}: DataServiceSourceQuery): Promise<DataServiceSourcePage> =>
  HttpUtils.getData<DataServiceSourcePage>(
    `${DATA_SERVICE_API_PREFIX}/sources${queryString({
      sourceType,
      pageNo,
      pageSize,
      keyword,
    })}`,
  );

export const publishDataService = (
  payload: DataServicePublishPayload,
): Promise<DataServiceApi> =>
  HttpUtils.postData<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/publish`,
    payload,
  );

export const republishDataService = (id: number): Promise<DataServiceApi> =>
  HttpUtils.postData<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/${id}/republish`,
    {},
  );

export const updateDataService = (
  id: number,
  payload: DataServiceUpdatePayload,
): Promise<DataServiceApi> =>
  HttpUtils.putData<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/${id}`,
    payload,
  );

export const deleteDataService = async (id: number): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${DATA_SERVICE_API_PREFIX}/${id}`);
};

export const setDataServiceEnabled = (
  id: number,
  enabled: boolean,
): Promise<DataServiceApi> =>
  HttpUtils.putData<DataServiceApi>(
    `${DATA_SERVICE_API_PREFIX}/${id}/enabled${queryString({ enabled })}`,
    {},
  );

export const getDataServiceDocumentation = (
  id: number,
): Promise<DataServiceDocumentation> =>
  HttpUtils.getData<DataServiceDocumentation>(
    `${DATA_SERVICE_API_PREFIX}/${id}/documentation`,
  );

export const saveDataServiceDocumentation = (
  id: number,
  payload: DataServiceDocumentationInput,
): Promise<DataServiceDocumentation> =>
  HttpUtils.putData<DataServiceDocumentation>(
    `${DATA_SERVICE_API_PREFIX}/${id}/documentation`,
    payload,
  );

export const getDataServiceOpenApi = (
  id: number,
): Promise<Record<string, unknown>> =>
  HttpUtils.getData<Record<string, unknown>>(
    `${DATA_SERVICE_API_PREFIX}/${id}/openapi`,
  );

export const getDataServiceRuntime = (
  id: number,
): Promise<DataServiceRuntimeStatus> =>
  HttpUtils.getData<DataServiceRuntimeStatus>(
    `${DATA_SERVICE_API_PREFIX}/${id}/runtime`,
  );

export const updateDataServiceRuntime = (
  id: number,
  payload: DataServiceRuntimeConfig,
): Promise<DataServiceRuntimeStatus> =>
  HttpUtils.putData<DataServiceRuntimeStatus>(
    `${DATA_SERVICE_API_PREFIX}/${id}/runtime`,
    payload,
  );

export const setDataServiceAuthMode = (
  id: number,
  mode: DataServiceAuthMode,
): Promise<DataServiceAuthMode> =>
  HttpUtils.putData<DataServiceAuthMode>(
    `${DATA_SERVICE_API_PREFIX}/${id}/auth-mode${queryString({ mode })}`,
    {},
  );

export const listDataServiceKeys = (id: number): Promise<DataServiceApiKey[]> =>
  HttpUtils.getData<DataServiceApiKey[]>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys`,
  );

export const createDataServiceKey = (
  id: number,
  payload: DataServiceApiKeyInput,
): Promise<CreatedDataServiceApiKey> =>
  HttpUtils.postData<CreatedDataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys`,
    payload,
  );

export const updateDataServiceKey = (
  id: number,
  keyId: number,
  payload: DataServiceApiKeyUpdate,
): Promise<DataServiceApiKey> =>
  HttpUtils.putData<DataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}`,
    payload,
  );

export const setDataServiceKeyEnabled = (
  id: number,
  keyId: number,
  enabled: boolean,
): Promise<DataServiceApiKey> =>
  HttpUtils.putData<DataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}/enabled${queryString({
      enabled,
    })}`,
    {},
  );

export const rotateDataServiceKey = (
  id: number,
  keyId: number,
): Promise<CreatedDataServiceApiKey> =>
  HttpUtils.postData<CreatedDataServiceApiKey>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}/rotate`,
    {},
  );

export const deleteDataServiceKey = async (
  id: number,
  keyId: number,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DATA_SERVICE_API_PREFIX}/${id}/keys/${keyId}`,
  );
};

export const testDataService = (
  id: number,
  parameters: Record<string, string>,
): Promise<DataServiceQueryResult> =>
  HttpUtils.postData<DataServiceQueryResult>(
    `${DATA_SERVICE_API_PREFIX}/${id}/test`,
    parameters,
  );

export const listRecentDataServiceLogs = (): Promise<DataServiceCallLog[]> =>
  HttpUtils.getData<DataServiceCallLog[]>(
    `${DATA_SERVICE_API_PREFIX}/logs/recent`,
  );

export const listDataServiceDataSources = (): Promise<DataSourceOption[]> =>
  HttpUtils.getData<DataSourceOption[]>(DATA_SOURCE_OPTION_API);
