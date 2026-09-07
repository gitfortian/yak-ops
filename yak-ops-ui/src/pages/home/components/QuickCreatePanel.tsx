import { history, useIntl } from '@umijs/max';
import {
  ArrowRightLeft,
  Braces,
  ChevronRight,
  RadioTower,
  Workflow,
} from 'lucide-react';
import type { ReactNode } from 'react';

type QuickCreateKey = 'offline' | 'realtime' | 'development' | 'workflow';

interface QuickCreateItem {
  key: QuickCreateKey;
  icon: ReactNode;
  iconClassName: string;
}

const QUICK_CREATE_ITEMS: QuickCreateItem[] = [
  {
    key: 'offline',
    icon: <ArrowRightLeft size={18} strokeWidth={2} />,
    iconClassName: 'bg-[#eef2ff] text-[#6578df]',
  },
  {
    key: 'realtime',
    icon: <RadioTower size={18} strokeWidth={2} />,
    iconClassName: 'bg-[#eaf8ff] text-[#2698d8]',
  },
  {
    key: 'development',
    icon: <Braces size={18} strokeWidth={2} />,
    iconClassName: 'bg-[#fff0f3] text-[#e85a73]',
  },
  {
    key: 'workflow',
    icon: <Workflow size={18} strokeWidth={2} />,
    iconClassName: 'bg-[#fff8e8] text-[#b98924]',
  },
];

function QuickCreateCard({ item }: { item: QuickCreateItem }) {
  const intl = useIntl();
  const title = intl.formatMessage({
    id: `pages.home.quickCreate.${item.key}.title`,
  });
  const description = intl.formatMessage({
    id: `pages.home.quickCreate.${item.key}.description`,
  });

  return (
    <button
      type="button"
      onClick={() => history.push(`/create?type=${item.key}`)}
      className="group flex h-[72px] min-w-0 items-center gap-3 rounded-[16px] border border-[rgba(31,35,41,0.07)] bg-white/[0.94] px-4 text-left shadow-[0_2px_8px_rgba(31,35,41,0.035)] transition-[transform,border-color,box-shadow] duration-200 hover:-translate-y-px hover:border-[rgba(31,35,41,0.11)] hover:shadow-[0_8px_20px_rgba(31,35,41,0.065)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-200/70"
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] transition-transform duration-200 group-hover:scale-105 ${item.iconClassName}`}
      >
        {item.icon}
      </span>

      <span className="min-w-0 flex-1">
        <strong className="block truncate text-[14px] font-semibold leading-5 text-[#292c35]">
          {title}
        </strong>
        <span className="mt-0.5 block truncate text-[11px] leading-4 text-[#9498a1]">
          {description}
        </span>
      </span>

      <ChevronRight
        size={14}
        strokeWidth={1.8}
        className="shrink-0 -translate-x-1 text-[#a4a8b0] opacity-0 transition-[opacity,transform] duration-200 group-hover:translate-x-0 group-hover:opacity-100"
      />
    </button>
  );
}

export function QuickCreatePanel() {
  const intl = useIntl();

  return (
    <section className="px-1 pt-1">
      <h2 className="mb-3 text-[16px] font-semibold leading-6 tracking-[-0.2px] text-[#343842]">
        {intl.formatMessage({ id: 'pages.home.quickCreate.title' })}
      </h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 min-[1280px]:grid-cols-4">
        {QUICK_CREATE_ITEMS.map((item) => (
          <QuickCreateCard key={item.key} item={item} />
        ))}
      </div>
    </section>
  );
}
