import YakButton from '@/components/YakButton';
import {
  Code2,
  Database,
  Network,
  Sparkles,
  TerminalSquare,
} from 'lucide-react';
import type { ReactNode } from 'react';

import JavaIcon from '../icon/JavaIcon';
import PythonIcon from '../icon/PythonIcon';
import type { DevelopmentNodeType } from '../types';

interface DevelopmentWelcomeProps {
  onCreateNode: (type: DevelopmentNodeType) => void;
}

interface QuickStartItem {
  type: DevelopmentNodeType;
  label: string;
  description: string;
  icon: ReactNode;
}

const QUICK_START_ITEMS: QuickStartItem[] = [
  {
    type: 'SQL',
    label: '新建 SQL',
    description: '编写、运行并调试 SQL',
    icon: <Code2 size={15} strokeWidth={1.8} />,
  },
  {
    type: 'PYTHON',
    label: '新建 Python',
    description: '创建 Python 开发任务',
    icon: <PythonIcon size={15} />,
  },
  {
    type: 'SHELL',
    label: '新建 Shell',
    description: '创建 Shell 脚本任务',
    icon: <TerminalSquare size={15} strokeWidth={1.8} />,
  },
  {
    type: 'JAVA',
    label: '新建 Java',
    description: '创建 Java 开发任务',
    icon: <JavaIcon size={15} />,
  },
  {
    type: 'DATASET',
    label: '新建数据集',
    description: '沉淀可复用的数据结果',
    icon: <Database size={15} strokeWidth={1.8} />,
  },
  {
    type: 'DATA_SERVICE',
    label: '新建数据服务',
    description: '把开发结果发布为数据服务',
    icon: <Network size={15} strokeWidth={1.8} />,
  },
];

const DevelopmentWelcome = ({ onCreateNode }: DevelopmentWelcomeProps) => (
  <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
    <div className="flex h-9 shrink-0 items-end border-b border-[#e4e7ec] bg-[#f5f5f6]">
      <div className="flex h-9 items-center gap-2 border-r border-[#e4e7ec] bg-white px-3.5 text-[12px] font-medium text-[#344054]">
        <Sparkles size={13} className="text-[#fe2c55]" />
        欢迎
      </div>
    </div>

    <div className="relative min-h-0 flex-1 overflow-auto bg-white">
      <div className="mx-auto grid w-full max-w-[960px] grid-cols-1 gap-10 px-10 pb-16 pt-14 xl:grid-cols-[minmax(0,1.15fr)_minmax(280px,.85fr)] xl:gap-16 xl:px-14 xl:pt-[11vh]">
        <section>
          <div className="text-[30px] font-semibold tracking-[-0.02em] text-[#161823]">
            Yak Ops
          </div>
          <div className="mt-1 text-[18px] font-medium text-[#667085]">
            数据开发工作台
          </div>
          <p className="mt-4 max-w-[520px] text-[13px] leading-6 text-[#8b93a6]">
            从左侧打开开发节点，或创建一个任务开始编码。所有资源会在同一个工作台中以编辑器标签页打开。
          </p>

          <div className="mt-9 text-[13px] font-semibold text-[#344054]">快速开始</div>
          <div className="mt-3 grid max-w-[620px] grid-cols-1 gap-x-8 gap-y-1 lg:grid-cols-2">
            {QUICK_START_ITEMS.map((item) => (
              <YakButton
                key={item.type}
                type="text"
                htmlType="button"
                className="group !flex !h-auto !min-h-11 !w-full !items-center !justify-start gap-3 !rounded-md !px-2 !py-1.5 !text-left transition-colors hover:!bg-[#f7f8fa]"
                onClick={() => onCreateNode(item.type)}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#fff1f4] text-[#fe2c55] transition-colors group-hover:bg-[#ffe7ed]">
                  {item.icon}
                </span>
                <span className="min-w-0">
                  <span className="block text-[13px] font-medium text-[#344054] group-hover:text-[#161823]">
                    {item.label}
                  </span>
                  <span className="mt-0.5 block truncate text-[11px] text-[#98a2b3]">
                    {item.description}
                  </span>
                </span>
              </YakButton>
            ))}
          </div>
        </section>

        <aside className="pt-1">
          <div className="text-[13px] font-semibold text-[#344054]">工作台</div>
          <div className="mt-4 space-y-5 text-[12px] leading-5 text-[#667085]">
            <div>
              <div className="font-medium text-[#475467]">统一编辑</div>
              <div className="mt-1 text-[#98a2b3]">
                SQL、Python、Shell、Java、数据集和数据服务在同一开发目录中管理。
              </div>
            </div>
            <div>
              <div className="font-medium text-[#475467]">多标签工作区</div>
              <div className="mt-1 text-[#98a2b3]">
                打开的资源会保留为编辑器标签，可以像 IDE 一样连续切换和开发。
              </div>
            </div>
            <div>
              <div className="font-medium text-[#475467]">从目录开始</div>
              <div className="mt-1 text-[#98a2b3]">
                也可以直接从左侧开发目录选择已有节点，进入对应编辑器继续工作。
              </div>
            </div>
          </div>
        </aside>
      </div>

      <div className="absolute inset-x-0 bottom-0 flex h-6 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#8b93a6]">
        <span>Yak Ops · Data Development</span>
        <span>就绪</span>
      </div>
    </div>
  </main>
);

export default DevelopmentWelcome;
