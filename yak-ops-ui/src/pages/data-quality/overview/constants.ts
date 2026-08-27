export interface QualityOverviewMetric {
  label: string;
  value: string;
  tooltip?: string;
}

export interface QualityOverviewTab {
  key: string;
  label: string;
  metrics: QualityOverviewMetric[];
  emptyTitle: string;
  emptyDescription: string;
}

export interface QualityRadarMetric {
  key: string;
  label: string;
  value: string;
  caption: string;
}

export const QUALITY_RADAR_METRICS: QualityRadarMetric[] = [
  { key: 'completeness', label: '完整性', value: '0.0%', caption: '暂无可比数据' },
  { key: 'uniqueness', label: '唯一性', value: '0.0%', caption: '暂无可比数据' },
  { key: 'validity', label: '有效性', value: '0.0%', caption: '暂无可比数据' },
  { key: 'accuracy', label: '准确性', value: '0.0%', caption: '暂无可比数据' },
  { key: 'timeliness', label: '及时性', value: '0.0%', caption: '暂无可比数据' },
];

export const QUALITY_EXECUTION_TABS: QualityOverviewTab[] = [
  {
    key: 'execution',
    label: '执行',
    metrics: [
      { label: '执行次数', value: '0' },
      { label: '监控对象', value: '0' },
      { label: '规则数', value: '0' },
      { label: '通过规则', value: '0' },
      { label: '未通过规则', value: '0' },
      { label: '异常规则', value: '0' },
      { label: '通过率', value: '0.0%', tooltip: '通过规则数 / 已执行规则数' },
      { label: '平均耗时', value: '0 ms' },
      { label: '问题数', value: '0', tooltip: '未通过规则与异常规则合计' },
    ],
    emptyTitle: '暂无质量执行数据',
    emptyDescription: '完成一次质量监控执行后，这里将展示趋势和运行指标',
  },
  {
    key: 'monitor',
    label: '监控',
    metrics: [
      { label: '监控总数', value: '0' },
      { label: '启用监控', value: '0' },
      { label: '覆盖数据表', value: '0' },
      { label: '今日新增', value: '0' },
      { label: '正常监控', value: '0' },
      { label: '异常监控', value: '0' },
      { label: '问题监控', value: '0' },
      { label: '最近运行', value: '--' },
    ],
    emptyTitle: '暂无监控趋势数据',
    emptyDescription: '创建数据表监控后，这里将展示监控覆盖与运行变化',
  },
  {
    key: 'rule',
    label: '规则',
    metrics: [
      { label: '规则总数', value: '0' },
      { label: '启用规则', value: '0' },
      { label: '表级规则', value: '0' },
      { label: '字段级规则', value: '0' },
      { label: '系统模板', value: '0' },
      { label: '自定义模板', value: '0' },
      { label: '覆盖字段', value: '0' },
      { label: '平均每表规则', value: '0' },
    ],
    emptyTitle: '暂无规则趋势数据',
    emptyDescription: '配置质量规则后，这里将展示规则覆盖和使用情况',
  },
];

export const QUALITY_ISSUE_TABS: QualityOverviewTab[] = [
  {
    key: 'issue',
    label: '问题',
    metrics: [
      { label: '问题执行', value: '0' },
      { label: '未通过规则', value: '0' },
      { label: '异常规则', value: '0' },
      { label: '涉及监控', value: '0' },
      { label: '涉及数据表', value: '0' },
      { label: '涉及字段', value: '0' },
      { label: '问题率', value: '0.0%', tooltip: '问题规则数 / 已执行规则数' },
      { label: '连续异常', value: '0' },
    ],
    emptyTitle: '暂无问题数据',
    emptyDescription: '出现未通过或执行异常的质量规则后，这里将展示问题趋势',
  },
  {
    key: 'dimension',
    label: '维度',
    metrics: [
      { label: '完整性问题', value: '0' },
      { label: '唯一性问题', value: '0' },
      { label: '有效性问题', value: '0' },
      { label: '准确性问题', value: '0' },
      { label: '及时性问题', value: '0' },
      { label: '自定义问题', value: '0' },
    ],
    emptyTitle: '暂无维度问题分布',
    emptyDescription: '质量问题产生后，这里将按质量维度展示变化',
  },
];
