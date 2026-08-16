import { Tooltip, message } from 'antd';
import {
  Database,
  LoaderCircle,
  Play,
  Redo2,
  Rocket,
  Save,
  Search,
  Sparkles,
  Undo2,
  Wand2,
  Webhook,
} from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';

import PublishDataServiceModal from '../../components/workbench/PublishDataServiceModal';
import {
  fetchDevelopmentDataServiceContext,
  publishDevelopmentReleaseDataService,
  type DevelopmentDataServiceContext,
  type DevelopmentReleaseDataServiceState,
  type PublishDevelopmentDataServicePayload,
} from '../../data-service-publication';
import type { DevelopmentEditorToolbarContext } from '../types';
import {
  executeSqlEditorCommand,
  type SqlEditorCommand,
} from './commands/sqlEditorCommandBus';
import SqlMetadataContextToolbar from './metadata/SqlMetadataContextToolbar';

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

const actionButtonClassName =
  'flex h-7 shrink-0 items-center gap-1 rounded-[3px] px-1.5 text-[10px] font-medium text-[#667085] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#344054] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

interface ToolbarButtonProps {
  title: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

const ToolbarButton = ({ title, onClick, disabled, children }: ToolbarButtonProps) => (
  <Tooltip title={title} mouseEnterDelay={0.35}>
    <button
      type="button"
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className={iconButtonClassName}
    >
      {children}
    </button>
  </Tooltip>
);

const ToolbarDivider = () => <span className="mx-1 h-4 w-px shrink-0 bg-[#e5e7eb]" />;

const datasetTooltip = (
  state: DevelopmentEditorToolbarContext['datasetState'],
  loading?: boolean,
) => {
  if (loading) return '正在读取 Dataset 状态';
  if (!state) return '请先发布 SQL 版本，再发布为 Dataset';
  if (state.releaseStatus !== 'ONLINE') {
    return 'SQL 发布任务当前未上线，请先在发布中心上线后再发布 Dataset';
  }
  if (!state.datasetId || !state.datasetVersionNo) return `发布 SQL v${state.releaseRevisionNo} 为 Dataset`;
  if (state.datasetSourceRevisionNo !== state.releaseRevisionNo) {
    return `更新 Dataset · DV${state.datasetVersionNo} → SQL v${state.releaseRevisionNo}`;
  }
  const status = state.datasetStatus === 'OFFLINE' ? ' · 已下线' : '';
  return `Dataset DV${state.datasetVersionNo} 已同步 SQL v${state.releaseRevisionNo}${status}`;
};

const dataServiceTooltip = (
  state: DevelopmentReleaseDataServiceState | undefined,
  loading?: boolean,
) => {
  if (loading) return '正在读取 API 发布状态';
  if (!state) return '请先发布 SQL 版本，再发布为 API';
  if (state.releaseStatus !== 'ONLINE') {
    return 'SQL 发布任务当前未上线，请先在发布中心上线后再发布 API';
  }
  if (!state.published) return `发布当前 ONLINE SQL v${state.releaseRevisionNo} 为 API`;
  const apiRevisionNo = state.detail?.sourceRevisionNo;
  if (state.updateAvailable) {
    return `线上 API 仍运行 SQL v${apiRevisionNo || '-'}，当前 ONLINE 为 v${state.releaseRevisionNo}；点击更新`;
  }
  if (state.detail?.enabled === false) {
    return `API 已同步 SQL v${apiRevisionNo || state.releaseRevisionNo}，当前已停用`;
  }
  return `API 已同步当前 ONLINE SQL v${apiRevisionNo || state.releaseRevisionNo}`;
};

const dataServiceActionLabel = (state?: DevelopmentReleaseDataServiceState) => {
  if (!state?.published) return '发布 API';
  const apiRevisionNo = state.detail?.sourceRevisionNo || state.releaseRevisionNo;
  if (state.updateAvailable) return `API v${apiRevisionNo} → v${state.releaseRevisionNo}`;
  if (state.detail?.enabled === false) return `API v${apiRevisionNo} · 停用`;
  return `API v${apiRevisionNo}`;
};

const SqlToolbar = ({
  node,
  onRun,
  onSave,
  onPublish,
  onPublishDataset,
  datasetState,
  datasetLoading,
  datasetPublishing,
  running,
  saving,
  publishing,
}: DevelopmentEditorToolbarContext) => {
  const [dataServiceContext, setDataServiceContext] = useState<DevelopmentDataServiceContext>({});
  const [dataServiceLoading, setDataServiceLoading] = useState(false);
  const [dataServicePublishing, setDataServicePublishing] = useState(false);
  const [dataServiceModalOpen, setDataServiceModalOpen] = useState(false);
  const [dataServiceRefreshKey, setDataServiceRefreshKey] = useState(0);

  useEffect(() => {
    if (publishing) return;
    let active = true;
    setDataServiceLoading(true);
    fetchDevelopmentDataServiceContext(node.id, node.name)
      .then((context) => {
        if (active) setDataServiceContext(context);
      })
      .catch((error) => {
        if (!active) return;
        setDataServiceContext({});
        message.error(error instanceof Error ? error.message : '加载 API 发布状态失败');
      })
      .finally(() => {
        if (active) setDataServiceLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataServiceRefreshKey, node.id, node.name, publishing]);

  const execute = (command: SqlEditorCommand, fallback: string) => {
    if (!executeSqlEditorCommand(node.id, command)) {
      message.info(fallback);
    }
  };
  const datasetNeedsUpdate = Boolean(
    datasetState?.datasetId
      && datasetState.datasetSourceRevisionNo !== datasetState.releaseRevisionNo,
  );
  const datasetReleaseUnavailable = Boolean(
    datasetState && datasetState.releaseStatus !== 'ONLINE',
  );
  const datasetStateLabel = datasetState?.datasetVersionNo
    ? `DV${datasetState.datasetVersionNo}${datasetNeedsUpdate ? ' · 待更新' : datasetState.datasetStatus === 'OFFLINE' ? ' · 已下线' : ''}`
    : undefined;

  const release = dataServiceContext.release;
  const dataServiceState = dataServiceContext.dataServiceState;
  const dataServiceReleaseUnavailable = Boolean(
    dataServiceState && dataServiceState.releaseStatus !== 'ONLINE',
  );
  const dataServiceActionRequired = Boolean(
    dataServiceState
      && dataServiceState.releaseStatus === 'ONLINE'
      && (!dataServiceState.published || dataServiceState.updateAvailable),
  );

  const openDataServiceModal = () => {
    if (!release || !dataServiceState || release.status !== 'ONLINE') return;
    setDataServiceModalOpen(true);
  };

  const publishDataService = async (payload: PublishDevelopmentDataServicePayload) => {
    if (!release) return;
    const wasPublished = Boolean(dataServiceState?.published);
    const previousRevisionNo = dataServiceState?.detail?.sourceRevisionNo;
    setDataServicePublishing(true);
    try {
      const detail = await publishDevelopmentReleaseDataService(release.assetId, payload);
      setDataServiceContext({
        release,
        dataServiceState: {
          published: true,
          updateAvailable: false,
          releaseRevisionNo: release.currentRevisionNo,
          releaseStatus: release.status,
          detail,
        },
      });
      setDataServiceModalOpen(false);
      setDataServiceRefreshKey((current) => current + 1);
      if (!wasPublished) {
        message.success(`API 已发布 · SQL v${release.currentRevisionNo}`);
      } else if (previousRevisionNo && previousRevisionNo !== release.currentRevisionNo) {
        message.success(`API 已更新 · SQL v${previousRevisionNo} → v${release.currentRevisionNo}`);
      } else {
        message.success('API 配置已保存');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 API 失败');
    } finally {
      setDataServicePublishing(false);
    }
  };

  return (
    <>
      <div className="flex h-full w-full min-w-0 items-center justify-between gap-3">
        <div className="flex shrink-0 items-center gap-0.5">
          <ToolbarButton title={running ? 'SQL 运行中' : '运行当前 SQL'} disabled={running} onClick={onRun}>
            {running ? <LoaderCircle size={15} className="animate-spin" /> : <Play size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton title="保存草稿" disabled={saving || publishing || running} onClick={onSave}>
            {saving ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarButton title="发布版本" disabled={saving || publishing || running} onClick={onPublish}>
            {publishing ? <LoaderCircle size={15} className="animate-spin" /> : <Rocket size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarButton
            title={datasetTooltip(datasetState, datasetLoading)}
            disabled={!onPublishDataset || !datasetState || datasetReleaseUnavailable || datasetLoading || datasetPublishing || saving || publishing || running}
            onClick={() => onPublishDataset?.()}
          >
            {datasetLoading || datasetPublishing ? (
              <LoaderCircle size={15} className="animate-spin" />
            ) : (
              <Database
                size={15}
                strokeWidth={1.8}
                className={datasetNeedsUpdate ? 'text-[var(--yak-brand-color)]' : undefined}
              />
            )}
          </ToolbarButton>
          {datasetStateLabel ? (
            <span
              className={[
                'mr-1 text-[10px] font-medium',
                datasetNeedsUpdate
                  ? 'text-[var(--yak-brand-color)]'
                  : datasetState?.datasetStatus === 'OFFLINE'
                    ? 'text-[#b54708]'
                    : 'text-[#667085]',
              ].join(' ')}
            >
              {datasetStateLabel}
            </span>
          ) : null}
          <Tooltip title={dataServiceTooltip(dataServiceState, dataServiceLoading)} mouseEnterDelay={0.35}>
            <button
              type="button"
              aria-label={dataServiceTooltip(dataServiceState, dataServiceLoading)}
              disabled={!release || !dataServiceState || dataServiceReleaseUnavailable || dataServiceLoading || dataServicePublishing || saving || publishing || running}
              onClick={openDataServiceModal}
              className={[
                actionButtonClassName,
                dataServiceActionRequired ? 'text-[var(--yak-brand-color)]' : '',
                dataServiceState?.detail?.enabled === false && !dataServiceState?.updateAvailable
                  ? 'text-[#b54708]'
                  : '',
              ].join(' ')}
            >
              {dataServiceLoading || dataServicePublishing ? (
                <LoaderCircle size={14} className="animate-spin" />
              ) : (
                <Webhook size={14} strokeWidth={1.8} />
              )}
              <span>{dataServiceLoading ? 'API 状态' : dataServiceActionLabel(dataServiceState)}</span>
              {dataServiceState?.updateAvailable ? (
                <span className="ml-0.5 text-[9px] font-medium">待更新</span>
              ) : null}
            </button>
          </Tooltip>
          <ToolbarDivider />
          <ToolbarButton title="撤销" disabled={running} onClick={() => execute('undo', 'SQL 编辑器尚未就绪')}>
            <Undo2 size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarButton title="重做" disabled={running} onClick={() => execute('redo', 'SQL 编辑器尚未就绪')}>
            <Redo2 size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarButton title="查找" onClick={() => execute('find', 'SQL 编辑器尚未就绪')}>
            <Search size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton title="格式化 SQL" disabled={running} onClick={() => execute('format', 'SQL 编辑器尚未就绪')}>
            <Wand2 size={15} strokeWidth={1.8} />
          </ToolbarButton>
          <ToolbarButton title="触发智能提示" onClick={() => execute('suggest', 'SQL 编辑器尚未就绪')}>
            <Sparkles size={15} strokeWidth={1.8} />
          </ToolbarButton>
        </div>
        <SqlMetadataContextToolbar nodeId={node.id} />
      </div>

      <PublishDataServiceModal
        open={dataServiceModalOpen}
        nodeName={node.name}
        release={release}
        dataServiceState={dataServiceState}
        publishing={dataServicePublishing}
        onCancel={() => {
          if (!dataServicePublishing) setDataServiceModalOpen(false);
        }}
        onPublish={(payload) => void publishDataService(payload)}
      />
    </>
  );
};

export default SqlToolbar;
