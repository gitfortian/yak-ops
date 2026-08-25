import { ScreenRenderer } from '@/components/screen-engine';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { history } from '@umijs/max';
import { Button, Input, Popconfirm, message } from 'antd';
import { Copy, Eye, Monitor, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { DigitalScreenInstance, DigitalScreenStatus } from './model';
import {
  deleteDigitalScreen,
  duplicateDigitalScreen,
  fetchDigitalScreens,
} from './screen-service';

type StatusFilter = 'all' | DigitalScreenStatus;

const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16);

const DigitalScreenEmptyIllustration = () => (
  <svg
    width="392"
    height="244"
    viewBox="0 0 392 244"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    className="select-none"
  >
    <defs>
      <linearGradient id="ds-bg-panel" x1="92" y1="50" x2="300" y2="172">
        <stop stopColor="#FFFFFF" />
        <stop offset="1" stopColor="#F7F9FC" />
      </linearGradient>

      <linearGradient id="ds-pink" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#FFA3B6" />
        <stop offset="1" stopColor="#FE2C55" />
      </linearGradient>

      <linearGradient id="ds-blue" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#9AD9FF" />
        <stop offset="1" stopColor="#4D88FF" />
      </linearGradient>

      <linearGradient id="ds-cyan" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#9CEDE7" />
        <stop offset="1" stopColor="#2BB8A8" />
      </linearGradient>

      <linearGradient id="ds-violet" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#C9BCFF" />
        <stop offset="1" stopColor="#8B6EFF" />
      </linearGradient>

      <filter
        id="ds-main-shadow"
        x="52"
        y="24"
        width="288"
        height="180"
        filterUnits="userSpaceOnUse"
      >
        <feDropShadow
          dx="0"
          dy="14"
          stdDeviation="16"
          floodColor="#1F2937"
          floodOpacity="0.08"
        />
      </filter>

      <filter
        id="ds-card-shadow"
        x="0"
        y="0"
        width="392"
        height="244"
        filterUnits="userSpaceOnUse"
      >
        <feDropShadow
          dx="0"
          dy="7"
          stdDeviation="8"
          floodColor="#1F2937"
          floodOpacity="0.07"
        />
      </filter>
    </defs>

    {/* ambient dots */}
    <circle cx="62" cy="66" r="4" fill="#DDE7FF" />
    <circle cx="328" cy="64" r="4" fill="#FBDDE4" />
    <circle cx="74" cy="191" r="4" fill="#EAE1FF" />
    <circle cx="320" cy="190" r="4" fill="#DDF5EC" />
    <circle cx="105" cy="44" r="2.5" fill="#D9E8FF" />
    <circle cx="291" cy="44" r="2.5" fill="#FFE2E9" />

    {/* glow lines */}
    <path
      d="M56 107H70"
      stroke="#D8DEE8"
      strokeWidth="1.6"
      strokeLinecap="round"
    />
    <path
      d="M63 100V114"
      stroke="#D8DEE8"
      strokeWidth="1.6"
      strokeLinecap="round"
    />
    <path
      d="M322 107H336"
      stroke="#D8DEE8"
      strokeWidth="1.6"
      strokeLinecap="round"
    />
    <path
      d="M329 100V114"
      stroke="#D8DEE8"
      strokeWidth="1.6"
      strokeLinecap="round"
    />

    {/* main ultra-wide screen */}
    <g filter="url(#ds-main-shadow)">
      <rect
        x="84"
        y="44"
        width="224"
        height="128"
        rx="22"
        fill="url(#ds-bg-panel)"
      />
      <rect
        x="84.75"
        y="44.75"
        width="222.5"
        height="126.5"
        rx="21.25"
        stroke="#E6E9EF"
        strokeWidth="1.5"
      />

      {/* top header */}
      <circle cx="101" cy="61" r="3" fill="#FE2C55" fillOpacity="0.55" />
      <circle cx="112" cy="61" r="3" fill="#F5C451" fillOpacity="0.75" />
      <circle cx="123" cy="61" r="3" fill="#58C38E" fillOpacity="0.75" />
      <rect x="140" y="57.5" width="60" height="7" rx="3.5" fill="#E7EAF0" />
      <rect x="208" y="57.5" width="26" height="7" rx="3.5" fill="#F0F2F5" />
      <rect x="240" y="57.5" width="26" height="7" rx="3.5" fill="#F0F2F5" />

      {/* left data rail */}
      <rect x="99" y="79" width="44" height="78" rx="13" fill="#FBFCFD" stroke="#ECEFF3" />
      <rect x="108" y="90" width="18" height="5" rx="2.5" fill="#D8DDE5" />
      <rect x="108" y="101" width="28" height="7" rx="3.5" fill="url(#ds-pink)" />
      <rect x="108" y="119" width="26" height="6" rx="3" fill="#EEF2F7" />
      <rect x="108" y="130" width="20" height="6" rx="3" fill="#E4EAF2" />
      <rect x="108" y="141" width="24" height="6" rx="3" fill="#EEF2F7" />

      {/* center wide line chart */}
      <rect x="151" y="79" width="102" height="53" rx="14" fill="#FBFCFD" stroke="#ECEFF3" />
      <path d="M163 118H241" stroke="#EFF2F6" strokeWidth="1.2" strokeLinecap="round" />
      <path d="M163 106H241" stroke="#F2F4F7" strokeWidth="1.2" strokeLinecap="round" />
      <path
        d="M164 117C173 111 180 115 188 107C196 99 204 106 212 96C220 87 229 95 239 88"
        stroke="url(#ds-blue)"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="239" cy="88" r="3.5" fill="#4D88FF" />

      {/* bottom wide cards */}
      <rect x="151" y="140" width="48" height="17" rx="8.5" fill="#EEF8F7" />
      <rect x="204" y="140" width="49" height="17" rx="8.5" fill="#F0EEFF" />

      {/* right stacked cards */}
      <rect x="261" y="79" width="31" height="35" rx="10" fill="#FFF7F9" stroke="#F7E9ED" />
      <rect x="268" y="91" width="6" height="15" rx="3" fill="#FFD1DA" />
      <rect x="278" y="85" width="6" height="21" rx="3" fill="url(#ds-pink)" />

      <rect x="261" y="121" width="31" height="36" rx="10" fill="#F7FBFF" stroke="#E8F0FF" />
      <circle cx="276.5" cy="139" r="10.5" stroke="#D9E7FF" strokeWidth="6" />
      <path
        d="M276.5 128.5A10.5 10.5 0 1 1 269 147"
        stroke="url(#ds-blue)"
        strokeWidth="6"
        strokeLinecap="round"
      />
    </g>

    {/* left floating radar-ish widget */}
    <g filter="url(#ds-card-shadow)">
      <rect x="45" y="99" width="58" height="58" rx="16" fill="#FFFFFF" />
      <rect
        x="45.75"
        y="99.75"
        width="56.5"
        height="56.5"
        rx="15.25"
        stroke="#E6E9EF"
        strokeWidth="1.5"
      />
      <path
        d="M74 113L82 118L84 128L78 138L68 141L60 135L58 124L65 116Z"
        fill="#F7FBFF"
        stroke="#D9E8FF"
        strokeWidth="1.5"
      />
      <path
        d="M74 118L79 121L80.5 128L76.5 134L69.5 136L64 132L63 125.5L67.5 120Z"
        fill="url(#ds-cyan)"
        fillOpacity="0.16"
        stroke="url(#ds-cyan)"
        strokeWidth="1.5"
      />
      <circle cx="74" cy="128" r="2.5" fill="#2BB8A8" />
    </g>

    {/* right floating mini screen */}
    <g filter="url(#ds-card-shadow)">
      <rect x="309" y="93" width="40" height="64" rx="14" fill="#FFFFFF" />
      <rect
        x="309.75"
        y="93.75"
        width="38.5"
        height="62.5"
        rx="13.25"
        stroke="#E6E9EF"
        strokeWidth="1.5"
      />
      <rect x="319" y="104" width="20" height="4" rx="2" fill="#E7EAF0" />
      <path
        d="M318 141C322 136 326 142 330 136C334 130 337 134 340 126"
        stroke="url(#ds-violet)"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <circle cx="340" cy="126" r="2.6" fill="#8B6EFF" />
    </g>

    {/* bottom control bar */}
    <g filter="url(#ds-card-shadow)">
      <rect x="150" y="185" width="92" height="22" rx="11" fill="#FFFFFF" />
      <rect
        x="150.75"
        y="185.75"
        width="90.5"
        height="20.5"
        rx="10.25"
        stroke="#E6E9EF"
        strokeWidth="1.5"
      />
      <circle cx="164" cy="196" r="4" fill="#FE2C55" fillOpacity="0.72" />
      <rect x="173" y="193" width="24" height="6" rx="3" fill="#DCE1E8" />
      <rect x="201" y="193" width="30" height="6" rx="3" fill="#EEF2F6" />
    </g>
  </svg>
);


const DigitalScreenEmptyState = ({
  onCreate,
}: {
  onCreate: () => void;
}) => (
  <div className="flex min-h-[470px] flex-col items-center justify-center pb-8 pt-2 text-center">
    <DigitalScreenEmptyIllustration />

    <div className="mt-1 text-[15px] font-semibold leading-6 text-[#30333B]">
      还没有数字化大屏
    </div>

    <div className="mt-1.5 text-[12px] leading-5 text-[#8A9099]">
做一块更有氛围感的数据大屏，把重点信息直接铺开展示。
    </div>
  </div>
);

const DigitalScreenFilterEmptyState = ({
  onReset,
}: {
  onReset: () => void;
}) => (
  <div className="flex min-h-[420px] flex-col items-center justify-center pb-8 text-center">
    <div className="flex h-12 w-12 items-center justify-center rounded-[14px] bg-[#F5F6F7] text-[#98A2B3]">
      <Monitor size={22} strokeWidth={1.7} />
    </div>

    <div className="mt-3 text-[13px] font-medium text-[#667085]">
      没有匹配的大屏
    </div>

    <div className="mt-1 text-[12px] text-[#A3A8B0]">
      换个关键词或状态试试。
    </div>

    <Button
      type="link"
      onClick={onReset}
      className="mt-1 h-8 px-2 text-[12px]"
    >
      清空筛选
    </Button>
  </div>
);

export default function DigitalScreenListPage() {
  const [screens, setScreens] = useState<DigitalScreenInstance[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<StatusFilter>('all');

  const loadScreens = useCallback(async () => {
    setLoading(true);
    try {
      setScreens(await fetchDigitalScreens());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载数字化大屏失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadScreens();
  }, [loadScreens]);

  const filteredScreens = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return screens.filter((screen) => {
      if (status !== 'all' && screen.status !== status) return false;
      if (!value) return true;
      const template = resolveScreenTemplateById(screen.templateId);
      return [screen.name, screen.description, template?.name]
        .some((field) => String(field || '').toLowerCase().includes(value));
    });
  }, [keyword, screens, status]);

  const statusItems: Array<{ key: StatusFilter; label: string; count: number }> = [
    { key: 'all', label: '全部', count: screens.length },
    { key: 'draft', label: '草稿', count: screens.filter((item) => item.status === 'draft').length },
    { key: 'published', label: '已发布', count: screens.filter((item) => item.status === 'published').length },
  ];

  const handleDuplicate = async (screen: DigitalScreenInstance) => {
    try {
      const duplicated = await duplicateDigitalScreen(screen.id);
      message.success('已复制大屏');
      await loadScreens();
      history.push(`/digital-screen/${duplicated.id}/edit`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复制大屏失败');
    }
  };

  const handleDelete = async (screen: DigitalScreenInstance) => {
    try {
      await deleteDigitalScreen(screen.id);
      message.success('大屏已删除');
      await loadScreens();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除大屏失败');
    }
  };

  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#F6F7F8]">
      <div className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        <div className="flex items-center justify-between gap-6">
          <div className="text-[18px] font-semibold leading-7 text-[#161823]">
            数字化大屏
          </div>

          <Button
            type="primary"
            icon={<Plus size={15} />}
            onClick={() => history.push('/digital-screen/new')}
          >
            新建大屏
          </Button>
        </div>

        <div className="mt-4 flex min-h-[48px] flex-wrap items-center justify-between gap-3 border-b border-[#eceef1] pb-3">
          <div className="flex items-center gap-1">
            {statusItems.map((item) => {
              const active = status === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => setStatus(item.key)}
                  className={[
                    'h-8 rounded-[6px] border-0 px-3 text-[13px] transition-colors',
                    active
                      ? 'bg-[#f0f1f2] font-semibold text-[#161823]'
                      : 'bg-transparent text-[#8a9099] hover:bg-[#f7f8f9] hover:text-[#444950]',
                  ].join(' ')}
                >
                  {item.label}
                  <span className="ml-1 text-[11px] font-normal text-[#a3a8b0]">{item.count}</span>
                </button>
              );
            })}
          </div>

          <Input
            allowClear
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder="搜索大屏"
            className="w-[220px]"
            variant="filled"
          />
        </div>

        {loading && screens.length === 0 ? (
          <div className="flex min-h-[470px] items-center justify-center text-[13px] text-[#98A2B3]">
            正在加载数字化大屏...
          </div>
        ) : filteredScreens.length === 0 ? (
          keyword.trim() || status !== 'all' ? (
            <DigitalScreenFilterEmptyState
              onReset={() => {
                setKeyword('');
                setStatus('all');
              }}
            />
          ) : (
            <DigitalScreenEmptyState
              onCreate={() => history.push('/digital-screen/new')}
            />
          )
        ) : (
          <div className="grid grid-cols-1 gap-x-5 gap-y-6 pt-5 xl:grid-cols-2 2xl:grid-cols-3">
            {filteredScreens.map((screen) => {
              const template = resolveScreenTemplateById(screen.templateId);
              return (
                <article
                  key={screen.id}
                  className="group overflow-hidden rounded-[8px] border border-[#e7e9ec] bg-white transition-[border-color,background-color] hover:border-[#d8dce1]"
                >
                  <button
                    type="button"
                    onClick={() => history.push(`/digital-screen/${screen.id}/edit`)}
                    className="relative block w-full overflow-hidden border-0 bg-[#111827] p-0 text-left"
                  >
                    {template ? (
                      <ScreenRenderer template={template} className="pointer-events-none" />
                    ) : (
                      <div className="flex aspect-video items-center justify-center text-[12px] text-white/60">
                        模板不存在
                      </div>
                    )}
                    <div className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-hover:bg-black/20 group-hover:opacity-100">
                      <span className="flex h-8 items-center gap-1.5 rounded-[6px] bg-white/95 px-3 text-[12px] font-medium text-[#161823]">
                        <Pencil size={13} /> 编辑
                      </span>
                    </div>
                  </button>

                  <div className="px-4 py-3.5">
                    <div className="flex min-w-0 items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <button
                          type="button"
                          onClick={() => history.push(`/digital-screen/${screen.id}/edit`)}
                          className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold text-[#161823] hover:underline"
                        >
                          {screen.name}
                        </button>
                        <div className="mt-1 flex items-center gap-2 text-[11px] text-[#98a2b3]">
                          <span>{template?.name || '未知模板'}</span>
                          <span className="text-[#d7dade]">·</span>
                          <span>{formatTime(screen.updatedAt)}</span>
                        </div>
                      </div>
                      <span className={[
                        'shrink-0 rounded-[4px] px-2 py-1 text-[11px] font-medium',
                        screen.status === 'published'
                          ? 'bg-[#edf8f2] text-[#27845a]'
                          : 'bg-[#f4f5f6] text-[#7b818a]',
                      ].join(' ')}>
                        {screen.status === 'published' ? '已发布' : '草稿'}
                      </span>
                    </div>

                    <div className="mt-3 flex items-center justify-end gap-1 border-t border-[#f0f1f2] pt-2.5">
                      <Button
                        type="text"
                        size="small"
                        icon={<Eye size={13} />}
                        onClick={() => history.push(`/digital-screen/${screen.id}`)}
                      >
                        预览
                      </Button>
                      <Button
                        type="text"
                        size="small"
                        icon={<Copy size={13} />}
                        onClick={() => void handleDuplicate(screen)}
                      >
                        复制
                      </Button>
                      <Popconfirm
                        title="删除大屏"
                        description={`确定删除“${screen.name}”吗？`}
                        okText="删除"
                        cancelText="取消"
                        onConfirm={() => void handleDelete(screen)}
                      >
                        <Button type="text" size="small" danger icon={<Trash2 size={13} />}>
                          删除
                        </Button>
                      </Popconfirm>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}