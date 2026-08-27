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
}

const SUMMARY_ITEMS: SummaryItem[] = [
  {
    key: 'total',
    label: '全部数据源',
    icon: <Database size={18} strokeWidth={1.85} />,
    iconClassName: 'bg-[#eef2ff] text-[#5669da]',
  },
  {
    key: 'connected',
    label: '连接正常',
    icon: <CheckCircle2 size={18} strokeWidth={1.85} />,
    iconClassName: 'bg-[#eef9f2] text-[#28a251]',
  },
  {
    key: 'disconnected',
    label: '连接异常',
    icon: <XCircle size={18} strokeWidth={1.85} />,
    iconClassName: 'bg-[#fff1f2] text-[#e55763]',
  },
  {
    key: 'environmentCount',
    label: '运行环境',
    icon: <Server size={18} strokeWidth={1.85} />,
    iconClassName: 'bg-[#f0f3f6] text-[#667487]',
  },
];

interface DataSourceSummaryCardsProps {
  summary: DataSourceSummary;
}

const DataSourceSummaryCards = ({ summary }: DataSourceSummaryCardsProps) => (
  <motion.section
    variants={PAGE_ANIMATION.cardStagger}
    className="mt-5 grid grid-cols-2 gap-x-6 border-y border-[#eef0f2] lg:grid-cols-4 lg:gap-x-8"
  >
    {SUMMARY_ITEMS.map((item) => (
      <motion.div
        key={item.key}
        variants={PAGE_ANIMATION.fadeUp}
        className="flex min-h-[74px] min-w-0 items-center gap-3 py-4"
      >
        <span
          className={[
            'flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]',
            item.iconClassName,
          ].join(' ')}
        >
          {item.icon}
        </span>
        <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-[#686d76]">
          {item.label}
        </span>
        <strong className="shrink-0 text-[24px] font-semibold leading-8 tracking-[-0.6px] text-[#252832]">
          {summary[item.key]}
        </strong>
      </motion.div>
    ))}
  </motion.section>
);

export default DataSourceSummaryCards;
