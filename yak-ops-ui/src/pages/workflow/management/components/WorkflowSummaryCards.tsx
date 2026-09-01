import { motion } from 'framer-motion';
import { Activity, FilePenLine, GitBranch, Rocket } from 'lucide-react';
import type { ReactNode } from 'react';

import type { WorkflowSummary } from '../model';
import { WORKFLOW_PAGE_ANIMATION } from '../model';

interface SummaryItem {
  key: keyof WorkflowSummary;
  label: string;
  icon: ReactNode;
  iconClassName: string;
}

const SUMMARY_ITEMS: SummaryItem[] = [
  {
    key: 'total',
    label: '全部工作流',
    icon: <GitBranch size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#eef2ff] text-[#5868d8]',
  },
  {
    key: 'online',
    label: '已上线',
    icon: <Rocket size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#edf8f1] text-[#2ea35d]',
  },
  {
    key: 'draftChanged',
    label: '待发布草稿',
    icon: <FilePenLine size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#fff7e9] text-[#c98628]',
  },
  {
    key: 'activeExecutions',
    label: '活动执行',
    icon: <Activity size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#fff1f4] text-[#e34f6d]',
  },
];

interface WorkflowSummaryCardsProps {
  summary: WorkflowSummary;
}

const WorkflowSummaryCards = ({ summary }: WorkflowSummaryCardsProps) => (
  <div
    className="flex flex-wrap gap-3"
  >
    {SUMMARY_ITEMS.map((item) => (
      <div
        key={item.key}
        className="flex min-w-[180px] flex-1 items-center gap-3 rounded-[14px] bg-[#fafbfc] px-4 py-3"
      >
        <span
          className={[
            'flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]',
            item.iconClassName,
          ].join(' ')}
        >
          {item.icon}
        </span>

        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#606571]">
            {item.label}
          </div>
        </div>

        <strong className="ml-auto shrink-0 text-[28px] font-semibold leading-none tracking-[-0.6px] text-[#242731]">
          {summary[item.key]}
        </strong>
      </div>
    ))}
  </div>
);

export default WorkflowSummaryCards;
