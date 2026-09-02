import type { ComponentType, SVGProps } from 'react';
import SqlNodeIcon from './SqlNodeIcon';
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
  SQL: {
    icon: SqlNodeIcon,
  },
};

const WorkflowNodeIcon = ({ taskType, size = 'md' }: WorkflowNodeIconProps) => {
  const meta = NODE_ICON_META[(taskType || '').toUpperCase()] || DEFAULT_ICON_META;
  const Icon = meta.icon;
  const compact = size === 'sm';
  const tiny = size === 'xs';

  return (
    <span
      className={[
        'flex shrink-0 items-center justify-center border border-[#eceef1] bg-[#fafafa] text-[#667085]',
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
