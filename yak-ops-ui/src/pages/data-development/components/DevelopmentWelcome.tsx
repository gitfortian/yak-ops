import { Code2 } from 'lucide-react';

import type { DevelopmentNodeType } from '../types';

interface DevelopmentWelcomeProps {
  onCreateNode: (type: DevelopmentNodeType) => void;
}

interface ShortcutItem {
  label: string;
  keys: string;
}

const SHORTCUT_ITEMS: ShortcutItem[] = [
  {
    label: '保存',
    keys: 'Ctrl + S',
  },
  {
    label: '运行',
    keys: 'Ctrl + Enter',
  },
  {
    label: '快速打开',
    keys: 'Ctrl + P',
  },
  {
    label: '关闭标签',
    keys: 'Ctrl + W',
  },
];

const DevelopmentIllustration = () => (
  <svg
    width="200"
    height="150"
    viewBox="0 0 220 160"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    {/* 左上角强调符号 */}
    <circle cx="57" cy="34" r="20" fill="#FFE8EE" />

    <path
      d="M57 24V44M47 34H67"
      stroke="#FE2C55"
      strokeWidth="4"
      strokeLinecap="round"
    />

    {/* 编辑器主体 */}
    <rect
      x="61"
      y="42"
      width="108"
      height="76"
      rx="4"
      fill="white"
      stroke="#515151"
      strokeWidth="1.5"
    />

    {/* 编辑器顶部栏 */}
    <path
      d="M61 57H169"
      stroke="#515151"
      strokeWidth="1.5"
    />

    <circle cx="70" cy="50" r="2" fill="#C6CACD" />
    <circle cx="78" cy="50" r="2" fill="#C6CACD" />
    <circle cx="86" cy="50" r="2" fill="#C6CACD" />

    {/* 左侧编辑器导航 */}
    <rect
      x="69"
      y="65"
      width="18"
      height="45"
      rx="2"
      fill="#F1F2F3"
    />

    <path
      d="M74 73H82"
      stroke="#9CA3AA"
      strokeWidth="1.5"
      strokeLinecap="round"
    />

    <path
      d="M74 81H80"
      stroke="#9CA3AA"
      strokeWidth="1.5"
      strokeLinecap="round"
    />

    <path
      d="M74 89H83"
      stroke="#9CA3AA"
      strokeWidth="1.5"
      strokeLinecap="round"
    />

    {/* SQL 编辑区域 */}
    <path
      d="M97 70H139"
      stroke="#515151"
      strokeWidth="2"
      strokeLinecap="round"
    />

    <path
      d="M97 79H151"
      stroke="#C6CACD"
      strokeWidth="2"
      strokeLinecap="round"
    />

    <path
      d="M97 88H131"
      stroke="#C6CACD"
      strokeWidth="2"
      strokeLinecap="round"
    />

    <path
      d="M97 97H145"
      stroke="#C6CACD"
      strokeWidth="2"
      strokeLinecap="round"
    />

    <path
      d="M97 106H120"
      stroke="#C6CACD"
      strokeWidth="2"
      strokeLinecap="round"
    />

    {/* 编辑器底座 */}
    <path
      d="M50 118H177L166 133H39L50 118Z"
      fill="#E7E9EB"
      stroke="#515151"
      strokeWidth="1.5"
      strokeLinejoin="round"
    />

    {/* 数据库 */}
    <ellipse
      cx="172"
      cy="101"
      rx="18"
      ry="7"
      fill="white"
      stroke="#515151"
      strokeWidth="1.5"
    />

    <path
      d="M154 101V122C154 126 162 129 172 129C182 129 190 126 190 122V101"
      fill="white"
    />

    <path
      d="M154 101V122C154 126 162 129 172 129C182 129 190 126 190 122V101"
      stroke="#515151"
      strokeWidth="1.5"
    />

    <path
      d="M154 111C154 115 162 118 172 118C182 118 190 115 190 111"
      stroke="#C6CACD"
      strokeWidth="1.5"
    />

    {/* 左侧人物 */}
    <circle
      cx="42"
      cy="95"
      r="8"
      fill="white"
      stroke="#515151"
      strokeWidth="1.5"
    />

    <path
      d="M33 111C34 103 38 99 44 99C51 99 55 105 55 113V129H30C30 122 31 116 33 111Z"
      fill="#515151"
    />

    {/* 人物手臂 */}
    <path
      d="M50 106C55 109 59 111 67 111"
      stroke="#515151"
      strokeWidth="2"
      strokeLinecap="round"
    />

    {/* 小鼠标指针 */}
    <path
      d="M143 68L151 72L147 74L145 79L143 68Z"
      fill="#515151"
    />
  </svg>
);

const DevelopmentWelcome = (_: DevelopmentWelcomeProps) => (
  <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
    {/* 编辑器标签 */}
    <div className="flex h-9 shrink-0 items-end border-b border-[#e4e7ec] bg-[#f7f8fa]">
      <div className="flex h-9 items-center gap-2 border-r border-[#e4e7ec] bg-white px-3.5 text-[12px] font-medium text-[#344054]">
        <Code2
          size={13}
          strokeWidth={1.7}
          className="text-[#98a2b3]"
        />
        欢迎
      </div>
    </div>

    {/* 欢迎内容 */}
    <div className="relative flex min-h-0 flex-1 items-center justify-center overflow-auto bg-white pb-10">
      <div className="flex w-[360px] -translate-y-6 flex-col items-center">
        {/* 插画 */}
        <DevelopmentIllustration />

        {/* 标题 */}
        <div className="mt-1 text-[14px] font-medium text-[#515151]">
          数据开发工作台
        </div>

        {/* 快捷键 */}
        <div className="mt-7 w-[280px]">
          {SHORTCUT_ITEMS.map((item) => (
            <div
              key={item.keys}
              className="
                flex
                h-9
                items-center
                justify-between
                px-1
                text-[12px]
              "
            >
              <span className="text-[#667085]">
                {item.label}
              </span>

              <kbd
                className="
                  min-w-[72px]
                  whitespace-nowrap
                  rounded-[4px]
                  border
                  border-[#e4e7ec]
                  bg-[#fafafa]
                  px-2
                  py-[2px]
                  text-center
                  font-mono
                  text-[10px]
                  font-normal
                  leading-4
                  text-[#8b93a6]
                  shadow-[0_1px_1px_rgba(16,24,40,0.03)]
                "
              >
                {item.keys}
              </kbd>
            </div>
          ))}
        </div>
      </div>

      {/* 状态栏 */}
      <div
        className="
          absolute
          inset-x-0
          bottom-0
          flex
          h-6
          items-center
          justify-between
          border-t
          border-[#eef0f2]
          bg-[#fafafa]
          px-2.5
          text-[10px]
          text-[#98a2b3]
        "
      >
        <span>Yak Ops · Data Development</span>
        <span>就绪</span>
      </div>
    </div>
  </main>
);

export default DevelopmentWelcome;