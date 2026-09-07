import {
  canAccessNavigationRoute,
  getRouteMetadata,
} from '@/config/navigation';
import { Link, useIntl, useModel } from '@umijs/max';
import { useMemo } from 'react';

import HomePrimitiveIcon, {
  type HomePrimitiveIconName,
} from './HomePrimitiveIcon';

interface QuickNavigationItem {
  key: string;
  labelId: string;
  path: string;
  icon: HomePrimitiveIconName;
}

const QUICK_NAVIGATION_ITEMS: QuickNavigationItem[] = [
  {
    key: 'data-source',
    labelId: 'pages.home.quickNavigation.dataSource',
    path: '/data-source',
    icon: 'dataSource',
  },
  {
    key: 'offline-sync',
    labelId: 'pages.home.quickNavigation.offlineSync',
    path: '/sync/batch-link-up',
    icon: 'sync',
  },
  {
    key: 'data-quality',
    labelId: 'pages.home.quickNavigation.dataQuality',
    path: '/data-quality/overview',
    icon: 'quality',
  },
  {
    key: 'data-service',
    labelId: 'pages.home.quickNavigation.dataService',
    path: '/data-service',
    icon: 'service',
  },
];

export default function HomeQuickNavigation() {
  const intl = useIntl();
  const { initialState } = useModel('@@initialState');
  const permissionCodes = initialState?.currentUser?.permissionCodes;
  const menuCodes = initialState?.currentUser?.menuCodes;

  const navigationItems = useMemo(
    () =>
      QUICK_NAVIGATION_ITEMS.filter((item) => {
        const route = getRouteMetadata(item.path);
        return (
          !route ||
          canAccessNavigationRoute(route, permissionCodes, menuCodes)
        );
      }),
    [menuCodes, permissionCodes],
  );

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header>
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.quickNavigation.title' })}
        </h2>
      </header>

      <div className="mt-5 grid grid-cols-4 gap-2">
        {navigationItems.map((item) => (
          <Link
            key={item.key}
            to={item.path}
            className="group flex min-w-0 flex-col items-center text-center text-[#555b65] transition-colors hover:text-[#252832]"
          >
            <span className="flex h-11 w-11 items-center justify-center rounded-[12px] border border-[#eef0f3] bg-[#fafbfc] text-[#69707a] transition-all duration-150 group-hover:border-[#e3e6ea] group-hover:bg-[#f5f6f8] group-hover:text-[#343941]">
              <HomePrimitiveIcon name={item.icon} size={21} />
            </span>
            <span className="mt-2 w-full truncate text-[11px] font-medium leading-4">
              {intl.formatMessage({ id: item.labelId })}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
