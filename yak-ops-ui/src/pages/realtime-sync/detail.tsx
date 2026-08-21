import { history, useParams } from '@umijs/max';
import { message, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { realtimeApi } from './api';
import JobEditor from './JobEditor';
import type { DataSourceOption, RealtimeJob } from './types';

export default function RealtimeSyncDetail() {
  const { id } = useParams<{ id: string }>();
  const [job, setJob] = useState<RealtimeJob>();
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  useEffect(() => {
    Promise.all([realtimeApi.detail(Number(id)), realtimeApi.dataSources()])
      .then(([detail, sources]) => {
        setJob(detail.data);
        setDataSources(sources.data || []);
      })
      .catch((error) => message.error(error?.message || '加载实时同步配置失败'));
  }, [id]);
  if (!job)
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spin />
      </div>
    );
  return (
    <JobEditor
      open
      job={job}
      dataSources={dataSources}
      onClose={() => history.push('/sync/realtime')}
      onSaved={() => history.push('/sync/realtime')}
    />
  );
}
