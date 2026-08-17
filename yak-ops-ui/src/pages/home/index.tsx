import {
  ChevronRight,
  FileText,
  Image as ImageIcon,
  Play,
  UserRound,
  Video,
  WandSparkles,
} from 'lucide-react';
import type { ReactNode } from 'react';

/* =========================================================
 * Types
 * ========================================================= */

type IconTheme =
  | 'video'
  | 'image'
  | 'panorama'
  | 'article'
  | 'avatar'
  | 'workshop';

interface ActionItem {
  key: string;
  title: string;
  description: string;
  theme: IconTheme;
  icon: ReactNode;
}

/* =========================================================
 * Data
 * ========================================================= */

const createItems: ActionItem[] = [
  {
    key: 'avatar',
    title: 'AI分身',
    description: '创造陪伴用户的另一个“你”',
    theme: 'avatar',
    icon: <UserRound size={18} strokeWidth={2.2} />,
  },
  {
    key: 'workshop',
    title: 'AI工坊',
    description: '把好想法变成好玩法',
    theme: 'workshop',
    icon: <WandSparkles size={18} strokeWidth={2.2} />,
  },
];

const publishItems: ActionItem[] = [
  {
    key: 'video',
    title: '发布高清视频',
    description: '支持常用格式，推荐mp4',
    theme: 'video',
    icon: <Play size={18} fill="currentColor" strokeWidth={0} />,
  },
  {
    key: 'image',
    title: '发布图文',
    description: '支持常用图片格式，如png/jpg等',
    theme: 'image',
    icon: <ImageIcon size={18} strokeWidth={2.3} />,
  },
  {
    key: 'panorama',
    title: '发布全景视频',
    description: '推荐分辨率为4K及以上',
    theme: 'panorama',
    icon: <Video size={18} strokeWidth={2.3} />,
  },
  {
    key: 'article',
    title: '发布文章',
    description: '支持上传20000字文本和30个图片素材',
    theme: 'article',
    icon: <FileText size={18} strokeWidth={2.3} />,
  },
];

/* =========================================================
 * Icon Theme
 * ========================================================= */

const themeStyles: Record<
  IconTheme,
  {
    panel: string;
    core: string;
    glow: string;
    iconHover: string;
  }
> = {
  video: {
    panel: 'bg-gradient-to-b from-[#ff94a9] to-[#ffe9ed]',
    core: 'bg-[#fe2c55] shadow-[inset_0_-2px_2px_0_#ff5b6f]',
    glow: 'bg-[#ff58a9]',
    iconHover: 'group-hover:rotate-[360deg]',
  },

  image: {
    panel: 'bg-gradient-to-b from-[#79c8ff] to-[#e4f5ff]',
    core:
      'bg-gradient-to-br from-[#28b8ff] to-[#168cf4] shadow-[inset_0_-2px_2px_rgba(0,93,231,0.28)]',
    glow: 'bg-[#63e3ff]',
    iconHover:
      'group-hover:-translate-y-[1px] group-hover:rotate-[8deg]',
  },

  panorama: {
    panel: 'bg-gradient-to-b from-[#aaa1ff] to-[#eeebff]',
    core:
      'bg-gradient-to-br from-[#8374ff] to-[#6657f5] shadow-[inset_0_-2px_2px_rgba(71,54,228,0.32)]',
    glow: 'bg-[#c677ff]',
    iconHover: 'group-hover:rotate-[360deg]',
  },

  article: {
    panel: 'bg-gradient-to-b from-[#ffd65c] to-[#fff3c9]',
    core:
      'bg-gradient-to-br from-[#ffcb24] to-[#ffb900] shadow-[inset_0_-2px_2px_rgba(225,152,0,0.25)]',
    glow: 'bg-[#fff27d]',
    iconHover:
      'group-hover:-translate-y-[2px] group-hover:rotate-[-4deg]',
  },

  avatar: {
    panel: 'bg-gradient-to-b from-[#89d6f6] to-[#e6f7fe]',
    core:
      'bg-gradient-to-br from-[#3cc2ef] to-[#278ee6] shadow-[inset_0_-2px_2px_rgba(22,112,193,0.24)]',
    glow: 'bg-[#84eeff]',
    iconHover: 'group-hover:rotate-[12deg]',
  },

  workshop: {
    panel: 'bg-gradient-to-b from-[#a5ccff] to-[#eef6ff]',
    core:
      'bg-gradient-to-br from-[#6d9cff] to-[#6468f3] shadow-[inset_0_-2px_2px_rgba(60,73,214,0.25)]',
    glow: 'bg-[#a8c9ff]',
    iconHover: 'group-hover:rotate-[-15deg]',
  },
};

/* =========================================================
 * Layered Icon
 *
 * 对应原页面：
 *
 * 77 × 84 icon layer
 * ├── 60 × 75 后置倾斜玻璃板
 * └── 60 × 75 前置渐变玻璃板
 *      └── 32 × 32 核心 icon
 * ========================================================= */

interface LayeredIconProps {
  theme: IconTheme;
  icon: ReactNode;
}

function LayeredIcon({ theme, icon }: LayeredIconProps) {
  const styles = themeStyles[theme];

  return (
    <div
      className="
        pointer-events-none
        absolute
        -left-px
        -top-[5px]
        z-[1]
        h-[84px]
        w-[77px]
      "
    >
      {/* 后置倾斜玻璃层 */}
      <div
        className="
          absolute
          left-[3px]
          top-0
          h-[75px]
          w-[60px]
          rotate-[10deg]
          skew-x-[-1.54deg]
          rounded-[12px]
          border
          border-white
          bg-[#d6d7d9]/40
          backdrop-blur-[6px]
        "
      />

      {/* 前置渐变玻璃层 */}
      <div
        className={`
          absolute
          left-0
          top-[5px]
          flex
          h-[75px]
          w-[60px]
          items-center
          justify-center
          overflow-hidden
          rounded-[12px]
          border
          border-white
          backdrop-blur-[6px]
          ${styles.panel}
        `}
      >
        {/* 核心 32 × 32 */}
        <div
          className={`
            relative
            flex
            h-8
            w-8
            shrink-0
            items-center
            justify-center
            overflow-hidden
            rounded-lg
            text-white
            ${styles.core}
          `}
        >
          {/* 左上光斑 */}
          <span
            className={`
              absolute
              -left-[7px]
              -top-2
              h-[23px]
              w-[23px]
              rounded-full
              blur-[5px]
              transition-all
              duration-500
              ease-in-out
              group-hover:translate-x-[5px]
              group-hover:translate-y-[3px]
              ${styles.glow}
            `}
          />

          {/* 右下白色光斑 */}
          <span
            className="
              absolute
              left-[18px]
              top-[21px]
              h-[18px]
              w-[18px]
              rounded-full
              bg-white/50
              blur-[5px]
              transition-all
              duration-500
              ease-in-out
              group-hover:-translate-x-[4px]
              group-hover:-translate-y-[4px]
            "
          />

          {/* 白色图形 */}
          <span
            className={`
              relative
              z-[3]
              flex
              h-5
              w-5
              items-center
              justify-center
              text-white
              drop-shadow-[0_1px_1px_rgba(0,0,0,0.05)]
              transition-transform
              duration-500
              ease-in-out
              ${styles.iconHover}
            `}
          >
            {icon}
          </span>
        </div>
      </div>
    </div>
  );
}

/* =========================================================
 * Action Card
 * ========================================================= */

interface ActionCardProps {
  item: ActionItem;
  onClick?: () => void;
}

function ActionCard({ item, onClick }: ActionCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="
        group
        relative
        flex
        h-[75px]
        w-full
        items-center
        overflow-visible
        rounded-[18px]
        border
        border-[rgba(31,35,41,0.075)]
        bg-white/[0.96]
        pr-4
        text-left
        shadow-[0_5px_12px_rgba(31,35,41,0.055),0_1px_2px_rgba(31,35,41,0.025)]
        transition-[background-color,border-color,box-shadow]
        duration-200
        ease-out
        hover:border-[rgba(31,35,41,0.10)]
        hover:bg-white
        hover:shadow-[0_6px_15px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.025)]
      "
    >
      {/* Icon 占位 */}
      <div className="relative h-[75px] w-[77px] shrink-0">
        <LayeredIcon theme={item.theme} icon={item.icon} />
      </div>

      {/* Text */}
      <div className="ml-2 min-w-0">
        <div
          className="
            truncate
            text-[15px]
            font-semibold
            leading-[22px]
            text-[#292c35]
          "
        >
          {item.title}
        </div>

        <div
          className="
            mt-0.5
            truncate
            text-[13px]
            font-normal
            leading-5
            text-[#9498a1]
          "
        >
          {item.description}
        </div>
      </div>
    </button>
  );
}

/* =========================================================
 * Profile Stat
 * ========================================================= */

interface ProfileStatProps {
  label: string;
  value: number;
  arrow?: boolean;
}

function ProfileStat({
  label,
  value,
  arrow = false,
}: ProfileStatProps) {
  return (
    <button
      type="button"
      className="
        flex
        items-center
        border-0
        bg-transparent
        p-0
        text-sm
        leading-[22px]
        text-[#747983]
        transition-colors
        duration-200
        hover:text-[#292c35]
      "
    >
      <span>{label}</span>

      <strong className="ml-[5px] font-semibold text-[#282b34]">
        {value}
      </strong>

      {arrow && (
        <ChevronRight
          size={14}
          strokeWidth={1.8}
          className="ml-[3px] text-[#9599a2]"
        />
      )}
    </button>
  );
}

/* =========================================================
 * Section
 * ========================================================= */

interface SectionProps {
  title: string;
  children: ReactNode;
  className?: string;
}

function Section({
  title,
  children,
  className = '',
}: SectionProps) {
  return (
    <section
      className={`
        min-h-[258px]
        rounded-[22px]
        border
        border-[#f1f1f1]
        bg-white/[0.68]
        px-[22px]
        pb-6
        pt-6
        backdrop-blur-[8px]
        ${className}
      `}
    >
      <h2
        className="
          mb-5
          text-xl
          font-semibold
          leading-7
          tracking-[-0.35px]
          text-[#252832]
        "
      >
        {title}
      </h2>

      {children}
    </section>
  );
}

/* =========================================================
 * Page
 * ========================================================= */

export default function CreatorWorkbenchPage() {
  const handleAction = (key: string) => {
    console.log('creator action:', key);

    switch (key) {
      case 'avatar':
        // TODO AI 分身
        break;

      case 'workshop':
        // TODO AI 工坊
        break;

      case 'video':
        // TODO 发布高清视频
        break;

      case 'image':
        // TODO 发布图文
        break;

      case 'panorama':
        // TODO 发布全景视频
        break;

      case 'article':
        // TODO 发布文章
        break;

      default:
        break;
    }
  };

  return (
    <div
      className="
        relative
        min-h-screen
        w-full
        overflow-hidden
        bg-[#f7f8fa]
        text-[#242731]
      "
    >
      {/* =====================================================
          背景
      ====================================================== */}

      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        {/* 顶部基础背景 */}
        <div
          className="
            absolute
            inset-x-0
            top-0
            h-[170px]
            bg-gradient-to-b
            from-[#f9fafb]
            to-transparent
          "
        />

        {/* 右上浅蓝 */}
        <div
          className="
            absolute
            -right-[60px]
            -top-[180px]
            h-[420px]
            w-[76%]
            bg-[radial-gradient(ellipse_at_center,rgba(159,215,239,0.48)_0%,rgba(187,224,240,0.23)_45%,rgba(255,255,255,0)_74%)]
            blur-[12px]
          "
        />

        {/* 右下浅黄 */}
        <div
          className="
            absolute
            -bottom-[180px]
            -right-[160px]
            h-[420px]
            w-[620px]
            bg-[radial-gradient(circle_at_center,rgba(231,238,178,0.47),rgba(255,255,255,0)_70%)]
            blur-[16px]
          "
        />

        {/* 顶部细纹理 */}
        <div
          className="
            absolute
            right-0
            top-0
            h-[118px]
            w-[73%]
            opacity-[0.16]
            [background-image:repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0px,rgba(255,255,255,0.95)_1px,transparent_1px,transparent_5px)]
            [mask-image:linear-gradient(90deg,transparent_0%,rgba(0,0,0,0.2)_20%,rgba(0,0,0,1)_100%)]
          "
        />
      </div>

      {/* =====================================================
          Content
      ====================================================== */}

      <div className="relative z-10">
        {/* ===================================================
            Header
        ==================================================== */}

        <header className="flex h-[116px] items-center px-4">
          <div className="flex items-center">
            {/* Avatar */}
            <div
              className="
                flex
                h-[66px]
                w-[66px]
                shrink-0
                items-center
                justify-center
                overflow-hidden
                rounded-full
                border
                border-white/90
                bg-gradient-to-br
                from-[#dde6ef]
                via-[#a6c8e2]
                to-[#5e93d4]
                text-white/95
                shadow-[0_2px_4px_rgba(31,35,41,0.04)]
              "
            >
              <UserRound size={30} strokeWidth={1.5} />
            </div>

            {/* Info */}
            <div className="ml-4 min-w-0">
              {/* 第一行 */}
              <div className="flex min-h-[22px] items-center">
                <span
                  className="
                    whitespace-nowrap
                    text-sm
                    font-medium
                    leading-[22px]
                    text-[#252830]
                  "
                >
                  正函数
                </span>

                <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />

                <span
                  className="
                    whitespace-nowrap
                    text-sm
                    font-normal
                    leading-[22px]
                    text-[#777b84]
                  "
                >
                  抖音号：83644455250
                </span>

                <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />

                <span
                  className="
                    whitespace-nowrap
                    text-sm
                    font-normal
                    leading-[22px]
                    text-[#777b84]
                  "
                >
                  见路不走
                </span>
              </div>

              {/* 第二行 */}
              <div className="mt-2.5 flex items-center gap-[27px]">
                <ProfileStat
                  label="关注"
                  value={159}
                  arrow
                />

                <ProfileStat
                  label="粉丝"
                  value={10}
                  arrow
                />

                <ProfileStat
                  label="获赞"
                  value={1}
                />
              </div>
            </div>
          </div>
        </header>

        {/* ===================================================
            Main
        ==================================================== */}

        <main className="px-4 pb-4">
          <div
            className="
              grid
              grid-cols-1
              gap-4
              min-[901px]:grid-cols-[330px_minmax(0,1fr)]
              min-[1101px]:grid-cols-[398px_minmax(0,1fr)]
            "
          >
            {/* =================================================
                智能创作
            ================================================== */}

            <Section title="智能创作">
              <div className="grid gap-[13px]">
                {createItems.map((item) => (
                  <ActionCard
                    key={item.key}
                    item={item}
                    onClick={() => handleAction(item.key)}
                  />
                ))}
              </div>
            </Section>

            {/* =================================================
                作品发布
            ================================================== */}

            <Section
              title="作品发布"
              className="bg-white/[0.74]"
            >
              <div
                className="
                  grid
                  grid-cols-1
                  gap-x-[14px]
                  gap-y-[13px]
                  sm:grid-cols-2
                "
              >
                {publishItems.map((item) => (
                  <ActionCard
                    key={item.key}
                    item={item}
                    onClick={() => handleAction(item.key)}
                  />
                ))}
              </div>
            </Section>
          </div>
        </main>
      </div>
    </div>
  );
}