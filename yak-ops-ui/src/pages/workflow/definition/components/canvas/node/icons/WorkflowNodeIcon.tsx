import JavaIcon from '@/pages/data-development/icon/JavaIcon';
import PythonIcon from '@/pages/data-development/icon/PythonIcon';
import { TerminalSquare } from 'lucide-react';
import type { ComponentType, SVGProps } from 'react';
import SyncNodeIcon from './SyncNodeIcon';

interface WorkflowNodeIconProps {
  taskType?: string;
  size?: 'xs' | 'sm' | 'md';
}

interface NodeIconMeta {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
}

const DefaultNodeIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    {...props}
  >
    <rect x="5" y="5" width="5" height="5" rx="1.5" stroke="currentColor" strokeWidth="1.6" />
    <rect x="14" y="14" width="5" height="5" rx="1.5" stroke="currentColor" strokeWidth="1.6" />
    <path d="M10 7.5h3.5a3 3 0 0 1 3 3V14" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
  </svg>
);

const DEFAULT_ICON_META: NodeIconMeta = {
  icon: DefaultNodeIcon,
};

const NODE_ICON_META: Record<string, NodeIconMeta> = {
  SYNC: {
    icon: SyncNodeIcon,
  },
};

type IconSize = NonNullable<WorkflowNodeIconProps['size']>;

const COMPACT_CONTAINER_SIZE: Record<IconSize, string> = {
  xs: 'h-4 w-4 rounded-[5px] shadow-[0_1px_2px_rgba(16,24,40,.05)]',
  sm: 'h-5 w-5 rounded-md shadow-[0_1px_2px_rgba(16,24,40,.05)]',
  md: 'h-6 w-6 rounded-lg shadow-[0_4px_8px_-2px_rgba(16,24,40,.10),0_2px_4px_-2px_rgba(16,24,40,.06)]',
};

const COMPACT_ICON_SIZE: Record<IconSize, string> = {
  xs: 'h-3 w-3',
  sm: 'h-3.5 w-3.5',
  md: 'h-4 w-4',
};

const BRAND_ICON_SIZE: Record<IconSize, number> = {
  xs: 11,
  sm: 13,
  md: 15,
};

const SQL_TEXT_SIZE: Record<IconSize, string> = {
  xs: 'text-[6px]',
  sm: 'text-[7px]',
  md: 'text-[8px]',
};

const DATA_DEVELOPMENT_TASK_TYPES = new Set([
  'SQL',
  'SHELL',
  'PYTHON',
  'JAVA',
]);

const DevelopmentTaskGlyph = ({
  taskType,
  size,
}: {
  taskType: string;
  size: IconSize;
}) => {
  if (taskType === 'SQL') {
    return (
      <span
        className={[
          'font-bold leading-none tracking-[-0.04em]',
          SQL_TEXT_SIZE[size],
        ].join(' ')}
      >
        SQL
      </span>
    );
  }

  if (taskType === 'JAVA') {
    return <JavaIcon size={BRAND_ICON_SIZE[size]} />;
  }

  if (taskType === 'PYTHON') {
    return <PythonIcon size={BRAND_ICON_SIZE[size]} />;
  }

  return (
    <TerminalSquare
      size={BRAND_ICON_SIZE[size]}
      strokeWidth={1.8}
      aria-hidden="true"
    />
  );
};

const developmentContainerClassName = (taskType: string) => {
  if (taskType === 'SQL') {
    return 'border-[#fed7aa] bg-[#fff7ed] text-[#c2410c]';
  }
  if (taskType === 'SHELL') {
    return 'border-[#d9ddff] bg-[#f4f5ff] text-[#6172f3]';
  }
  return 'border-[#e4e7ec] bg-white text-[#344054]';
};

const WorkflowNodeIcon = ({ taskType, size = 'md' }: WorkflowNodeIconProps) => {
  const normalizedTaskType = (taskType || '').toUpperCase();
  const meta = NODE_ICON_META[normalizedTaskType] || DEFAULT_ICON_META;
  const Icon = meta.icon;

  if (normalizedTaskType === 'SYNC') {
    return (
      <span
        className={[
          'flex shrink-0 items-center justify-center border-[0.5px] border-white/[0.02]',
          'bg-[#6172f3] text-white',
          COMPACT_CONTAINER_SIZE[size],
        ].join(' ')}
      >
        <Icon className={COMPACT_ICON_SIZE[size]} />
      </span>
    );
  }

  if (DATA_DEVELOPMENT_TASK_TYPES.has(normalizedTaskType)) {
    return (
      <span
        className={[
          'flex shrink-0 items-center justify-center border-[0.5px]',
          COMPACT_CONTAINER_SIZE[size],
          developmentContainerClassName(normalizedTaskType),
        ].join(' ')}
      >
        <DevelopmentTaskGlyph taskType={normalizedTaskType} size={size} />
      </span>
    );
  }

  const compact = size === 'sm';
  const tiny = size === 'xs';

  return (
    <span
      className={[
        'flex shrink-0 items-center justify-center bg-[#6172f3] text-white',
        'shadow-[0_1px_2px_rgba(97,114,243,.22)]',
        tiny
          ? 'h-6 w-6 rounded-[7px]'
          : compact
            ? 'h-7 w-7 rounded-[8px]'
            : 'h-9 w-9 rounded-[10px]',
      ].join(' ')}
    >
      <Icon
        className={tiny
          ? 'h-[13px] w-[13px]'
          : compact
            ? 'h-[15px] w-[15px]'
            : 'h-[19px] w-[19px]'}
      />
    </span>
  );
};

export default WorkflowNodeIcon;
