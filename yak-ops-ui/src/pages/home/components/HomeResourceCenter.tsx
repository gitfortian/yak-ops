import { Link, useIntl } from '@umijs/max';

import HomePrimitiveIcon, {
  type HomePrimitiveIconName,
} from './HomePrimitiveIcon';

interface ResourceMetricDefinition {
  key: string;
  labelId: string;
  icon: HomePrimitiveIconName;
}

const RESOURCE_METRICS: ResourceMetricDefinition[] = [
  {
    key: 'files',
    labelId: 'pages.home.resourceCenter.files',
    icon: 'file',
  },
  {
    key: 'folders',
    labelId: 'pages.home.resourceCenter.folders',
    icon: 'folder',
  },
  {
    key: 'storage',
    labelId: 'pages.home.resourceCenter.storage',
    icon: 'storage',
  },
];

/**
 * Frontend-only resource summary shell. The values deliberately remain `--`
 * until a dedicated home resource summary endpoint is introduced.
 */
export default function HomeResourceCenter() {
  const intl = useIntl();

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.resourceCenter.title' })}
        </h2>

        <Link
          to="/resource-management"
          className="group flex shrink-0 items-center gap-0.5 text-[12px] font-medium text-[#7a8089] transition-colors hover:text-[#30353d]"
        >
          {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
          <span className="text-[15px] font-light leading-none transition-transform group-hover:translate-x-0.5">
            ›
          </span>
        </Link>
      </header>

      <div className="mt-4 grid grid-cols-3 gap-2 border-t border-[#eef0f2] pt-4">
        {RESOURCE_METRICS.map((metric) => (
          <div key={metric.key} className="min-w-0 text-center">
            <span className="mx-auto flex h-9 w-9 items-center justify-center rounded-[10px] bg-[#f7f8fa] text-[#7b818b]">
              <HomePrimitiveIcon name={metric.icon} size={18} />
            </span>
            <strong className="mt-2 block text-[17px] font-semibold leading-5 text-[#434851]">
              --
            </strong>
            <span className="mt-1 block truncate text-[10px] text-[#969ba3]">
              {intl.formatMessage({ id: metric.labelId })}
            </span>
          </div>
        ))}
      </div>

      <div className="mt-4 border-t border-[#eef0f2] pt-3.5">
        <div className="flex items-center justify-between gap-3">
          <strong className="text-[12px] font-semibold text-[#555a64]">
            {intl.formatMessage({ id: 'pages.home.resourceCenter.recent' })}
          </strong>
          <span className="text-[10px] text-[#a0a5ad]">
            {intl.formatMessage({ id: 'pages.home.resourceCenter.preview' })}
          </span>
        </div>

        <div className="mt-3 flex min-h-[74px] items-center justify-center rounded-[12px] bg-[#fafbfc] px-4 text-center">
          <div className="flex items-center gap-2 text-[#a0a5ad]">
            <HomePrimitiveIcon name="file" size={18} />
            <span className="text-[11px]">
              {intl.formatMessage({ id: 'pages.home.resourceCenter.empty' })}
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
