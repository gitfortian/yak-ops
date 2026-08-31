import {
  pageMessages,
  type SecurityMessage,
} from '@/services/security/messages';
import { history } from '@umijs/max';
import { Bell, ChevronRight } from 'lucide-react';
import { useEffect, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';

interface NotificationState {
  items: SecurityMessage[];
  total: number;
  loading: boolean;
  failed: boolean;
}

const formatMessageDate = (value?: string) => {
  if (!value) return '--';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
};

function NotificationRow({ item }: { item: SecurityMessage }) {
  return (
    <button
      type="button"
      onClick={() => history.push('/system/messages')}
      className="group flex w-full items-start gap-2 border-0 bg-transparent py-3 text-left"
    >
      <span
        className={`mt-[7px] h-1 w-1 shrink-0 rounded-full ${
          item.status === 'UNREAD' ? 'bg-[#ff3657]' : 'bg-[#d7d9de]'
        }`}
      />
      <span className="min-w-0 flex-1">
        <strong
          className={`line-clamp-2 block text-[12px] leading-5 transition-colors group-hover:text-[#20232b] ${
            item.status === 'UNREAD'
              ? 'font-semibold text-[#353943]'
              : 'font-normal text-[#555a64]'
          }`}
        >
          {item.title}
        </strong>
        {item.summary ? (
          <span className="mt-0.5 block truncate text-[10px] leading-4 text-[#9a9ea6]">
            {item.summary}
          </span>
        ) : null}
      </span>
      <span className="shrink-0 pt-0.5 text-[10px] leading-5 text-[#a0a4ac]">
        {formatMessageDate(item.createTime)}
      </span>
    </button>
  );
}

export default function NotificationCenter() {
  const [state, setState] = useState<NotificationState>({
    items: [],
    total: 0,
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;

    pageMessages({ pageNum: 1, pageSize: 3 })
      .then((result) => {
        if (!active) return;
        setState({
          items: result.records || [],
          total: result.total || 0,
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setState({ items: [], total: 0, loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-2">
          <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
            通知
          </h2>
          {state.total > 0 ? (
            <span className="rounded-full bg-[#f4f5f7] px-2 py-0.5 text-[10px] text-[#8e939c]">
              {state.total}
            </span>
          ) : null}
        </div>

        <button
          type="button"
          onClick={() => history.push('/system/messages')}
          className="flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
        >
          查看更多
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      </header>

      <div className="mt-3 min-h-[126px]">
        {state.items.length > 0 ? (
          <div className="divide-y divide-[#f0f1f3]">
            {state.items.map((item) => (
              <NotificationRow key={item.id} item={item} />
            ))}
          </div>
        ) : state.loading || state.failed ? (
          <div className="flex min-h-[126px] items-center justify-center text-[11px] text-[#9da1a8]">
            {state.loading ? '通知加载中...' : '通知加载失败'}
          </div>
        ) : (
          <HomeEmptyState
            icon={Bell}
            title="暂无通知"
            size="small"
            className="min-h-[126px]"
          />
        )}
      </div>
    </section>
  );
}
