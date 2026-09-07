import { history, useIntl } from "@umijs/max";
import type { SVGProps } from "react";

interface QuickNavigationItem {
  key: string;
  labelId: string;
  path: string;
  icon: React.ComponentType<SVGProps<SVGSVGElement>>;
}

/**
 * Temporary icons.
 *
 * These SVGs only reserve the icon size and visual structure.
 * Replace them with the final Yak Ops SVG assets later.
 */
function DigitalScreenIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="16390"
      width="24"
      height="24"
    >
      <path
        d="M768 1024H256C115.2 1024 0 908.8 0 768V256C0 115.2 115.2 0 256 0h512c140.8 0 256 115.2 256 256v512c0 140.8-115.2 256-256 256z"
        fill="#25C251"
        p-id="16391"
      ></path>
      <path
        d="M256 243.2c6.4-6.4 17.067-10.667 25.6-10.667h204.8c10.667 0 21.333 4.267 27.733 12.8 6.4 6.4 10.667 14.934 10.667 25.6v64h213.333c6.4 0 12.8 2.134 19.2 6.4 8.534 6.4 14.934 14.934 17.067 25.6V537.6c-23.467-21.333-55.467-34.133-85.333-36.267-27.734-2.133-55.467 4.267-78.934 17.067C576 535.467 550.4 567.467 537.6 601.6c-8.533 25.6-10.667 55.467-2.133 81.067C539.733 704 550.4 723.2 563.2 738.133H283.733c-10.666 0-21.333-4.266-27.733-12.8-6.4-6.4-8.533-14.933-10.667-23.466V268.8c-2.133-8.533 2.134-19.2 10.667-25.6m55.467 121.6c-4.267 0-6.4 2.133-8.534 4.267-6.4 6.4-6.4 17.066-2.133 23.466 2.133 4.267 6.4 6.4 12.8 6.4h108.8c4.267 0 8.533-2.133 10.667-4.266 6.4-6.4 6.4-17.067 0-23.467-2.134-4.267-8.534-4.267-12.8-4.267-34.134-2.133-72.534-2.133-108.8-2.133m0 113.067c-4.267 0-6.4 2.133-8.534 4.266-6.4 6.4-6.4 17.067 0 23.467 2.134 2.133 6.4 4.267 10.667 4.267h110.933c6.4 0 12.8-4.267 14.934-10.667 2.133-4.267 0-10.667-2.134-14.933-2.133-4.267-8.533-6.4-12.8-6.4H311.467m0 113.066c-4.267 0-6.4 2.134-8.534 4.267-6.4 6.4-6.4 17.067 0 23.467 2.134 4.266 6.4 4.266 10.667 4.266h108.8c4.267 0 8.533-2.133 10.667-4.266 6.4-6.4 6.4-17.067 0-23.467-2.134-2.133-6.4-4.267-12.8-4.267h-108.8z"
        fill="#FFFFFF"
        p-id="16392"
      ></path>
      <path
        d="M659.2 520.533c25.6-2.133 49.067 2.134 72.533 12.8 17.067 8.534 32 21.334 44.8 38.4 10.667 14.934 19.2 34.134 21.334 53.334 4.266 29.866-2.134 61.866-17.067 87.466-12.8 19.2-29.867 36.267-51.2 44.8-29.867 14.934-64 17.067-96 6.4-17.067-6.4-34.133-14.933-46.933-27.733-17.067-14.933-27.734-36.267-34.134-57.6-6.4-23.467-6.4-51.2 2.134-74.667C563.2 582.4 576 563.2 593.067 548.267c19.2-14.934 42.666-25.6 66.133-27.734m12.8 51.2c-2.133 0-4.267 2.134-6.4 6.4-6.4 12.8-12.8 23.467-17.067 36.267-12.8 2.133-27.733 4.267-40.533 6.4-4.267 0-8.533 6.4-8.533 10.667s2.133 6.4 4.266 8.533c8.534 8.533 19.2 17.067 27.734 27.733-2.134 12.8-4.267 27.734-6.4 40.534 0 4.266 2.133 8.533 4.266 10.666 2.134 2.134 6.4 2.134 10.667 0 12.8-6.4 23.467-12.8 36.267-19.2 12.8 6.4 23.466 12.8 36.266 19.2 4.267 2.134 8.534 2.134 12.8-2.133 2.134-2.133 4.267-6.4 4.267-10.667-2.133-12.8-4.267-27.733-6.4-40.533 10.667-8.533 19.2-19.2 29.867-27.733 2.133-2.134 4.266-8.534 2.133-12.8-2.133-2.134-4.267-4.267-8.533-6.4-12.8-2.134-27.734-4.267-40.534-6.4-6.4-12.8-12.8-23.467-17.066-36.267-8.534-2.133-12.8-6.4-17.067-4.267z"
        fill="#FFFFFF"
        p-id="16393"
      ></path>
    </svg>
  );
}

function DataCatalogIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="52074"
      width="24"
      height="24"
    >
      <path
        d="M64 64m64 0l704 0q64 0 64 64l0 128q0 64-64 64l-704 0q-64 0-64-64l0-128q0-64 64-64Z"
        fill="#4A90E2"
        p-id="52075"
      ></path>
      <path
        d="M64 384m64 0l704 0q64 0 64 64l0 128q0 64-64 64l-704 0q-64 0-64-64l0-128q0-64 64-64Z"
        fill="#4A90E2"
        p-id="52076"
      ></path>
      <path
        d="M64 704m64 0l704 0q64 0 64 64l0 128q0 64-64 64l-704 0q-64 0-64-64l0-128q0-64 64-64Z"
        fill="#94C5FF"
        p-id="52077"
      ></path>
      <path
        d="M192 128h128v128H192zM192 448h128v128H192zM192 768h128v128H192z"
        fill="#FFFFFF"
        p-id="52078"
      ></path>
    </svg>
  );
}

function DashboardIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="40987"
      width="24"
      height="24"
    >
      <path
        d="M51.2 512h153.6v256H51.2v-256z m-51.2 358.4h1024v102.4H0v-102.4z m307.2-460.8h153.6v358.4H307.2V409.6z m256-358.4h153.6v716.8h-153.6V51.2z m256 256h153.6v460.8h-153.6V307.2z"
        fill="#033294"
        p-id="40988"
      ></path>
    </svg>
  );
}

function DataServiceIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="44853"
      width="24"
      height="24"
    >
      <path
        d="M128 1024h768a128 128 0 0 0 128-128V128a128 128 0 0 0-128-128H128a128 128 0 0 0-128 128v768a128 128 0 0 0 128 128z"
        fill="#39C19D"
        p-id="44854"
      ></path>
      <path
        d="M230.08 449.92L132.288 686.72l-1.856 5.184a56.768 56.768 0 0 0 54.336 73.28h165.632l-120.32-315.136zM599.872 256h-102.4l172.16 457.856c11.136 29.696 39.36 49.472 71.04 49.92l107.264 1.344-176.192-459.648A76.992 76.992 0 0 0 599.872 256zM375.168 256h-102.4l172.16 457.856c11.136 29.696 39.296 49.472 71.04 49.92l107.264 1.344-176.128-459.648A76.992 76.992 0 0 0 375.168 256z"
        fill="#FFFFFF"
        p-id="44855"
      ></path>
    </svg>
  );
}

function MoreArrowIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      {...props}
    >
      <path
        d="M6 3.5 10.5 8 6 12.5"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const QUICK_NAVIGATION_ITEMS: QuickNavigationItem[] = [
  {
    key: "digital-screen",
    labelId: "pages.home.quickNavigation.digitalScreen",
    path: "/digital-screen",
    icon: DigitalScreenIcon,
  },
  {
    key: "data-catalog",
    labelId: "pages.home.quickNavigation.dataCatalog",
    path: "/data-analysis/data-catalog",
    icon: DataCatalogIcon,
  },
  {
    key: "dashboard",
    labelId: "pages.home.quickNavigation.dashboard",
    path: "/dashboard",
    icon: DashboardIcon,
  },
  {
    key: "data-service",
    labelId: "pages.home.quickNavigation.dataService",
    path: "/data-service",
    icon: DataServiceIcon,
  },
];

export default function HomeQuickNavigation() {
  const intl = useIntl();

  const handleNavigate = (path: string) => {
    history.push(path);
  };

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({
            id: "pages.home.quickNavigation.title",
          })}
        </h2>

        {/*
         * 暂时没有独立的“快捷导航列表页”，
         * 所以这里先保留截图中的视觉结构，不做错误跳转。
         * 后续有统一入口后直接改成 button 即可。
         */}
        <div className="flex shrink-0 items-center gap-0.5 text-[12px] text-[#666b75]">
          <span>
            {intl.formatMessage({
              id: "pages.home.common.viewMore",
            })}
          </span>

          <MoreArrowIcon className="h-3.5 w-3.5" />
        </div>
      </header>

      <div className="mt-4 grid grid-cols-4 gap-1">
        {QUICK_NAVIGATION_ITEMS.map((item) => {
          const Icon = item.icon;

          return (
            <button
              key={item.key}
              type="button"
              onClick={() => handleNavigate(item.path)}
              className="
                group
                flex
                min-w-0
                flex-col
                items-center
                justify-center
                gap-2
                border-0
                bg-transparent
                px-1
                py-2
                text-center
                outline-none
                transition-colors
                duration-150
                hover:bg-[#f7f8fa]
                focus-visible:bg-[#f7f8fa]
              "
            >
              <div
                className="
                  flex
                  h-11
                  w-11
                  shrink-0
                  items-center
                  justify-center
                  rounded-[9px]
                  border
                  border-[#eceef1]
                  bg-white
                  text-[#5b6270]
                  shadow-[0_1px_3px_rgba(31,35,41,0.04)]
                  transition-all
                  duration-150
                  group-hover:border-[#e2e5e9]
                "
              >
                <Icon className="h-[22px] w-[22px]" />
              </div>

              <span
                className="
                  block
                  w-full
                  truncate
                  text-[12px]
                  font-normal
                  leading-5
                  text-[#30333b]
                "
              >
                {intl.formatMessage({
                  id: item.labelId,
                })}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
