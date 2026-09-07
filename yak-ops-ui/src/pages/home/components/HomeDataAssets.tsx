import { Link, useIntl } from '@umijs/max';

import HomePrimitiveIcon, {
  type HomePrimitiveIconName,
} from './HomePrimitiveIcon';

interface AssetColumnDefinition {
  key: string;
  titleId: string;
  emptyId: string;
  path: string;
  icon: HomePrimitiveIconName;
}

const ASSET_COLUMNS: AssetColumnDefinition[] = [
  {
    key: 'dataset',
    titleId: 'pages.home.dataAssets.dataset',
    emptyId: 'pages.home.dataAssets.datasetEmpty',
    path: '/dataset',
    icon: 'dataset',
  },
  {
    key: 'service',
    titleId: 'pages.home.dataAssets.dataService',
    emptyId: 'pages.home.dataAssets.dataServiceEmpty',
    path: '/data-service',
    icon: 'service',
  },
  {
    key: 'dashboard',
    titleId: 'pages.home.dataAssets.dashboard',
    emptyId: 'pages.home.dataAssets.dashboardEmpty',
    path: '/dashboard',
    icon: 'dashboard',
  },
];

function AssetColumn({
  definition,
  divider,
}: {
  definition: AssetColumnDefinition;
  divider: boolean;
}) {
  const intl = useIntl();

  return (
    <div
      className={`flex min-w-0 flex-col px-0 md:px-6 ${
        divider ? 'md:border-r md:border-[#eceef1]' : ''
      }`}
    >
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-[#f6f7f9] text-[#676d77]">
            <HomePrimitiveIcon name={definition.icon} size={19} />
          </span>
          <h3 className="m-0 truncate text-[14px] font-semibold text-[#343841]">
            {intl.formatMessage({ id: definition.titleId })}
          </h3>
        </div>

        <strong className="shrink-0 text-[15px] font-semibold text-[#a0a4ab]">
          --
        </strong>
      </div>

      <div className="mt-3 border-t border-[#eef0f2]" />

      <div className="flex min-h-[220px] flex-1 items-center justify-center px-5 py-7">
        <div className="flex max-w-[220px] flex-col items-center text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f7f8fa] text-[#c4c8ce]">
            <HomePrimitiveIcon name={definition.icon} size={24} />
          </span>
          <span className="mt-3 text-[12px] font-medium text-[#8b9098]">
            {intl.formatMessage({ id: definition.emptyId })}
          </span>
        </div>
      </div>

      <Link
        to={definition.path}
        className="group mx-auto flex items-center gap-1 text-[12px] font-medium text-[#787e87] transition-colors hover:text-[#30353d]"
      >
        {intl.formatMessage({ id: 'pages.home.common.viewAll' })}
        <span className="text-[15px] font-light leading-none transition-transform group-hover:translate-x-0.5">
          ›
        </span>
      </Link>
    </div>
  );
}

/**
 * Homepage data-asset shell.
 *
 * This PR intentionally keeps the panel frontend-only. Dataset, data-service,
 * and dashboard summaries will receive their read models in a later backend PR.
 */
export default function HomeDataAssets() {
  const intl = useIntl();

  return (
    <section className="min-h-[390px] rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.dataAssets.title' })}
        </h2>
      </header>

      <div className="mt-4 grid grid-cols-1 gap-6 border-t border-[#eef0f2] pt-4 md:grid-cols-3 md:gap-0">
        {ASSET_COLUMNS.map((definition, index) => (
          <AssetColumn
            key={definition.key}
            definition={definition}
            divider={index < ASSET_COLUMNS.length - 1}
          />
        ))}
      </div>
    </section>
  );
}
