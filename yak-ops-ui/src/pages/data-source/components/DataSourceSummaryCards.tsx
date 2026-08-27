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
  description: string;
  icon: ReactNode;
  iconClassName: string;
  glowClassName: string;
}

const SUMMARY_ITEMS: SummaryItem[] = [
  {
    key: 'total',
    label: '全部数据源',
    description: '当前已接入',
    icon: <Database size={19} strokeWidth={1.85} />,
    iconClassName:
      'bg-[linear-gradient(145deg,#eef2ff_0%,#e4eaff_100%)] text-[#5669da]',
    glowClassName: 'bg-[#7388ff]/10',
  },
  {
    key: 'connected',
    label: '连接正常',
    description: '可正常访问',
    icon: <CheckCircle2 size={19} strokeWidth={1.85} />,
    iconClassName:
      'bg-[linear-gradient(145deg,#eef9f2_0%,#e2f5e8_100%)] text-[#28a251]',
    glowClassName: 'bg-[#42ba6f]/10',
  },
  {
    key: 'disconnected',
    label: '连接异常',
    description: '需要关注',
    icon: <XCircle size={19} strokeWidth={1.85} />,
    iconClassName:
      'bg-[linear-gradient(145deg,#fff2f3_0%,#ffe7e9_100%)] text-[#e55763]',
    glowClassName: 'bg-[#ef6570]/10',
  },
  {
    key: 'environmentCount',
    label: '运行环境',
    description: '已覆盖环境',
    icon: <Server size={19} strokeWidth={1.85} />,
    iconClassName:
      'bg-[linear-gradient(145deg,#f1f4f7_0%,#e8edf2_100%)] text-[#667487]',
    glowClassName: 'bg-[#738196]/10',
  },
];

interface DataSourceSummaryCardsProps {
  summary: DataSourceSummary;
}

const DataSourceSummaryCards = ({ summary }: DataSourceSummaryCardsProps) => (
  <motion.section
    variants={PAGE_ANIMATION.cardStagger}
    className="mt-5 grid grid-cols-1 gap-[14px] sm:grid-cols-2 xl:grid-cols-4"
  >
    {SUMMARY_ITEMS.map((item) => (
      <motion.div
        key={item.key}
        variants={PAGE_ANIMATION.fadeUp}
        className="group relative min-h-[104px] overflow-hidden rounded-[16px] border border-[rgba(31,35,41,0.075)] bg-white/[0.96] px-[18px] py-4 shadow-[0_3px_10px_rgba(31,35,41,0.04),0_1px_2px_rgba(31,35,41,0.02)] transition-[transform,border-color,box-shadow] duration-[260ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:-translate-y-px hover:border-[rgba(31,35,41,0.10)] hover:shadow-[0_8px_20px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.02)]"
      >
        <span
          aria-hidden="true"
          className={[
            'pointer-events-none absolute -right-8 -top-10 h-28 w-28 rounded-full blur-2xl transition-transform duration-300 group-hover:scale-110',
            item.glowClassName,
          ].join(' ')}
        />

        <div className="relative flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <span
              className={[
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] border border-white/80 shadow-[0_4px_10px_rgba(31,35,41,0.035)]',
                item.iconClassName,
              ].join(' ')}
            >
              {item.icon}
            </span>

            <div className="min-w-0">
              <div className="truncate text-[13px] font-medium leading-5 text-[#626771]">
                {item.label}
              </div>
              <div className="mt-0.5 truncate text-[11px] leading-[18px] text-[#a3a7af]">
                {item.description}
              </div>
            </div>
          </div>

          <strong className="shrink-0 text-[28px] font-semibold leading-9 tracking-[-0.8px] text-[#252832]">
            {summary[item.key]}
          </strong>
        </div>
      </motion.div>
    ))}
  </motion.section>
);

export default DataSourceSummaryCards;
