import { history } from '@umijs/max';
import {
  Cable,
  Code2,
  Database,
  LayoutDashboard,
  Server,
  Sparkles,
  Workflow,
  type LucideIcon,
} from 'lucide-react';

type ShortcutTone = 'blue' | 'pink' | 'purple' | 'yellow' | 'cyan' | 'slate';

interface Shortcut {
  title: string;
  description: string;
  path: string;
  icon: LucideIcon;
  tone: ShortcutTone;
}

const toneClasses: Record<ShortcutTone, { icon: string; back: string }> = {
  blue: {
    icon: 'bg-[#2f9bff] text-white',
    back: 'bg-[#dcedff]',
  },
  pink: {
    icon: 'bg-[#ff4f78] text-white',
    back: 'bg-[#ffe2e9]',
  },
  purple: {
    icon: 'bg-[#7a66ff] text-white',
    back: 'bg-[#e8e3ff]',
  },
  yellow: {
    icon: 'bg-[#ffcf43] text-white',
    back: 'bg-[#fff2bd]',
  },
  cyan: {
    icon: 'bg-[#49b8cf] text-white',
    back: 'bg-[#dff4f8]',
  },
  slate: {
    icon: 'bg-[#596579] text-white',
    back: 'bg-[#e8ebef]',
  },
};

const integrationShortcuts: Shortcut[] = [
  {
    title: '数据源管理',
    description: '统一管理数据库与外部系统连接',
    path: '/data-source',
    icon: Database,
    tone: 'blue',
  },
  {
    title: '离线同步',
    description: '配置来源、目标与字段映射',
    path: '/sync/batch-link-up',
    icon: Cable,
    tone: 'cyan',
  },
];

const workspaceShortcuts: Shortcut[] = [
  {
    title: '开发任务',
    description: 'SQL 开发、发布与运行管理',
    path: '/data-development',
    icon: Code2,
    tone: 'pink',
  },
  {
    title: '工作流',
    description: '编排任务依赖与调度执行',
    path: '/workflow/definitions',
    icon: Workflow,
    tone: 'purple',
  },
  {
    title: '仪表盘',
    description: '组织图表与数据分析视图',
    path: '/dashboard',
    icon: LayoutDashboard,
    tone: 'blue',
  },
  {
    title: '数据服务',
    description: '将数据能力发布为 API 服务',
    path: '/data-service',
    icon: Server,
    tone: 'yellow',
  },
];

const productAreas = ['数据集成', '数据开发', '工作流', '数据质量', '数据消费', '数据服务'];

const navigate = (path: string) => history.push(path);

function ShortcutCard({ shortcut }: { shortcut: Shortcut }) {
  const Icon = shortcut.icon;
  const tone = toneClasses[shortcut.tone];

  return (
    <button
      type="button"
      className="group flex min-h-[88px] w-full items-center rounded-[16px] border border-[rgba(22,24,35,0.065)] bg-white px-4 py-3 text-left transition-all duration-200 hover:-translate-y-px hover:border-[rgba(22,24,35,0.12)] hover:bg-[#fcfcfd]"
      onClick={() => navigate(shortcut.path)}
    >
      <span className="relative mr-5 h-[58px] w-[58px] shrink-0">
        <span
          aria-hidden="true"
          className={`absolute -right-2 -top-2 h-[58px] w-[58px] rounded-[15px] ${tone.back}`}
        />
        <span
          className={`relative flex h-[58px] w-[58px] items-center justify-center rounded-[15px] ${tone.icon}`}
        >
          <Icon size={28} strokeWidth={1.8} />
        </span>
      </span>

      <span className="min-w-0">
        <strong className="block truncate text-[15px] font-[650] leading-6 text-[#161823]">
          {shortcut.title}
        </strong>
        <span className="mt-1 block truncate text-[12px] leading-5 text-[rgba(22,24,35,0.5)]">
          {shortcut.description}
        </span>
      </span>
    </button>
  );
}

function PanelTitle({ children }: { children: string }) {
  return (
    <h2 className="m-0 text-[19px] font-[650] leading-7 tracking-[-0.2px] text-[#161823]">
      {children}
    </h2>
  );
}

export default function HomePage() {
  return (
    <div className="min-h-full bg-[#f7f8fa] px-5 py-5 lg:px-6 lg:py-6">
      <div className="mx-auto w-full max-w-[1540px]">
        <section className="relative overflow-hidden rounded-[18px] border border-[rgba(22,24,35,0.055)] bg-white px-6 py-6 lg:px-8">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-y-0 right-0 hidden w-[58%] lg:block"
            style={{
              background:
                'radial-gradient(circle at 72% 30%, rgba(47,155,255,.14), transparent 34%), radial-gradient(circle at 58% 76%, rgba(122,102,255,.09), transparent 38%), linear-gradient(110deg, rgba(255,255,255,0) 0%, rgba(244,248,255,.72) 56%, rgba(247,250,255,.95) 100%)',
            }}
          >
            <div className="absolute inset-0 opacity-[0.28] [background-image:linear-gradient(rgba(22,24,35,.08)_1px,transparent_1px),linear-gradient(90deg,rgba(22,24,35,.06)_1px,transparent_1px)] [background-size:28px_28px]" />
          </div>

          <div className="relative z-[1] flex min-h-[94px] items-center">
            <div className="flex h-[68px] w-[68px] shrink-0 items-center justify-center rounded-full bg-[#161823] text-white">
              <Sparkles size={30} strokeWidth={1.8} />
            </div>

            <div className="ml-5 min-w-0">
              <div className="flex flex-wrap items-center gap-2.5">
                <h1 className="m-0 text-[22px] font-[700] leading-8 tracking-[-0.4px] text-[#161823]">
                  Yak Ops
                </h1>
                <span className="rounded-[5px] bg-[#161823] px-2 py-0.5 text-[10px] font-medium tracking-wide text-white">
                  一体化
                </span>
              </div>
              <p className="mb-0 mt-1.5 text-[13px] leading-5 text-[rgba(22,24,35,0.52)]">
                数据工程工作台
              </p>
              <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11px] text-[rgba(22,24,35,0.56)]">
                {productAreas.map((area) => (
                  <span key={area}>{area}</span>
                ))}
              </div>
            </div>
          </div>
        </section>

        <div className="mt-4 grid gap-4 xl:grid-cols-[360px_minmax(0,1fr)]">
          <section className="rounded-[18px] border border-[rgba(22,24,35,0.065)] bg-white p-5 lg:p-6">
            <PanelTitle>数据接入</PanelTitle>
            <div className="mt-5 space-y-3">
              {integrationShortcuts.map((shortcut) => (
                <ShortcutCard key={shortcut.title} shortcut={shortcut} />
              ))}
            </div>
          </section>

          <section className="rounded-[18px] border border-[rgba(22,24,35,0.065)] bg-white p-5 lg:p-6">
            <PanelTitle>数据工作台</PanelTitle>
            <div className="mt-5 grid gap-3 md:grid-cols-2">
              {workspaceShortcuts.map((shortcut) => (
                <ShortcutCard key={shortcut.title} shortcut={shortcut} />
              ))}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
