import HttpUtils from '@/utils/HttpUtils';

import { DATA_QUALITY_EXECUTION_WORKSPACE_API } from './constants';
import type {
  CommonApiResponse,
  ExecutionLogView,
  ExecutionWorkspacePageView,
  ExecutionWorkspaceQuery,
  ExecutionWorkspaceView,
  RuleExecutionWorkspacePageView,
} from './types';

export const qualityExecutionWorkspaceApi = {
  page: (
    params: ExecutionWorkspaceQuery,
  ): Promise<CommonApiResponse<ExecutionWorkspacePageView>> =>
    HttpUtils.post<ExecutionWorkspacePageView>(
      `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/page`,
      params,
    ),
  rulePage: (
    params: ExecutionWorkspaceQuery,
  ): Promise<CommonApiResponse<RuleExecutionWorkspacePageView>> =>
    HttpUtils.post<RuleExecutionWorkspacePageView>(
      `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/rule/page`,
      params,
    ),
  detail: (
    executionNo: string,
  ): Promise<CommonApiResponse<ExecutionWorkspaceView>> =>
    HttpUtils.get<ExecutionWorkspaceView>(
      `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/${executionNo}`,
    ),
  logs: (
    executionNo: string,
  ): Promise<CommonApiResponse<ExecutionLogView>> =>
    HttpUtils.get<ExecutionLogView>(
      `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/${executionNo}/logs`,
    ),
};
