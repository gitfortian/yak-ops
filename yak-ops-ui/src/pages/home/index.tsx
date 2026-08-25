import { fetchDataSourceSummary } from "@/pages/data-source/service";
import { history } from "@umijs/max";
import {
  ArrowRightLeft,
  BarChart3,
  ChevronRight,
  Code2,
  Database,
  ShieldCheck,
  Workflow,
} from "lucide-react";
import { type ReactNode, useEffect, useRef, useState } from "react";
import DataCenter from "./DataCenter";
import HomeWorkbench from "./HomeWorkbench";
import ScheduleCenter from "./ScheduleCenter";
import { homeDataCenterApi } from "./service";

/* =========================================================
 * Types
 * ========================================================= */

type IconTheme =
  | "video"
  | "image"
  | "panorama"
  | "article"
  | "avatar"
  | "workshop";

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
    key: "data-source",
    title: "数据源管理",
    description: "连接和管理你的数据源",
    theme: "avatar",
    icon: <Database size={18} strokeWidth={2.1} />,
  },
  {
    key: "offline-sync",
    title: "离线同步",
    description: "创建和管理离线同步任务",
    theme: "workshop",
    icon: <ArrowRightLeft size={18} strokeWidth={2.1} />,
  },
];

const publishItems: ActionItem[] = [
  {
    key: "development",
    title: "数据开发",
    description: "编写、调试和发布数据任务",
    theme: "video",
    icon: <Code2 size={18} strokeWidth={2.1} />,
  },
  {
    key: "workflow",
    title: "工作流",
    description: "编排任务并配置运行调度",
    theme: "image",
    icon: <Workflow size={18} strokeWidth={2.1} />,
  },
  {
    key: "quality",
    title: "数据质量",
    description: "监控并检查数据质量",
    theme: "panorama",
    icon: <ShieldCheck size={18} strokeWidth={2.1} />,
  },
  {
    key: "application",
    title: "数据应用",
    description: "分析、共享和服务数据",
    theme: "article",
    icon: <BarChart3 size={18} strokeWidth={2.1} />,
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
  }
> = {
  video: {
    panel: "bg-[linear-gradient(180deg,#ff8ea5_0%,#ffd4dd_54%,#fff0f3_100%)]",
    core: "bg-[linear-gradient(145deg,#ff4768_0%,#fe2c55_100%)] shadow-[inset_0_-1px_2px_rgba(188,21,56,0.22)]",
    glow: "bg-[#ff8ea5]",
  },
  image: {
    panel: "bg-[linear-gradient(180deg,#76c8ff_0%,#caebff_54%,#edf8ff_100%)]",
    core: "bg-[linear-gradient(145deg,#32bfff_0%,#168cf4_100%)] shadow-[inset_0_-1px_2px_rgba(0,93,231,0.22)]",
    glow: "bg-[#78d9ff]",
  },
  panorama: {
    panel: "bg-[linear-gradient(180deg,#a89aff_0%,#d7d1ff_54%,#f1efff_100%)]",
    core: "bg-[linear-gradient(145deg,#8a78ff_0%,#6757f5_100%)] shadow-[inset_0_-1px_2px_rgba(71,54,228,0.24)]",
    glow: "bg-[#b6a9ff]",
  },
  article: {
    panel: "bg-[linear-gradient(180deg,#ffd657_0%,#ffebb0_54%,#fff8e2_100%)]",
    core: "bg-[linear-gradient(145deg,#ffd23c_0%,#ffb900_100%)] shadow-[inset_0_-1px_2px_rgba(225,152,0,0.20)]",
    glow: "bg-[#ffe27d]",
  },
  avatar: {
    panel: "bg-[linear-gradient(180deg,#77cff3_0%,#c9ecfa_54%,#edf9ff_100%)]",
    core: "bg-[linear-gradient(145deg,#3bc4ef_0%,#278ee6_100%)] shadow-[inset_0_-1px_2px_rgba(22,112,193,0.20)]",
    glow: "bg-[#8fe8ff]",
  },
  workshop: {
    panel: "bg-[linear-gradient(180deg,#99c8ff_0%,#d3e6ff_54%,#f0f6ff_100%)]",
    core: "bg-[linear-gradient(145deg,#729eff_0%,#6267f2_100%)] shadow-[inset_0_-1px_2px_rgba(60,73,214,0.22)]",
    glow: "bg-[#abc9ff]",
  },
};

/* =========================================================
 * Layered Icon
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
        pointer-events-none absolute -left-px -top-[2px] z-[3]
        h-[80px] w-[76px]
        origin-center
        transition-transform duration-[320ms]
        ease-[cubic-bezier(0.22,1,0.36,1)]
        group-hover:-translate-y-[1px]
        group-hover:scale-[1.045]
      "
    >
      {/* 后层玻璃片 */}
      {/* 后层玻璃片 */}
<div
  className="
    absolute left-[2px] top-[8px]
    h-[72px] w-[59px]

    origin-bottom-right
    rotate-[20deg]
    rounded-[12px]

    border border-white/80
    bg-[#d7d9df]/40
    shadow-[0_4px_12px_rgba(31,35,41,0.035)]
    backdrop-blur-[4px]

    transition-[transform,border-width,border-color]
    duration-[360ms]
    ease-[cubic-bezier(0.34,1.56,0.64,1)]

    group-hover:rotate-[26deg]
    group-hover:border-2
    group-hover:border-white
  "
/>

      {/* 前层彩色面板 */}
      <div
        className={`
          absolute left-0 top-[4px]
          flex h-[72px] w-[61px]
          items-center justify-center
          overflow-hidden
          rounded-[12px]
          border border-white/90
          shadow-[0_7px_16px_rgba(31,35,41,0.09)]
          ${styles.panel}
        `}
      >
        {/* 很轻的顶部高光 */}
        <span
          className="
            absolute inset-x-[5px] top-[2px]
            h-[20px]
            rounded-full
            bg-white/25
            blur-[8px]
          "
        />

        {/* 中心图标 */}
        <div
          className={`
            relative flex h-[34px] w-[34px]
            shrink-0 items-center justify-center
            overflow-hidden rounded-[9px]
            text-white
            transition-transform duration-[320ms]
            ease-[cubic-bezier(0.22,1,0.36,1)]
            group-hover:scale-[1.035]
            ${styles.core}
          `}
        >
          {/* 左上极弱彩光 */}
          <span
            className={`
              absolute -left-[7px] -top-[7px]
              h-[19px] w-[19px]
              rounded-full
              opacity-60 blur-[6px]
              ${styles.glow}
            `}
          />

          {/* 右下白色柔光 */}
          <span
            className="
              absolute -bottom-[7px] -right-[6px]
              h-[18px] w-[18px]
              rounded-full
              bg-white/40
              blur-[6px]
            "
          />

          <span
            className="
              relative z-[2]
              flex h-5 w-5
              items-center justify-center
              drop-shadow-[0_1px_1px_rgba(0,0,0,0.06)]
            "
          >
            {icon}
          </span>
        </div>
      </div>
    </div>
  );
}

/* =========================================================
 * Pixel Card
 * ========================================================= */

class Pixel {
  width: number;
  height: number;
  ctx: CanvasRenderingContext2D;
  x: number;
  y: number;
  color: string;
  speed: number;
  size: number;
  sizeStep: number;
  minSize: number;
  maxSizeInteger: number;
  maxSize: number;
  delay: number;
  counter: number;
  counterStep: number;
  isIdle: boolean;
  isReverse: boolean;
  isShimmer: boolean;

  constructor(
    canvas: HTMLCanvasElement,
    context: CanvasRenderingContext2D,
    x: number,
    y: number,
    color: string,
    speed: number,
    delay: number
  ) {
    this.width = canvas.width;
    this.height = canvas.height;
    this.ctx = context;
    this.x = x;
    this.y = y;
    this.color = color;
    this.speed = this.getRandomValue(0.1, 0.9) * speed;
    this.size = 0;
    this.sizeStep = Math.random() * 0.4;
    this.minSize = 0.5;
    this.maxSizeInteger = 2;
    this.maxSize = this.getRandomValue(this.minSize, this.maxSizeInteger);
    this.delay = delay;
    this.counter = 0;
    this.counterStep = Math.random() * 4 + (this.width + this.height) * 0.01;
    this.isIdle = false;
    this.isReverse = false;
    this.isShimmer = false;
  }

  getRandomValue(min: number, max: number) {
    return Math.random() * (max - min) + min;
  }

  draw() {
    const centerOffset = this.maxSizeInteger * 0.5 - this.size * 0.5;
    this.ctx.fillStyle = this.color;
    this.ctx.fillRect(
      this.x + centerOffset,
      this.y + centerOffset,
      this.size,
      this.size
    );
  }

  appear() {
    this.isIdle = false;

    if (this.counter <= this.delay) {
      this.counter += this.counterStep;
      return;
    }

    if (this.size >= this.maxSize) {
      this.isShimmer = true;
    }

    if (this.isShimmer) {
      this.shimmer();
    } else {
      this.size += this.sizeStep;
    }

    this.draw();
  }

  disappear() {
    this.isShimmer = false;
    this.counter = 0;

    if (this.size <= 0) {
      this.size = 0;
      this.isIdle = true;
      return;
    }

    this.size -= 0.1;
    this.draw();
  }

  shimmer() {
    if (this.size >= this.maxSize) {
      this.isReverse = true;
    } else if (this.size <= this.minSize) {
      this.isReverse = false;
    }

    if (this.isReverse) {
      this.size -= this.speed;
    } else {
      this.size += this.speed;
    }
  }
}

function getEffectiveSpeed(value: number, reducedMotion: boolean) {
  const min = 0;
  const max = 100;
  const throttle = 0.001;

  if (value <= min || reducedMotion) {
    return min;
  }

  if (value >= max) {
    return max * throttle;
  }

  return value * throttle;
}

const PIXEL_VARIANTS = {
  default: {
    gap: 5,
    speed: 35,
    colors: "#f8fafc,#f1f5f9,#cbd5e1",
    noFocus: false,
  },
  blue: {
    gap: 10,
    speed: 25,
    colors: "#e0f2fe,#7dd3fc,#0ea5e9",
    noFocus: false,
  },
  yellow: {
    gap: 3,
    speed: 20,
    colors: "#fef08a,#fde047,#eab308",
    noFocus: false,
  },
  pink: {
    gap: 6,
    speed: 80,
    colors: "#fecdd3,#fda4af,#e11d48",
    noFocus: true,
  },
} as const;

type PixelVariant = keyof typeof PIXEL_VARIANTS;
type PixelAnimationName = "appear" | "disappear";

interface PixelCardProps {
  variant?: PixelVariant;
  gap?: number;
  speed?: number;
  colors?: string;
  noFocus?: boolean;
  className?: string;
  children: ReactNode;
  onClick?: () => void;
}

function PixelCard({
  variant = "default",
  gap,
  speed,
  colors,
  noFocus,
  className = "",
  children,
  onClick,
}: PixelCardProps) {
  const containerRef = useRef<HTMLButtonElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pixelsRef = useRef<Pixel[]>([]);
  const animationRef = useRef<number | null>(null);
  const timePreviousRef = useRef(
    typeof performance !== "undefined" ? performance.now() : 0
  );
  const reducedMotionRef = useRef(
    typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );

  const variantConfig = PIXEL_VARIANTS[variant];
  const finalGap = gap ?? variantConfig.gap;
  const finalSpeed = speed ?? variantConfig.speed;
  const finalColors = colors ?? variantConfig.colors;
  const finalNoFocus = noFocus ?? variantConfig.noFocus;

  const initPixels = () => {
    if (!containerRef.current || !canvasRef.current) return;

    const rect = containerRef.current.getBoundingClientRect();
    const width = Math.floor(rect.width);
    const height = Math.floor(rect.height);
    const ctx = canvasRef.current.getContext("2d");

    if (!ctx || width <= 0 || height <= 0) return;

    canvasRef.current.width = width;
    canvasRef.current.height = height;
    canvasRef.current.style.width = `${width}px`;
    canvasRef.current.style.height = `${height}px`;

    const colorsArray = finalColors
      .split(",")
      .map((color) => color.trim())
      .filter(Boolean);
    const pixelGap = Math.max(1, Math.floor(finalGap));
    const pixels: Pixel[] = [];

    for (let x = 0; x < width; x += pixelGap) {
      for (let y = 0; y < height; y += pixelGap) {
        const color =
          colorsArray[Math.floor(Math.random() * colorsArray.length)] ||
          "#cbd5e1";
        const dx = x - width / 2;
        const dy = y - height / 2;
        const distance = Math.sqrt(dx * dx + dy * dy);
        const delay = reducedMotionRef.current ? 0 : distance;

        pixels.push(
          new Pixel(
            canvasRef.current,
            ctx,
            x,
            y,
            color,
            getEffectiveSpeed(finalSpeed, reducedMotionRef.current),
            delay
          )
        );
      }
    }

    pixelsRef.current = pixels;
  };

  const doAnimate = (animationName: PixelAnimationName) => {
    animationRef.current = requestAnimationFrame(() =>
      doAnimate(animationName)
    );

    const timeNow = performance.now();
    const timePassed = timeNow - timePreviousRef.current;
    const timeInterval = 1000 / 60;

    if (timePassed < timeInterval) return;

    timePreviousRef.current = timeNow - (timePassed % timeInterval);

    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");

    if (!canvas || !ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    let allIdle = true;

    for (const pixel of pixelsRef.current) {
      pixel[animationName]();

      if (!pixel.isIdle) {
        allIdle = false;
      }
    }

    if (animationName === "disappear" && allIdle) {
      if (animationRef.current !== null) {
        cancelAnimationFrame(animationRef.current);
      }
      animationRef.current = null;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    }
  };

  const handleAnimation = (animationName: PixelAnimationName) => {
    if (animationRef.current !== null) {
      cancelAnimationFrame(animationRef.current);
    }

    timePreviousRef.current = performance.now();
    animationRef.current = requestAnimationFrame(() =>
      doAnimate(animationName)
    );
  };

  useEffect(() => {
    initPixels();

    const observer = new ResizeObserver(() => {
      initPixels();
    });

    const container = containerRef.current;
    if (container) {
      observer.observe(container);
    }

    return () => {
      observer.disconnect();

      if (animationRef.current !== null) {
        cancelAnimationFrame(animationRef.current);
      }
    };
    // initPixels 依赖最终配置；这些值变化时重新生成粒子即可。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [finalGap, finalSpeed, finalColors]);

  return (
    <button
      ref={containerRef}
      type="button"
      onClick={onClick}
      onMouseEnter={() => handleAnimation("appear")}
      onMouseLeave={() => handleAnimation("disappear")}
      onFocus={
        finalNoFocus
          ? undefined
          : (event) => {
              if (event.currentTarget.contains(event.relatedTarget)) return;
              handleAnimation("appear");
            }
      }
      onBlur={
        finalNoFocus
          ? undefined
          : (event) => {
              if (event.currentTarget.contains(event.relatedTarget)) return;
              handleAnimation("disappear");
            }
      }
      tabIndex={finalNoFocus ? -1 : 0}
      className={className}
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-0 overflow-hidden rounded-[16px]"
      >
        <canvas ref={canvasRef} className="block h-full w-full" />

        {/* 右侧保持一点白色空气感，避免粒子盖住文案 */}
        <span
          className="
            absolute inset-0
            bg-[linear-gradient(90deg,transparent_0%,rgba(255,255,255,0.02)_45%,rgba(255,255,255,0.76)_100%)]
          "
        />
      </span>

      {children}
    </button>
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
    <PixelCard
      onClick={onClick}
      gap={6}
      speed={35}
      colors="#e2e8f0,#cbd5e1,#94a3b8"
      className="
        group relative flex h-[75px] w-full
        items-center overflow-visible
        rounded-[16px]
        border border-[rgba(31,35,41,0.075)]
        bg-white/[0.96]
        pr-4 text-left
        shadow-[0_3px_10px_rgba(31,35,41,0.045),0_1px_2px_rgba(31,35,41,0.025)]
        transition-[border-color,box-shadow,transform]
        duration-[260ms]
        ease-[cubic-bezier(0.22,1,0.36,1)]
        hover:border-[rgba(31,35,41,0.09)]
        hover:shadow-[0_7px_18px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.025)]
        focus-visible:outline-none
        focus-visible:ring-2
        focus-visible:ring-sky-200/70
      "
    >
      {/* 左侧图标 */}
      <div className="relative z-[2] h-[75px] w-[77px] shrink-0">
        <LayeredIcon theme={item.theme} icon={item.icon} />
      </div>

      {/* 文本 */}
      <div className="relative z-[2] ml-2 min-w-0 flex-1">
        <div
          className="
            truncate
            text-[15px] font-semibold
            leading-[22px]
            text-[#292c35]
          "
        >
          {item.title}
        </div>

        <div
          className="
            mt-0.5 flex min-w-0
            items-center
            text-[13px] font-normal
            leading-5 text-[#9498a1]
          "
        >
          <span className="min-w-0 truncate">{item.description}</span>

          <ChevronRight
            size={13}
            strokeWidth={1.8}
            className="
              ml-[1px] shrink-0
              -translate-x-[3px]
              text-[#92969f]
              opacity-0
              transition-[opacity,transform]
              duration-[220ms]
              ease-out
              group-hover:translate-x-0
              group-hover:opacity-100
            "
          />
        </div>
      </div>
    </PixelCard>
  );
}

/* =========================================================
 * Profile Stat
 * ========================================================= */

interface ProfileStatProps {
  label: string;
  value: number;
  arrow?: boolean;
  onClick?: () => void;
}

function ProfileStat({
  label,
  value,
  arrow = false,
  onClick,
}: ProfileStatProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="
        flex items-center
        border-0 bg-transparent p-0
        text-sm leading-[22px]
        text-[#747983]
        transition-colors duration-200
        hover:text-[#292c35]
      "
    >
      <span>{label}</span>

      <strong className="ml-[5px] font-semibold text-[#282b34]">{value}</strong>

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

function Section({ title, children, className = "" }: SectionProps) {
  return (
    <section
      className={`
        min-h-[258px]
        rounded-[22px]
        border border-[#f1f1f1]
        bg-white/[0.70]
        px-[22px] pb-6 pt-6
        backdrop-blur-[8px]
        ${className}
      `}
    >
      <h2
        className="
          mb-5
          text-xl font-semibold
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
 * Home secondary panels
 * ========================================================= */

function HomeSecondaryPanels() {
  return (
    <div
      className="
        mt-4 grid grid-cols-1 gap-4
        xl:grid-cols-[minmax(0,1fr)_410px]
      "
    >
      <DataCenter />
      <ScheduleCenter />
    </div>
  );
}

/* =========================================================
 * Background atmosphere
 * ========================================================= */

/**
 * 参考抖音创作者中心：
 *
 * 不是在 Header 上覆盖一整层毛玻璃，
 * 而是在页面背景中放置一条很淡的蓝白氛围带。
 *
 * 特征：
 * 1. 中部有柔和蓝白高光；
 * 2. 两侧自然消失；
 * 3. 有极淡点阵纹理；
 * 4. 不覆盖正文与卡片，不影响文字清晰度。
 */
function AtmosphereBand({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`
        absolute overflow-hidden
        rounded-[999px]
        bg-[linear-gradient(90deg,rgba(247,248,250,0)_0%,rgba(245,248,251,0.72)_15%,rgba(230,240,246,0.90)_50%,rgba(245,248,251,0.72)_85%,rgba(247,248,250,0)_100%)]
        ${className}
      `}
    >
      {/* 点阵 */}
      <div
        className="
          absolute inset-0
          opacity-[0.30]
          [background-image:radial-gradient(circle,rgba(255,255,255,0.95)_0.8px,transparent_0.9px)]
          [background-size:7px_7px]
          [mask-image:linear-gradient(90deg,transparent_0%,#000_15%,#000_85%,transparent_100%)]
        "
      />

      {/* 中间蓝白光 */}
      <div
        className="
          absolute left-1/2 top-1/2
          h-[170%] w-[42%]
          -translate-x-1/2 -translate-y-1/2

          bg-[radial-gradient(ellipse_at_center,rgba(186,220,237,0.57)_0%,rgba(210,231,241,0.31)_45%,rgba(255,255,255,0)_78%)]
          blur-[20px]
        "
      />

      {/* 上下弱白光 */}
      <div
        className="
          absolute inset-x-[12%] top-0
          h-[34%]
          bg-gradient-to-b
          from-white/60 to-transparent
          blur-[5px]
        "
      />

      {/* 左侧渐隐 */}
      <div
        className="
          absolute inset-y-0 left-0
          w-[18%]
          bg-gradient-to-r
          from-[#f7f8fa]
          via-[#f7f8fa]/55
          to-transparent
        "
      />

      {/* 右侧渐隐 */}
      <div
        className="
          absolute inset-y-0 right-0
          w-[18%]
          bg-gradient-to-l
          from-[#f7f8fa]
          via-[#f7f8fa]/55
          to-transparent
        "
      />
    </div>
  );
}

/* =========================================================
 * Page
 * ========================================================= */

export default function CreatorWorkbenchPage() {
  const [headerStats, setHeaderStats] = useState({
    dataSourceCount: 0,
    runningCount: 0,
    exceptionCount: 0,
  });

  useEffect(() => {
    let active = true;

    Promise.allSettled([
      fetchDataSourceSummary(),
      homeDataCenterApi.overview("7d"),
    ]).then(([dataSourceResult, overviewResult]) => {
      if (!active) return;

      setHeaderStats({
        dataSourceCount:
          dataSourceResult.status === "fulfilled"
            ? dataSourceResult.value.data?.total || 0
            : 0,

        runningCount:
          overviewResult.status === "fulfilled"
            ? overviewResult.value.data?.metrics?.runningCount || 0
            : 0,

        exceptionCount:
          overviewResult.status === "fulfilled"
            ? overviewResult.value.data?.metrics?.failedCount || 0
            : 0,
      });
    });

    return () => {
      active = false;
    };
  }, []);

  const handleAction = (key: string) => {
    const routes: Record<string, string> = {
      "data-source": "/data-source",
      "offline-sync": "/sync/batch-link-up",
      development: "/data-development",
      workflow: "/workflow/definitions",
      quality: "/data-quality/table-config",
      application: "/dashboard",
    };

    if (routes[key]) {
      history.push(routes[key]);
    }
  };

  return (
    <div
      className="
        relative
        min-h-screen w-full
        overflow-hidden
        bg-[#f7f8fa]
        text-[#242731]
      "
    >
      {/* =====================================================
       * Page background
       * ===================================================== */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute inset-0
          overflow-hidden
        "
      >
        {/*
         * 顶部主要氛围带。
         *
         * 不从最左侧开始，和参考图一样，
         * 让品牌信息区域保持干净，
         * 蓝白氛围主要集中在右侧。
         */}
        <AtmosphereBand
          className="
            left-[330px] right-3 top-[1px]
            h-[106px]
            opacity-[0.90]
          "
        />

        {/*
         * 右上角非常淡的补光。
         * 只负责增加空气感，不形成明显色块。
         */}
        <div
          className="
            absolute -right-[40px] -top-[185px]
            h-[390px] w-[68%]

            bg-[radial-gradient(ellipse_at_center,rgba(158,215,239,0.22)_0%,rgba(190,224,239,0.10)_46%,rgba(255,255,255,0)_74%)]
            blur-[24px]
          "
        />

        {/*
         * 右下角原有的轻微暖光保留，
         * 但降低强度。
         */}
        <div
          className="
            absolute -bottom-[190px] -right-[170px]
            h-[420px] w-[620px]

            bg-[radial-gradient(circle_at_center,rgba(231,238,178,0.28),rgba(255,255,255,0)_70%)]
            blur-[20px]
          "
        />

        {/*
         * 顶部极淡竖向纹理。
         * 比旧版更弱，只用于增加质感。
         */}
        <div
          className="
            absolute right-[2%] top-0
            h-[102px] w-[62%]
            opacity-[0.08]

            [background-image:repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0px,rgba(255,255,255,0.95)_1px,transparent_1px,transparent_5px)]

            [mask-image:linear-gradient(90deg,transparent_0%,rgba(0,0,0,0.2)_20%,rgba(0,0,0,1)_70%,transparent_100%)]
          "
        />
      </div>

      {/* =====================================================
       * Content
       * ===================================================== */}
      <div className="relative z-10">
        {/* Header profile */}
        <header
          className="
            flex h-[116px]
            items-center px-4
          "
        >
          <div className="flex items-center">
            {/* Avatar */}
            <div
              className="
                flex h-[66px] w-[66px]
                shrink-0 items-center justify-center
                overflow-hidden rounded-full

                border border-white/90

                bg-gradient-to-br
                from-[#dde6ef]
                via-[#a6c8e2]
                to-[#5e93d4]

                text-white/95

                shadow-[0_2px_4px_rgba(31,35,41,0.04)]
              "
            >
              <Database size={30} strokeWidth={1.5} />
            </div>

            {/* Profile info */}
            <div className="ml-4 min-w-0">
              <div
                className="
                  flex min-h-[22px]
                  items-center
                "
              >
                <span
                  className="
                    whitespace-nowrap
                    text-sm font-medium
                    leading-[22px]
                    text-[#252830]
                  "
                >
                  Yak Ops
                </span>

                <span
                  className="
                    mx-3 h-[14px] w-px
                    shrink-0
                    bg-black/[0.14]
                  "
                />

                <span
                  className="
                    whitespace-nowrap
                    text-sm font-normal
                    leading-[22px]
                    text-[#777b84]
                  "
                >
                  Data Operations Platform
                </span>

                <span
                  className="
                    mx-3 h-[14px] w-px
                    shrink-0
                    bg-black/[0.14]
                  "
                />

                <span
                  className="
                    whitespace-nowrap
                    text-sm font-normal
                    leading-[22px]
                    text-[#777b84]
                  "
                >
                  让数据接入、开发、编排、质量与消费变得更简单
                </span>
              </div>

              <div
                className="
                  mt-2.5 flex
                  items-center gap-[27px]
                "
              >
                <ProfileStat
                  label="数据源"
                  value={headerStats.dataSourceCount}
                  arrow
                  onClick={() => history.push("/data-source")}
                />

                <ProfileStat
                  label="运行中"
                  value={headerStats.runningCount}
                  arrow
                  onClick={() => history.push("/data-development/executions")}
                />

                <ProfileStat
                  label="近7日异常"
                  value={headerStats.exceptionCount}
                  onClick={() => history.push("/data-development/executions")}
                />
              </div>
            </div>
          </div>
        </header>

        {/* ===================================================
         * Main
         * =================================================== */}
        <main className="px-4 pb-4">
          {/* Quick actions */}
          <div
            className="
              grid grid-cols-1 gap-4

              min-[901px]:
              grid-cols-[330px_minmax(0,1fr)]

              min-[1101px]:
              grid-cols-[398px_minmax(0,1fr)]
            "
          >
            {/* 数据接入 */}
            <Section title="数据接入">
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

            {/* 数据工作 */}
            <Section title="数据工作" className="bg-white/[0.74]">
              <div
                className="
                  grid grid-cols-1
                  gap-x-[14px] gap-y-[13px]
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

          {/* Data center + schedule */}
          <HomeSecondaryPanels />

          {/* Interaction / workbench */}
          <HomeWorkbench />
        </main>
      </div>
    </div>
  );
}
