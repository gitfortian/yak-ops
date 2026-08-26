import type { DataSourceSummary } from '@/services/data-source';
import { motion } from 'framer-motion';
import { CheckCircle2, Database, Server, XCircle } from 'lucide-react';
import type { ReactNode } from 'react';

import { PAGE_ANIMATION } from '../constants';

interface SummaryItem {
  key: keyof Pick<
    DataSourceSummary,
    'total' | 'connected' | 'disconnected' | 'environmentCount'
  >;
  label: string;
  icon: ReactNode;
  iconClassName: string;
  itemClassName: string;
}

const SUMMARY_ITEMS: SummaryItem[] = [
  {
    key: 'total',
    label: '全部数据源',
    icon: <Database size={20} strokeWidth={1.8} />,
    iconClassName: 'bg-[#edf0ff] text-[#4e62d6]',
    itemClassName: '',
  },
  {
    key: 'connected',
    label: '连接正常',
    icon: <CheckCircle2 size={20} strokeWidth={1.8} />,
    iconClassName: 'bg-[#eef9f0] text-[#25a244]',
    itemClassName: 'border-l border-black/[0.055]',
  },
  {
    key: 'disconnected',
    label: '连接异常',
    icon: <XCircle size={20} strokeWidth={1.8} />,
    iconClassName: 'bg-[#fff0f0] text-[#e85959]',
    itemClassName:
      'border-l border-black/[0.055] max-xl:border-l-0 max-xl:border-t',
  },
  {
    key: 'environmentCount',
    label: '运行环境',
    icon: <Server size={20} strokeWidth={1.8} />,
    iconClassName: 'bg-[#eef2f6] text-[#617084]',
    itemClassName: 'border-l border-black/[0.055] max-xl:border-t',
  },
];

interface DataSourceSummaryCardsProps {
  summary: DataSourceSummary;
}

const DataSourceSummaryCards = ({ summary }: DataSourceSummaryCardsProps) => (
  <motion.section
    variants={PAGE_ANIMATION.fadeUp}
    className="mt-[26px] grid grid-cols-4 overflow-hidden rounded-[9px] border border-black/[0.055] bg-[radial-gradient(circle_at_85%_10%,rgba(88,110,255,0.08),transparent_31%),linear-gradient(105deg,#fcfcff_0%,#f8f9ff_100%)] max-xl:grid-cols-2"
  >
    {SUMMARY_ITEMS.map((item) => (
      <div
        key={item.key}
        className={[
          'flex min-h-[92px] items-center gap-[13px] px-6 py-5',
          item.itemClassName,
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <span
          className={[
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px]',
            item.iconClassName,
          ].join(' ')}
        >
          {item.icon}
        </span>
        <div className="flex min-w-0 flex-col">
          <span className="text-xs text-black/[0.45]">{item.label}</span>
          <strong className="mt-1.5 text-2xl font-bold leading-7 text-[#161823]">
            {summary[item.key]}
          </strong>
        </div>
      </div>
    ))}
  </motion.section>
);

export default DataSourceSummaryCards;
