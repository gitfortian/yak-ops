export interface QualityOverviewTabDefinition {
  key: string;
  label: string;
}

export interface QualityRadarDimensionDefinition {
  key: string;
  label: string;
  aliases: string[];
  description: string;
}

export const QUALITY_RADAR_DIMENSIONS: QualityRadarDimensionDefinition[] = [
  {
    key: 'completeness',
    label: '完整性',
    aliases: ['完整性'],
    description: '关注数据是否缺失，以及目标数据范围是否具备应有记录。',
  },
  {
    key: 'uniqueness',
    label: '唯一性',
    aliases: ['唯一性'],
    description: '关注关键字段或业务主键是否存在重复。',
  },
  {
    key: 'validity',
    label: '有效性',
    aliases: ['有效性'],
    description: '关注数值范围、格式和值域是否满足业务约束。',
  },
  {
    key: 'accuracy',
    label: '准确性',
    aliases: ['准确性'],
    description: '关注枚举、规则判断和业务事实是否符合预期。',
  },
  {
    key: 'timeliness',
    label: '时效性',
    aliases: ['时效性', '及时性'],
    description: '关注数据是否按期到达并在预期时间窗口内完成更新。',
  },
];

export const QUALITY_EXECUTION_TABS: QualityOverviewTabDefinition[] = [
  { key: 'execution', label: '执行' },
  { key: 'monitor', label: '监控' },
  { key: 'rule', label: '规则' },
];

export const QUALITY_ISSUE_TABS: QualityOverviewTabDefinition[] = [
  { key: 'issue', label: '问题' },
  { key: 'dimension', label: '维度' },
];

export const QUALITY_METRIC_EXPLANATIONS = [
  ['通过率', '统计周期内通过规则数 / 已执行规则数。'],
  ['问题率', '统计周期内未通过规则与异常规则之和 / 已执行规则数。'],
  ['活跃监控', '统计周期内至少产生过一次执行记录的监控数量。'],
  ['执行规则', '统计周期内已经形成通过、未通过或异常结果的规则执行数量。'],
  ['问题执行', '统计周期内至少包含一个未通过或异常规则的执行记录数量。'],
  ['问题数据表', '统计周期内出现未通过或异常规则的数据表去重数量。'],
  ['涉及字段', '统计周期内出现未通过或异常结果的字段去重数量。'],
  ['问题贡献 TOP3', '按质量维度统计问题规则数量，并展示贡献最高的三个维度。'],
] as const;
