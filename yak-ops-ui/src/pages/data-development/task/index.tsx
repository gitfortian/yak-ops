import { Result } from 'antd';
import { useLocation, useParams } from '@umijs/max';

import type { DevelopmentTaskType } from '../types';
import SqlTaskEditor from './SqlTaskEditor';

export default function DataDevelopmentTaskPage() {
  const { id } = useParams<{ id?: string }>();
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const requestedType = (params.get('type') || 'SQL').toUpperCase() as DevelopmentTaskType;
  const projectId = Number(params.get('projectId') || 0) || undefined;
  const directoryId = Number(params.get('directoryId') || 0) || undefined;
  const initialName = params.get('name')?.trim() || undefined;
  const taskId = id ? Number(id) : undefined;

  if (requestedType !== 'SQL') {
    return (
      <Result
        status="info"
        title={`${requestedType} 任务暂未开放`}
        subTitle="数据开发已保留统一 Editor 扩展入口，当前阶段先完成 SQL。"
      />
    );
  }

  return (
    <SqlTaskEditor
      taskId={taskId}
      initialName={initialName}
      initialProjectId={projectId}
      initialDirectoryId={directoryId}
    />
  );
}
