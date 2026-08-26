import HttpUtils from '@/utils/HttpUtils';

import {
  DATA_QUALITY_EXECUTION_API,
  DATA_QUALITY_MONITOR_API,
  DATA_QUALITY_TABLE_ASSET_API,
  DATA_QUALITY_TEMPLATE_API,
} from './constants';
import type {
  CommonApiResponse,
  CopyCustomTemplatePayload,
  ExecutionPageView,
  ExecutionView,
  MonitorPageView,
  MonitorReportView,
  MonitorSettingsView,
  MonitorView,
  MonitorWorkspaceView,
  OperationLogPageView,
  RegisterTablesPayload,
  RegisterTablesView,
  RunView,
  SaveCustomTemplatePayload,
  SaveMonitorPayload,
  SaveTemplateFolderPayload,
  TableAssetPageView,
  TableCandidatePageView,
  TableMonitorSummary,
  TemplateFolderView,
  TemplateListView,
  TemplateView,
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

export const qualityTemplateApi = {
  list: (
    params: Record<string, unknown> = {},
  ): Promise<CommonApiResponse<TemplateListView>> =>
    HttpUtils.get<TemplateListView>(
      `${DATA_QUALITY_TEMPLATE_API}${queryString(params)}`,
    ),
  customList: (
    params: Record<string, unknown> = {},
  ): Promise<CommonApiResponse<TemplateListView>> =>
    HttpUtils.get<TemplateListView>(
      `${DATA_QUALITY_TEMPLATE_API}/custom${queryString(params)}`,
    ),
  detail: (id: number | string): Promise<CommonApiResponse<TemplateView>> =>
    HttpUtils.get<TemplateView>(`${DATA_QUALITY_TEMPLATE_API}/${id}`),
  folders: (): Promise<CommonApiResponse<TemplateFolderView[]>> =>
    HttpUtils.get<TemplateFolderView[]>(`${DATA_QUALITY_TEMPLATE_API}/folder`),
  createFolder: (
    payload: SaveTemplateFolderPayload,
  ): Promise<CommonApiResponse<TemplateFolderView>> =>
    HttpUtils.post<TemplateFolderView>(
      `${DATA_QUALITY_TEMPLATE_API}/folder`,
      payload,
    ),
  updateFolder: (
    id: number | string,
    payload: SaveTemplateFolderPayload,
  ): Promise<CommonApiResponse<TemplateFolderView>> =>
    HttpUtils.put<TemplateFolderView>(
      `${DATA_QUALITY_TEMPLATE_API}/folder/${id}`,
      payload,
    ),
  removeFolder: (id: number | string): Promise<CommonApiResponse<boolean>> =>
    HttpUtils.delete<boolean>(`${DATA_QUALITY_TEMPLATE_API}/folder/${id}`),
  createCustom: (
    payload: SaveCustomTemplatePayload,
  ): Promise<CommonApiResponse<TemplateView>> =>
    HttpUtils.post<TemplateView>(
      `${DATA_QUALITY_TEMPLATE_API}/custom`,
      payload,
    ),
  updateCustom: (
    id: number | string,
    payload: SaveCustomTemplatePayload,
  ): Promise<CommonApiResponse<TemplateView>> =>
    HttpUtils.put<TemplateView>(
      `${DATA_QUALITY_TEMPLATE_API}/custom/${id}`,
      payload,
    ),
  copyCustom: (
    id: number | string,
    payload: CopyCustomTemplatePayload,
  ): Promise<CommonApiResponse<TemplateView>> =>
    HttpUtils.post<TemplateView>(
      `${DATA_QUALITY_TEMPLATE_API}/custom/${id}/copy`,
      payload,
    ),
  removeCustom: (id: number | string): Promise<CommonApiResponse<boolean>> =>
    HttpUtils.delete<boolean>(`${DATA_QUALITY_TEMPLATE_API}/custom/${id}`),
};

export const qualityTableAssetApi = {
  page: (
    params: Record<string, unknown>,
  ): Promise<CommonApiResponse<TableAssetPageView>> =>
    HttpUtils.post<TableAssetPageView>(
      `${DATA_QUALITY_TABLE_ASSET_API}/page`,
      params,
    ),
  candidates: (
    params: Record<string, unknown>,
  ): Promise<CommonApiResponse<TableCandidatePageView>> =>
    HttpUtils.get<TableCandidatePageView>(
      `${DATA_QUALITY_TABLE_ASSET_API}/candidates${queryString(params)}`,
    ),
  register: (
    payload: RegisterTablesPayload,
  ): Promise<CommonApiResponse<RegisterTablesView>> =>
    HttpUtils.post<RegisterTablesView>(
      `${DATA_QUALITY_TABLE_ASSET_API}/register`,
      payload,
    ),
  remove: (id: number | string): Promise<CommonApiResponse<boolean>> =>
    HttpUtils.delete<boolean>(`${DATA_QUALITY_TABLE_ASSET_API}/${id}`),
};

export const qualityMonitorApi = {
  page: (
    params: Record<string, unknown>,
  ): Promise<CommonApiResponse<MonitorPageView>> =>
    HttpUtils.post<MonitorPageView>(
      `${DATA_QUALITY_MONITOR_API}/page`,
      params,
    ),
  tableSummary: (params: {
    dataSourceId: number;
    databaseName?: string;
    schemaName?: string;
  }): Promise<CommonApiResponse<TableMonitorSummary[]>> =>
    HttpUtils.get<TableMonitorSummary[]>(
      `${DATA_QUALITY_MONITOR_API}/table-summary${queryString(params)}`,
    ),
  detail: (id: number | string): Promise<CommonApiResponse<MonitorView>> =>
    HttpUtils.get<MonitorView>(`${DATA_QUALITY_MONITOR_API}/${id}`),
  settings: (
    id: number | string,
  ): Promise<CommonApiResponse<MonitorSettingsView>> =>
    HttpUtils.get<MonitorSettingsView>(
      `${DATA_QUALITY_MONITOR_API}/${id}/settings`,
    ),
  create: (
    payload: SaveMonitorPayload,
  ): Promise<CommonApiResponse<MonitorView>> =>
    HttpUtils.post<MonitorView>(DATA_QUALITY_MONITOR_API, payload),
  update: (
    id: number | string,
    payload: SaveMonitorPayload,
  ): Promise<CommonApiResponse<MonitorView>> =>
    HttpUtils.put<MonitorView>(`${DATA_QUALITY_MONITOR_API}/${id}`, payload),
  remove: (id: number | string): Promise<CommonApiResponse<boolean>> =>
    HttpUtils.delete<boolean>(`${DATA_QUALITY_MONITOR_API}/${id}`),
  run: (id: number | string): Promise<CommonApiResponse<RunView>> =>
    HttpUtils.post<RunView>(`${DATA_QUALITY_MONITOR_API}/${id}/run`, {}),
};

export const qualityWorkspaceApi = {
  workspace: (
    id: number | string,
  ): Promise<CommonApiResponse<MonitorWorkspaceView>> =>
    HttpUtils.get<MonitorWorkspaceView>(
      `${DATA_QUALITY_MONITOR_API}/${id}/workspace`,
    ),
  report: (
    id: number | string,
    params: { date?: string } = {},
  ): Promise<CommonApiResponse<MonitorReportView>> =>
    HttpUtils.get<MonitorReportView>(
      `${DATA_QUALITY_MONITOR_API}/${id}/report${queryString(params)}`,
    ),
  operationLogs: (
    id: number | string,
    params: { current?: number; pageSize?: number } = {},
  ): Promise<CommonApiResponse<OperationLogPageView>> =>
    HttpUtils.get<OperationLogPageView>(
      `${DATA_QUALITY_MONITOR_API}/${id}/operation-log${queryString(params)}`,
    ),
};

export const qualityExecutionApi = {
  page: (
    params: Record<string, unknown>,
  ): Promise<CommonApiResponse<ExecutionPageView>> =>
    HttpUtils.post<ExecutionPageView>(
      `${DATA_QUALITY_EXECUTION_API}/page`,
      params,
    ),
  detail: (executionNo: string): Promise<CommonApiResponse<ExecutionView>> =>
    HttpUtils.get<ExecutionView>(
      `${DATA_QUALITY_EXECUTION_API}/${executionNo}`,
    ),
};
