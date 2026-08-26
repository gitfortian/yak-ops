import { YakButton } from '@/components/ui';
import type { DataSourceRecord } from '@/services/data-source';
import { motion } from 'framer-motion';
import { Pencil, Trash2, Unplug } from 'lucide-react';

import { environmentTagConfigMap, PAGE_ANIMATION } from '../constants';
import DatabaseIcons from '../icon/DatabaseIcons';
import type { DataSourcePermissions, DataSourceViewMode } from '../types';
import { dataSourceRecordKey } from '../types';
import DataSourceStatus from './DataSourceStatus';

interface DataSourceCardProps {
  record: DataSourceRecord;
  viewMode: DataSourceViewMode;
  permissions: DataSourcePermissions;
  testingId: string;
  editingId: string;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onTestConnection: (record: DataSourceRecord) => void;
}

const DataSourceCard = ({
  record,
  viewMode,
  permissions,
  testingId,
  editingId,
  onEdit,
  onDelete,
  onTestConnection,
}: DataSourceCardProps) => {
  const environmentConfig = environmentTagConfigMap[
    record.environment || ''
  ] || {
    text: record.environmentName || '未分类',
    color: '#667085',
    backgroundColor: '#f2f4f7',
    icon: null,
  };
  const currentId = dataSourceRecordKey(record.id);
  const actionAvailable =
    permissions.canTest || permissions.canUpdate || permissions.canDelete;
  const isListView = viewMode === 'list';

  return (
    <motion.article
      variants={PAGE_ANIMATION.fadeUp}
      className={[
        'group min-w-0 overflow-hidden rounded-[9px] border border-black/[0.075] bg-white',
        'transition-[transform,border-color,box-shadow] duration-200',
        'hover:-translate-y-0.5 hover:border-black/[0.11] hover:shadow-[0_10px_28px_rgba(22,24,35,0.07)]',
        isListView
          ? 'grid grid-cols-[minmax(360px,1.35fr)_minmax(360px,1fr)] max-xl:grid-cols-1'
          : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="flex min-h-[92px] items-start justify-between gap-[15px] bg-[radial-gradient(circle_at_100%_0,rgba(88,110,255,0.08),transparent_37%),linear-gradient(110deg,#fbfcff_0%,#f7f8fc_100%)] px-[19px] pb-4 pt-[19px]">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-[47px] w-[47px] shrink-0 items-center justify-center rounded-xl border border-black/[0.055] bg-white shadow-[0_5px_14px_rgba(22,24,35,0.055)]">
            <DatabaseIcons dbType={record.dbType} width="30" height="30" />
          </div>

          <div className="min-w-0">
            <div className="flex min-w-0 items-center gap-2">
              <h3
                title={record.name}
                className="m-0 min-w-0 truncate text-[15px] font-semibold text-[#161823]"
              >
                {record.name || '未命名数据源'}
              </h3>

              <span
                className="inline-flex h-5 shrink-0 items-center gap-1 whitespace-nowrap rounded-full px-[7px] text-[9px] font-semibold"
                style={{
                  color: environmentConfig.color,
                  background: environmentConfig.backgroundColor,
                }}
              >
                {environmentConfig.icon}
                {record.environmentName || environmentConfig.text}
              </span>
            </div>

            <p
              title={record.jdbcUrl}
              className="mt-1.5 max-w-[410px] truncate text-[11px] text-black/[0.43]"
            >
              {record.jdbcUrl || '暂未配置连接地址'}
            </p>
          </div>
        </div>

        {actionAvailable ? (
          <div className="pointer-events-none flex shrink-0 -translate-y-1 gap-1 opacity-0 transition-all duration-150 group-hover:pointer-events-auto group-hover:translate-y-0 group-hover:opacity-100">
            {permissions.canTest ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title="测试连接"
                loading={testingId === currentId}
                disabled={Boolean(testingId) && testingId !== currentId}
                className="!h-[29px] !w-[29px] !rounded-[7px] !border !border-black/[0.07] !bg-white/90 !p-0 !text-black/[0.52] hover:!text-[#4058c8]"
                icon={<Unplug size={15} strokeWidth={1.9} />}
                onClick={() => onTestConnection(record)}
              />
            ) : null}

            {permissions.canUpdate ? (
              <YakButton
                type="text"
                size="small"
                iconOnly
                title="编辑数据源"
                loading={editingId === currentId}
                disabled={Boolean(editingId) && editingId !== currentId}
                className="!h-[29px] !w-[29px] !rounded-[7px] !border !border-black/[0.07] !bg-white/90 !p-0 !text-black/[0.52] hover:!text-[#4058c8]"
                icon={<Pencil size={15} strokeWidth={1.9} />}
                onClick={() => onEdit(record)}
              />
            ) : null}

            {permissions.canDelete ? (
              <YakButton
                type="text"
                size="small"
                danger
                iconOnly
                title="删除数据源"
                className="!h-[29px] !w-[29px] !rounded-[7px] !border !border-black/[0.07] !bg-white/90 !p-0"
                icon={<Trash2 size={15} strokeWidth={1.9} />}
                onClick={() => onDelete(record)}
              />
            ) : null}
          </div>
        ) : null}
      </div>

      <div
        className={[
          'grid grid-cols-3 px-[19px] py-[15px]',
          isListView
            ? 'items-center border-l border-black/[0.055] max-xl:border-l-0 max-xl:border-t'
            : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <div className="flex min-w-0 flex-col gap-1.5">
          <span className="text-[10px] text-black/[0.38]">连接状态</span>
          <DataSourceStatus status={record.connStatus} />
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 border-l border-black/[0.06] pl-3.5">
          <span className="text-[10px] text-black/[0.38]">数据源类型</span>
          <strong className="truncate text-[11px] font-semibold text-black/[0.78]">
            {String(record.dbType || '-')}
          </strong>
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 border-l border-black/[0.06] pl-3.5">
          <span className="text-[10px] text-black/[0.38]">最近更新</span>
          <strong className="truncate text-[11px] font-semibold text-black/[0.78]">
            {record.updateTime || '-'}
          </strong>
        </div>
      </div>
    </motion.article>
  );
};

export default DataSourceCard;
