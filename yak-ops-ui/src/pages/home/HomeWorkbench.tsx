import { history } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
  Clock3,
  LayoutDashboard,
  Sparkles,
} from 'lucide-react';
import type { ReactNode } from 'react';

import {
  DataLineageOverview,
  DatasetOverview,
  useHomeAssetOverview,
} from './HomeAssetOverview';

/**
 * 首页业务总览。
 *
 * 数据集与血缘已接入真实统计；其他模块仍按后续阶段逐步替换 mock。
 */

interface SectionHeaderProps {
  title: string;
  description?: string;
  onMore?: () => void;
}

function SectionHeader({
  title,
  description,
  onMore,
}: SectionHeaderProps) {
  return (
    <header className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {title}
        </h2>

        {description ? (
          <p className="mt-1 text-[12px] leading-5 text-[#92969f]">
            {description}
          </p>
        ) : null}
      </div>

      {onMore ? (
        <button
          type="button"
          onClick={onMore}
          className="mt-0.5 flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#747982] transition-colors hover:text-[#252832]"
        >
          查看更多
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      ) : null}
    </header>
  );
}

/* -------------------------------------------------------------------------- */
/* 数据质量                                                                   */
/* -------------------------------------------------------------------------- */

const qualityRadarOption: EChartsOption = {
  animation: true,
  animationDuration: 760,
  tooltip: {
    trigger: 'item',
  },
  radar: {
    center: ['50%', '51%'],
    radius: '67%',
    splitNumber: 4,
    indicator: [
      { name: '完整性', max: 100 },
      { name: '唯一性', max: 100 },
      { name: '一致性', max: 100 },
      { name: '准确性', max: 100 },
      { name: '及时性', max: 100 },
    ],
    axisName: {
      color: '#7f848d',
      fontSize: 11,
    },
    axisLine: {
      lineStyle: {
        color: '#e3e6eb',
      },
    },
    splitLine: {
      lineStyle: {
        color: '#e9ebef',
      },
    },
    splitArea: {
      areaStyle: {
        color: ['#ffffff', '#fafbfc'],
      },
    },
  },
  series: [
    {
      type: 'radar',
      symbol: 'circle',
      symbolSize: 4,
      data: [
        {
          value: [98, 95, 93, 97, 91],
          name: '质量评分',
          lineStyle: {
            width: 2,
            color: '#6685ed',
          },
          itemStyle: {
            color: '#6685ed',
          },
          areaStyle: {
            color: 'rgba(102,133,237,0.13)',
          },
        },
      ],
    },
  ],
};

const qualityIssues = [
  {
    table: 'ods_user_profile',
    issue: '手机号字段完整性异常',
    time: '10:24',
  },
  {
    table: 'dwd_order_detail',
    issue: '订单编号唯一性异常',
    time: '09:42',
  },
  {
    table: 'dws_goods_sale',
    issue: '销售金额一致性异常',
    time: '08:17',
  },
];

function QualityOverview() {
  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader
        title="数据质量"
        description="关注质量健康度与需要处理的异常"
        onMore={() => history.push('/data-quality/table-config')}
      />

      <div className="mt-4 grid min-h-[300px] grid-cols-1 gap-5 lg:grid-cols-[180px_250px_minmax(0,1fr)]">
        <div className="flex flex-col justify-center">
          <div className="text-[11px] font-medium text-[#8f949d]">
            综合质量分
          </div>

          <div className="mt-1 flex items-end gap-1">
            <strong className="text-[42px] font-semibold leading-[48px] tracking-[-1.5px] text-[#2f333c]">
              96.8
            </strong>

            <span className="mb-1.5 text-[12px] text-[#9ca0a8]">
              /100
            </span>
          </div>

          <div className="mt-1 flex items-center gap-1.5 text-[11px] font-medium text-[#3c9766]">
            <CheckCircle2 size={13} strokeWidth={2} />
            整体质量健康
          </div>

          <div className="mt-6 grid grid-cols-2 gap-x-5 gap-y-4">
            <QualityMetric label="监控表" value="38" />
            <QualityMetric label="今日检测" value="126" />
            <QualityMetric label="异常表" value="3" warning />
            <QualityMetric label="规则" value="84" />
          </div>
        </div>

        <div className="min-h-[250px]">
          <ReactECharts
            option={qualityRadarOption}
            style={{
              width: '100%',
              height: '260px',
            }}
          />
        </div>

        <div className="min-w-0 border-t border-[#eef0f3] pt-4 lg:border-l lg:border-t-0 lg:pl-5 lg:pt-0">
          <div className="flex items-center justify-between">
            <strong className="text-[13px] font-semibold text-[#40444d]">
              最近异常
            </strong>

            <span className="flex items-center gap-1 text-[10px] text-[#a0a4ac]">
              <AlertTriangle
                size={11}
                strokeWidth={1.8}
                className="text-[#e46a73]"
              />
              3 项待关注
            </span>
          </div>

          <div className="mt-3 divide-y divide-[#f0f1f3]">
            {qualityIssues.map((item) => (
              <button
                key={`${item.table}-${item.issue}`}
                type="button"
                onClick={() => history.push('/data-quality/execution')}
                className="group flex w-full items-center gap-3 border-0 bg-transparent py-3 text-left"
              >
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#fff2f3] text-[#e35d69]">
                  <AlertTriangle size={14} strokeWidth={1.8} />
                </span>

                <span className="min-w-0 flex-1">
                  <strong className="block truncate text-[12px] font-medium text-[#41454e]">
                    {item.issue}
                  </strong>

                  <span className="mt-1 block truncate text-[10px] text-[#9ca0a8]">
                    {item.table}
                  </span>
                </span>

                <span className="shrink-0 text-[10px] text-[#9ca0a8]">
                  {item.time}
                </span>

                <ChevronRight
                  size={13}
                  strokeWidth={1.8}
                  className="shrink-0 text-[#b8bbc1] transition-transform group-hover:translate-x-0.5"
                />
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function QualityMetric({
  label,
  value,
  warning = false,
}: {
  label: string;
  value: string;
  warning?: boolean;
}) {
  return (
    <div>
      <div className="text-[10px] leading-4 text-[#999da5]">{label}</div>

      <strong
        className={`mt-0.5 block text-[18px] font-semibold leading-6 ${
          warning ? 'text-[#dc5964]' : 'text-[#40444d]'
        }`}
      >
        {value}
      </strong>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* 数据服务                                                                   */
/* -------------------------------------------------------------------------- */

const serviceTrendOption: EChartsOption = {
  animation: true,
  animationDuration: 720,
  grid: {
    top: 12,
    left: 8,
    right: 8,
    bottom: 20,
    containLabel: false,
  },
  tooltip: {
    trigger: 'axis',
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['08-20', '08-21', '08-22', '08-23', '08-24', '08-25', '08-26'],
    axisLine: {
      show: false,
    },
    axisTick: {
      show: false,
    },
    axisLabel: {
      color: '#a0a4ac',
      fontSize: 9,
    },
  },
  yAxis: {
    type: 'value',
    show: false,
  },
  series: [
    {
      name: '调用量',
      type: 'line',
      smooth: 0.42,
      symbol: 'none',
      data: [9800, 11420, 10860, 13600, 12180, 14920, 12860],
      lineStyle: {
        width: 2,
        color: '#6490ee',
      },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            {
              offset: 0,
              color: 'rgba(100,144,238,0.18)',
            },
            {
              offset: 1,
              color: 'rgba(100,144,238,0.01)',
            },
          ],
        },
      },
    },
  ],
};

const serviceMetrics = [
  {
    label: 'API 总数',
    value: '32',
  },
  {
    label: '已发布',
    value: '26',
  },
  {
    label: '今日调用',
    value: '12,860',
  },
  {
    label: '成功率',
    value: '99.2%',
  },
];

function DataServiceOverview() {
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader
        title="数据服务"
        description="近 7 日 API 服务运行情况"
        onMore={() => history.push('/data-service/overview')}
      />

      <div className="mt-5">
        <div className="grid grid-cols-2 divide-x divide-[#eef0f3] lg:grid-cols-4">
          {serviceMetrics.map((item) => (
            <div key={item.label} className="px-4 first:pl-0 last:pr-0">
              <div className="text-[11px] text-[#92969f]">{item.label}</div>

              <strong className="mt-1 block text-[24px] font-semibold tracking-[-0.6px] text-[#30343d]">
                {item.value}
              </strong>
            </div>
          ))}
        </div>

        <div className="mt-5 h-[126px]">
          <ReactECharts
            option={serviceTrendOption}
            style={{
              width: '100%',
              height: '126px',
            }}
          />
        </div>
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* 仪表盘                                                                     */
/* -------------------------------------------------------------------------- */

const dashboardItems = [
  {
    id: 'sales',
    title: '销售经营分析',
    description: '销售额、订单量、区域与渠道经营分析',
    time: '20 分钟前更新',
    type: 'sales',
  },
  {
    id: 'user',
    title: '用户增长分析',
    description: '新增用户、活跃度与留存趋势',
    time: '2 小时前更新',
    type: 'user',
  },
  {
    id: 'monitor',
    title: '数据平台运行监控',
    description: '任务运行、失败率与资源使用情况',
    time: '今天 08:20 更新',
    type: 'monitor',
  },
];

function DashboardPreview({ type }: { type: string }) {
  if (type === 'sales') {
    return (
      <div className="absolute inset-0 p-4">
        <div className="grid grid-cols-3 gap-2">
          {[64, 42, 78].map((width, index) => (
            <div
              key={`${width}-${index}`}
              className="h-7 rounded-[5px] bg-white/80 shadow-sm"
            >
              <div className="px-2 pt-1.5">
                <div className="h-1 w-7 rounded-full bg-[#d4dcf6]" />
                <div
                  className="mt-1 h-1.5 rounded-full bg-[#8fa4ea]"
                  style={{
                    width: `${width}%`,
                  }}
                />
              </div>
            </div>
          ))}
        </div>

        <div className="mt-3 flex h-[82px] items-end gap-2 rounded-[7px] bg-white/70 px-3 pb-2 pt-3">
          {[25, 42, 34, 62, 49, 76, 67, 89, 72, 94].map(
            (height, index) => (
              <span
                key={`${height}-${index}`}
                className="flex-1 rounded-t-[2px] bg-[#9caee8]"
                style={{
                  height: `${height}%`,
                }}
              />
            ),
          )}
        </div>
      </div>
    );
  }

  if (type === 'user') {
    return (
      <div className="absolute inset-0 p-4">
        <div className="flex gap-2">
          <div className="h-9 flex-1 rounded-[6px] bg-white/80 shadow-sm" />
          <div className="h-9 flex-1 rounded-[6px] bg-white/80 shadow-sm" />
        </div>

        <svg
          viewBox="0 0 320 100"
          className="mt-3 h-[88px] w-full rounded-[8px] bg-white/70"
          aria-hidden="true"
        >
          <path
            d="M8 78 C38 65 52 72 76 55 C105 35 121 58 149 45 C177 31 195 42 218 25 C244 9 266 37 312 15"
            fill="none"
            stroke="#8099e7"
            strokeWidth="3"
          />

          <path
            d="M8 78 C38 65 52 72 76 55 C105 35 121 58 149 45 C177 31 195 42 218 25 C244 9 266 37 312 15 L312 98 L8 98 Z"
            fill="rgba(128,153,231,0.10)"
          />
        </svg>
      </div>
    );
  }

  return (
    <div className="absolute inset-0 p-4">
      <div className="grid grid-cols-2 gap-2">
        <div className="flex h-[48px] items-center gap-2 rounded-[6px] bg-white/80 px-3 shadow-sm">
          <span className="h-6 w-6 rounded-full border-[5px] border-[#88a0e8] border-r-[#d8def2]" />
          <span className="h-1.5 flex-1 rounded-full bg-[#d5dbed]" />
        </div>

        <div className="flex h-[48px] items-end gap-1 rounded-[6px] bg-white/80 px-3 pb-2 pt-2 shadow-sm">
          {[44, 70, 52, 82, 63].map((height, index) => (
            <span
              key={`${height}-${index}`}
              className="flex-1 rounded-t-sm bg-[#91a5e6]"
              style={{
                height: `${height}%`,
              }}
            />
          ))}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-4 gap-2">
        {[1, 2, 3, 4].map((item) => (
          <div
            key={item}
            className="h-[60px] rounded-[6px] bg-white/75 shadow-sm"
          />
        ))}
      </div>
    </div>
  );
}

function DashboardOverview() {
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-6 pt-5">
      <SectionHeader
        title="仪表盘"
        description="最近更新的业务分析与平台监控看板"
        onMore={() => history.push('/dashboard')}
      />

      <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {dashboardItems.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => history.push('/dashboard')}
            className="group overflow-hidden rounded-[14px] border border-[#eceef2] bg-white text-left transition-[box-shadow,transform,border-color] duration-200 hover:-translate-y-0.5 hover:border-[#e0e3e9] hover:shadow-[0_10px_28px_rgba(31,35,41,0.075)]"
          >
            <div className="relative h-[164px] overflow-hidden bg-[linear-gradient(145deg,#f4f6fb_0%,#edf1fa_100%)]">
              <DashboardPreview type={item.type} />

              <div className="absolute right-3 top-3 flex h-7 w-7 items-center justify-center rounded-[8px] border border-white/80 bg-white/85 text-[#6674a8] opacity-0 shadow-sm backdrop-blur transition-opacity group-hover:opacity-100">
                <ChevronRight size={14} strokeWidth={1.8} />
              </div>
            </div>

            <div className="px-4 pb-4 pt-3.5">
              <div className="flex items-center gap-2">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[7px] bg-[#f2f4fb] text-[#737fae]">
                  <LayoutDashboard size={14} strokeWidth={1.8} />
                </span>

                <strong className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[#373b44]">
                  {item.title}
                </strong>
              </div>

              <p className="mt-2 truncate text-[11px] text-[#92969f]">
                {item.description}
              </p>

              <div className="mt-3 flex items-center gap-1 text-[10px] text-[#a0a4ac]">
                <Clock3 size={11} strokeWidth={1.8} />
                {item.time}
              </div>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* 首页                                                                       */
/* -------------------------------------------------------------------------- */

export default function HomeWorkbench() {
  const assetOverviewState = useHomeAssetOverview();

  return (
    <div className="mt-4 space-y-4">
      <DatasetOverview state={assetOverviewState} />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.32fr)_minmax(400px,0.68fr)]">
        <QualityOverview />
        <DataServiceOverview />
      </div>

      <DataLineageOverview state={assetOverviewState} />

      <DashboardOverview />

      <div className="flex items-center justify-center gap-2 py-2 text-[10px] text-[#aaadb4]">
        <Sparkles size={11} strokeWidth={1.8} />
        数据集与血缘已接入真实数据，其余总览将在后续阶段逐步接入
      </div>
    </div>
  );
}
