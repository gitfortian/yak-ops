import { FileSuffixIcon } from '@/components/resource/FileSuffixIcon';
import { YAK_OPS_PERMISSIONS } from '@/constants/yakOpsPermissions';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { API_SUCCESS_CODE } from '@/services/http/response';
import {
  fetchResourceTree,
  uploadResource,
  type ResourceItem,
} from '@/services/resource-management';
import { history, useIntl } from '@umijs/max';
import { message } from 'antd';
import {
  ChevronRight,
  Files,
  FolderTree,
  HardDrive,
  LockKeyhole,
  RefreshCw,
  Upload,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';

const RESOURCE_MANAGEMENT_PATH = '/resource-management';
const ROOT_RESOURCE_ID = 0;
const RECENT_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
const RECENT_FILE_LIMIT = 3;

interface ResourceSummary {
  files: number;
  folders: number;
  totalBytes: number;
}

const parseResourceTime = (value?: string) => {
  if (!value) return Number.NaN;
  const normalized = value.includes(' ') ? value.replace(' ', 'T') : value;
  return new Date(normalized).getTime();
};

const flattenResources = (resources: ResourceItem[]) => {
  const result: ResourceItem[] = [];

  const visit = (items: ResourceItem[]) => {
    items.forEach((item) => {
      result.push(item);
      if (item.children?.length) visit(item.children);
    });
  };

  visit(resources);
  return result;
};

const summarizeResources = (resources: ResourceItem[]): ResourceSummary =>
  flattenResources(resources).reduce<ResourceSummary>(
    (summary, item) => {
      if (item.nodeType === 'DIRECTORY') {
        summary.folders += 1;
      } else {
        summary.files += 1;
        summary.totalBytes += Number(item.fileSize || 0);
      }
      return summary;
    },
    { files: 0, folders: 0, totalBytes: 0 },
  );

const getRecentFiles = (resources: ResourceItem[]) => {
  const recentBoundary = Date.now() - RECENT_WINDOW_MS;

  return flattenResources(resources)
    .filter((item) => item.nodeType === 'FILE')
    .map((item) => ({
      item,
      timestamp: parseResourceTime(item.updateTime || item.createTime),
    }))
    .filter(
      ({ timestamp }) =>
        Number.isFinite(timestamp) && timestamp >= recentBoundary,
    )
    .sort((left, right) => right.timestamp - left.timestamp)
    .slice(0, RECENT_FILE_LIMIT)
    .map(({ item }) => item);
};

const formatFileSize = (bytes?: number) => {
  const value = Number(bytes || 0);
  if (value <= 0) return '0 B';

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const unitIndex = Math.min(
    Math.floor(Math.log(value) / Math.log(1024)),
    units.length - 1,
  );
  const normalized = value / 1024 ** unitIndex;
  const precision = normalized >= 100 || unitIndex === 0 ? 0 : normalized >= 10 ? 1 : 2;
  return `${normalized.toFixed(precision)} ${units[unitIndex]}`;
};

const formatRelativeTime = (value: string | undefined, locale: string) => {
  const timestamp = parseResourceTime(value);
  if (!Number.isFinite(timestamp)) return '--';

  const diffSeconds = Math.round((timestamp - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  const absoluteSeconds = Math.abs(diffSeconds);

  if (absoluteSeconds < 60) return formatter.format(diffSeconds, 'second');

  const diffMinutes = Math.round(diffSeconds / 60);
  if (Math.abs(diffMinutes) < 60) return formatter.format(diffMinutes, 'minute');

  const diffHours = Math.round(diffMinutes / 60);
  if (Math.abs(diffHours) < 24) return formatter.format(diffHours, 'hour');

  const diffDays = Math.round(diffHours / 24);
  return formatter.format(diffDays, 'day');
};

function ResourceCenterSkeleton() {
  return (
    <div className="mt-4 animate-pulse">
      <div className="grid grid-cols-3 gap-2">
        {Array.from({ length: 3 }).map((_, index) => (
          <div
            key={index}
            className="h-[82px] rounded-[14px] border border-[#f0f1f3] bg-[#fafbfc] p-3"
          >
            <div className="h-3 w-14 rounded bg-[#eceef1]" />
            <div className="mt-3 h-5 w-16 rounded bg-[#e5e7eb]" />
          </div>
        ))}
      </div>

      <div className="mt-5 border-t border-[#eef0f3] pt-4">
        <div className="h-4 w-20 rounded bg-[#eceef1]" />
        <div className="mt-3 space-y-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-[11px] bg-[#f0f1f3]" />
              <div className="min-w-0 flex-1">
                <div className="h-3.5 w-3/5 rounded bg-[#eceef1]" />
                <div className="mt-2 h-3 w-2/5 rounded bg-[#f1f2f4]" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function HomeResourceCenter() {
  const intl = useIntl();
  const { can } = usePermissionAccess();
  const canRead = can(YAK_OPS_PERMISSIONS.resource.read);
  const canCreate = can(YAK_OPS_PERMISSIONS.resource.create);
  const uploadInputRef = useRef<HTMLInputElement>(null);

  const [resources, setResources] = useState<ResourceItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [uploading, setUploading] = useState(false);

  const loadResources = useCallback(async () => {
    if (!canRead) return;

    try {
      setLoading(true);
      setFailed(false);
      const response = await fetchResourceTree();
      if (response.code !== API_SUCCESS_CODE) {
        setFailed(true);
        return;
      }
      setResources(response.data || []);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [canRead]);

  useEffect(() => {
    void loadResources();
  }, [loadResources]);

  const summary = useMemo(() => summarizeResources(resources), [resources]);
  const recentFiles = useMemo(() => getRecentFiles(resources), [resources]);

  const handleUpload = async (file?: File) => {
    if (!file || !canCreate) return;

    try {
      setUploading(true);
      const response = await uploadResource(ROOT_RESOURCE_ID, file);
      if (response.code !== API_SUCCESS_CODE) {
        message.error(
          response.message ||
            intl.formatMessage({ id: 'pages.home.resourceCenter.uploadFailed' }),
        );
        return;
      }
      message.success(
        response.message ||
          intl.formatMessage({ id: 'pages.home.resourceCenter.uploadSuccess' }),
      );
      await loadResources();
    } catch {
      message.error(
        intl.formatMessage({ id: 'pages.home.resourceCenter.uploadFailed' }),
      );
    } finally {
      setUploading(false);
    }
  };

  const statItems = [
    {
      key: 'files',
      label: intl.formatMessage({ id: 'pages.home.resourceCenter.files' }),
      value: summary.files.toLocaleString(intl.locale),
      icon: Files,
      iconClassName: 'bg-[#fff1f4] text-[#ff4d6d]',
    },
    {
      key: 'folders',
      label: intl.formatMessage({ id: 'pages.home.resourceCenter.folders' }),
      value: summary.folders.toLocaleString(intl.locale),
      icon: FolderTree,
      iconClassName: 'bg-[#f3f5f8] text-[#707681]',
    },
    {
      key: 'storage',
      label: intl.formatMessage({ id: 'pages.home.resourceCenter.storage' }),
      value: formatFileSize(summary.totalBytes),
      icon: HardDrive,
      iconClassName: 'bg-[#f3f1ff] text-[#7568ed]',
    },
  ];

  return (
    <section className="flex min-h-[420px] flex-1 flex-col rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.resourceCenter.title' })}
        </h2>

        {canRead ? (
          <button
            type="button"
            onClick={() => history.push(RESOURCE_MANAGEMENT_PATH)}
            className="flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
          >
            {intl.formatMessage({ id: 'pages.home.common.viewAll' })}
            <ChevronRight size={14} strokeWidth={1.8} />
          </button>
        ) : null}
      </header>

      {!canRead ? (
        <HomeEmptyState
          icon={LockKeyhole}
          title={intl.formatMessage({
            id: 'pages.home.resourceCenter.noPermission',
          })}
          size="medium"
          className="min-h-[300px] flex-1"
        />
      ) : loading && resources.length === 0 ? (
        <ResourceCenterSkeleton />
      ) : (
        <>
          <div className="mt-4 grid grid-cols-3 gap-2">
            {statItems.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.key}
                  className="min-w-0 rounded-[14px] border border-[#f0f1f3] bg-[#fbfbfc] px-3 py-3"
                >
                  <div className="flex min-w-0 items-center gap-2">
                    <span
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-[9px] ${item.iconClassName}`}
                    >
                      <Icon size={14} strokeWidth={1.8} />
                    </span>
                    <span className="min-w-0 truncate text-[10px] text-[#9398a1]">
                      {item.label}
                    </span>
                  </div>
                  <strong
                    className="mt-2 block truncate text-[16px] font-semibold tracking-[-0.2px] text-[#292d36]"
                    title={failed ? '--' : item.value}
                  >
                    {failed ? '--' : item.value}
                  </strong>
                </div>
              );
            })}
          </div>

          <div className="mt-5 flex min-h-[190px] flex-1 flex-col border-t border-[#eef0f3] pt-4">
            <div className="flex items-center justify-between gap-3">
              <strong className="text-[13px] font-semibold text-[#3c4049]">
                {intl.formatMessage({ id: 'pages.home.resourceCenter.recent' })}
              </strong>
              <span className="text-[10px] text-[#9a9fa8]">
                {intl.formatMessage({
                  id: 'pages.home.resourceCenter.recentRange',
                })}
              </span>
            </div>

            {failed ? (
              <div className="flex flex-1 flex-col items-center justify-center py-5 text-center">
                <HomeEmptyState
                  icon={RefreshCw}
                  title={intl.formatMessage({ id: 'pages.home.common.loadFailed' })}
                  size="small"
                />
                <button
                  type="button"
                  onClick={() => void loadResources()}
                  className="mt-2 border-0 bg-transparent p-0 text-[10px] font-medium text-[#6f7681] hover:text-[#252832]"
                >
                  {intl.formatMessage({ id: 'pages.home.resourceCenter.retry' })}
                </button>
              </div>
            ) : recentFiles.length ? (
              <div className="mt-2 divide-y divide-[#f1f2f4]">
                {recentFiles.map((resource) => (
                  <div
                    key={String(resource.id)}
                    className="flex min-w-0 items-center gap-3 py-2.5"
                    title={resource.fullPath || resource.name}
                  >
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] bg-[#f5f6f8]">
                      <FileSuffixIcon suffix={resource.suffix} size={18} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[12px] font-medium text-[#3c4049]">
                        {resource.name}
                      </div>
                      <div className="mt-1 flex min-w-0 items-center gap-1.5 text-[10px] text-[#9a9fa8]">
                        <span className="shrink-0">
                          {formatFileSize(resource.fileSize)}
                        </span>
                        <span className="h-0.5 w-0.5 shrink-0 rounded-full bg-[#c7cad0]" />
                        <span className="min-w-0 truncate">
                          {formatRelativeTime(
                            resource.updateTime || resource.createTime,
                            intl.locale,
                          )}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <HomeEmptyState
                icon={Files}
                title={intl.formatMessage({
                  id: 'pages.home.resourceCenter.emptyRecent',
                })}
                description={intl.formatMessage({
                  id: 'pages.home.resourceCenter.emptyRecentDescription',
                })}
                size="small"
                className="min-h-[150px] flex-1"
              />
            )}
          </div>

          <div className="mt-4 flex min-h-11 items-center rounded-[13px] border border-[#ffe5ea] bg-[#fff7f9] p-1">
            {canCreate ? (
              <button
                type="button"
                disabled={uploading}
                onClick={() => uploadInputRef.current?.click()}
                className="flex min-w-0 flex-1 items-center justify-center gap-1.5 rounded-[10px] border-0 bg-white px-3 py-2 text-[11px] font-medium text-[#ed4163] shadow-[0_1px_2px_rgba(31,35,41,0.04)] transition-colors hover:bg-[#fffafb] disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Upload size={14} strokeWidth={1.8} />
                <span className="truncate">
                  {uploading
                    ? intl.formatMessage({
                        id: 'pages.home.resourceCenter.uploading',
                      })
                    : intl.formatMessage({
                        id: 'pages.home.resourceCenter.upload',
                      })}
                </span>
              </button>
            ) : null}

            <button
              type="button"
              onClick={() => history.push(RESOURCE_MANAGEMENT_PATH)}
              className={`flex shrink-0 items-center justify-center gap-0.5 border-0 bg-transparent px-3 py-2 text-[10px] text-[#7f858f] transition-colors hover:text-[#3c4049] ${
                canCreate ? '' : 'w-full'
              }`}
            >
              {intl.formatMessage({ id: 'pages.home.resourceCenter.enter' })}
              <ChevronRight size={13} strokeWidth={1.8} />
            </button>
          </div>
        </>
      )}

      <input
        ref={uploadInputRef}
        type="file"
        className="hidden"
        onChange={(event) => {
          const file = event.currentTarget.files?.[0];
          event.currentTarget.value = '';
          void handleUpload(file);
        }}
      />
    </section>
  );
}
