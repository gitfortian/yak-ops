import HttpUtils from '@/utils/HttpUtils';

import { DATA_QUALITY_EXECUTION_WORKSPACE_API } from './constants';
import type {
  ExecutionLogView,
  ExecutionWorkspacePageView,
  ExecutionWorkspaceQuery,
  ExecutionWorkspaceView,
  RuleExecutionWorkspacePageView,
} from './types';

const executionPath = (executionNo: string) =>
  `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/${encodeURIComponent(executionNo)}`;

export const listQualityExecutionWorkspace = (
  query: ExecutionWorkspaceQuery,
): Promise<ExecutionWorkspacePageView> =>
  HttpUtils.postData<ExecutionWorkspacePageView>(
    `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/page`,
    query,
  );

export const listQualityRuleExecutionWorkspace = (
  query: ExecutionWorkspaceQuery,
): Promise<RuleExecutionWorkspacePageView> =>
  HttpUtils.postData<RuleExecutionWorkspacePageView>(
    `${DATA_QUALITY_EXECUTION_WORKSPACE_API}/rule/page`,
    query,
  );

export const getQualityExecutionWorkspace = (
  executionNo: string,
): Promise<ExecutionWorkspaceView> =>
  HttpUtils.getData<ExecutionWorkspaceView>(executionPath(executionNo));

export const getQualityExecutionLogs = (
  executionNo: string,
): Promise<ExecutionLogView> =>
  HttpUtils.getData<ExecutionLogView>(
    `${executionPath(executionNo)}/logs`,
  );
