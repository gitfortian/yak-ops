import YakButton from '@/components/YakButton';
import {
  ArrowRightOutlined,
  DeleteOutlined,
  DisconnectOutlined,
} from '@ant-design/icons';
import { Card } from 'antd';
import React from 'react';
import { environmentTagConfigMap } from '../constants';
import DatabaseIcons from '../icon/DatabaseIcons';
import type { DataSourceRecord } from '../types';
import DataSourceStatus from './DataSourceStatus';

interface DataSourceCardProps {
  record: DataSourceRecord;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onTestConnection: (record: DataSourceRecord) => void;
}

const DataSourceCard: React.FC<DataSourceCardProps> = ({
  record,
  onEdit,
  onDelete,
  onTestConnection,
}) => {
  const environmentConfig = environmentTagConfigMap[
    record.environment || ''
  ] || {
    text: record.environmentName || '-',
    color: '#8c8c8c',
    backgroundColor: '#fafafa',
    icon: null,
  };

  return (
    <Card
      bodyStyle={{ padding: 0 }}
      className={[
        'group relative overflow-hidden rounded-3xl border border-[#F0F0F0]',
        'bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)]',
        '!transition-shadow !duration-200 !ease-out',
        'hover:!translate-y-0 hover:!transform-none',
        'hover:shadow-[0_10px_20px_rgba(15,23,42,0.06)]',
      ].join(' ')}
    >
      <div className="relative h-[88px] bg-[hsl(210_40%_96.1%)]">
        <div
          className={[
            'absolute left-6 bottom-[-24px] z-[2]',
            'flex h-16 w-16 items-center justify-center rounded-full',
            'border-4 border-white bg-white',
            'shadow-[0_4px_12px_rgba(0,0,0,0.12)]',
          ].join(' ')}
        >
          <DatabaseIcons dbType={record.dbType} width="28" height="28" />
        </div>

        <div className="absolute right-5 top-4">
          <span
            className={[
              'inline-flex items-center gap-1.5 rounded-full',
              'px-2.5 py-1 text-xs font-medium leading-none',
            ].join(' ')}
            style={{
              background: environmentConfig.backgroundColor,
              color: environmentConfig.color,
            }}
          >
            {environmentConfig.icon}
            {record.environmentName || environmentConfig.text}
          </span>
        </div>

        <div
          className={[
            'absolute left-3 top-3 z-[3] flex gap-2',
            'opacity-0 translate-y-[-6px] pointer-events-none',
            'transition-all duration-200 ease-out',
            'group-hover:opacity-100 group-hover:translate-y-0 group-hover:pointer-events-auto',
          ].join(' ')}
        >
          <YakButton
            type="text"
            size="small"
            danger
            iconOnly
            icon={<DeleteOutlined />}
            aria-label="删除数据源"
            onClick={(event) => {
              event.stopPropagation();
              onDelete(record);
            }}
          />

          <YakButton
            type="text"
            size="small"
            iconOnly
            icon={<DisconnectOutlined />}
            aria-label="测试连接"
            onClick={(event) => {
              event.stopPropagation();
              onTestConnection(record);
            }}
          />
        </div>
      </div>

      <div className="px-6 pb-5 pt-9">
        <div
          className="mb-2 truncate text-lg font-bold text-[#1F1F1F]"
          title={record.name}
        >
          {record.name || '-'}
        </div>

        <div
          className="mb-3 truncate text-[13px] text-[#262626]"
          title={record.jdbcUrl}
        >
          {record.jdbcUrl || '-'}
        </div>

        <div className="mb-4">
          <DataSourceStatus status={record.connStatus} />
        </div>

        <div className="mb-4 mt-3 text-xs text-[#8C8C8C]">
          <span className="font-medium text-[#595959]">
            {record.updateTime || '-'}
          </span>
        </div>

        <YakButton block className="!h-[42px]" onClick={() => onEdit(record)}>
          查看详情
          <ArrowRightOutlined />
        </YakButton>
      </div>
    </Card>
  );
};

export default DataSourceCard;
