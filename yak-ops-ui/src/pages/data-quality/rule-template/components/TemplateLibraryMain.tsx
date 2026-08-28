import YakOpsEmpty from '@/components/YakOpsEmpty';
import YakTab from '@/components/YakTab';
import type { TemplateView } from '@/services/data-quality';
import { BRAND_COLOR, BRAND_COLOR_SOFT } from '@/styles/brand';
import { Button, Dropdown, Input, Spin, Table, Tag } from 'antd';
import { Copy, Ellipsis, Plus, RefreshCw, Search, Trash2 } from 'lucide-react';

import { dataQualityTableClassName } from '../../components/tableStyle';
import type { useQualityTemplateLibrary } from '../hooks/useQualityTemplateLibrary';
import type { TemplateTabKey } from '../hooks/useQualityTemplateLibrary';

type TemplateLibraryModel = ReturnType<typeof useQualityTemplateLibrary>;

interface TemplateLibraryMainProps {
  library: TemplateLibraryModel;
}

const DIMENSION_DESCRIPTIONS: Record<string, string> = {
  全部:
    '汇总展示全部质量维度下的规则模板，可通过维度、模板类型和关键字快速定位。',
  完整性:
    '完整性用于衡量数据是否按照预设要求完整填充，可识别必要数据缺失。',
  唯一性:
    '唯一性用于衡量数据是否存在重复，可判断业务键或字段组合是否唯一。',
  有效性:
    '有效性用于判断数据是否符合预设格式、范围和业务定义。',
  一致性:
    '一致性用于衡量字段、数据表或系统之间的数据表达是否保持一致。',
  准确性:
    '准确性用于衡量数据是否正确反映实际业务对象。',
  及时性:
    '及时性用于衡量数据是否在规定时间内产生、更新或同步。',
  规范性:
    '规范性用于衡量数据是否符合统一的数据标准和编码规则。',
  自定义:
    '自定义维度用于承载团队自行定义的数据质量指标和检查口径。',
};

export default function TemplateLibraryMain({
  library,
}: TemplateLibraryMainProps) {
  const {
    data,
    catalogMeta,
    dimension,
    activeTab,
    setActiveTab,
    keyword,
    setKeyword,
    loading,
    relatedRuleCount,
    loadCatalogMeta,
    loadTemplates,
    openCreateTemplate,
    openEditTemplate,
    removeTemplate,
    openCopy,
  } = library;

  return (
    <main className="min-w-0 flex-1 overflow-hidden px-5 py-4">
      <div className="flex h-full flex-col overflow-hidden">
        <section className="shrink-0">
          <div className="flex items-start justify-between gap-6">
            <div className="min-w-0 flex-1">
              <h2 className="m-0 text-[15px] font-semibold leading-6 text-[#161823]">
                {dimension}
              </h2>
              <div className="mt-1 max-w-[900px] text-[13px] leading-6 text-[#8a8f99]">
                {DIMENSION_DESCRIPTIONS[dimension] ||
                  `${dimension}用于衡量数据是否符合对应的数据质量要求。`}
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Input
                allowClear
                variant="filled"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                prefix={<Search size={14} className="text-[#98a2b3]" />}
                placeholder="搜索模板名称、编码或描述"
                className="w-[330px]"
              />
              <Button
                icon={<RefreshCw size={14} />}
                onClick={() => {
                  void loadCatalogMeta();
                  void loadTemplates();
                }}
              />
            </div>
          </div>

          <div className="mt-2.5 flex flex-wrap items-center gap-2">
            <div className="inline-flex h-7 items-center rounded bg-[#f5f5f6] px-2.5 text-xs text-[#60646f]">
              维度类型：
              <strong className="ml-1 text-[#30323b]">系统维度</strong>
            </div>
            <div className="inline-flex h-7 items-center rounded bg-[#f5f5f6] px-2.5 text-xs text-[#60646f]">
              关联模板数：
              <strong className="ml-1 text-[#30323b]">
                {data.records.length}
              </strong>
            </div>
            <div className="inline-flex h-7 items-center rounded bg-[#f5f5f6] px-2.5 text-xs text-[#60646f]">
              关联规则数：
              <strong className="ml-1 text-[#30323b]">{relatedRuleCount}</strong>
            </div>
          </div>

          <YakTab
            activeKey={activeTab}
            animated={false}
            items={[
              {
                key: 'SYSTEM',
                label: `系统模板 (${catalogMeta.systemTotal})`,
              },
              {
                key: 'CUSTOM',
                label: `自定义模板 (${catalogMeta.customTotal})`,
              },
            ]}
            onChange={(key) => setActiveTab(key as TemplateTabKey)}
            className="mt-2"
          />
        </section>

        <section className="min-h-0 flex flex-1 flex-col overflow-hidden pt-3">
          {activeTab === 'CUSTOM' ? (
            <div className="mb-3 flex shrink-0 items-center justify-between">
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={openCreateTemplate}
              >
                新建规则模板
              </Button>
              <div className="text-xs text-[#8a8f99]">
                模板变更仅影响后续引用，不会修改存量质量规则。
              </div>
            </div>
          ) : null}

          <div className="min-h-0 flex-1 overflow-auto">
            <Spin spinning={loading}>
              <Table<TemplateView>
                rowKey="id"
                size="small"
                bordered
                pagination={false}
                scroll={{ x: 1080 }}
                className={dataQualityTableClassName()}
                dataSource={data.records}
                locale={{
                  emptyText: (
                    <div className="flex min-h-[220px] items-center justify-center">
                      <YakOpsEmpty
                        width={176}
                        height={120}
                        title={
                          activeTab === 'CUSTOM'
                            ? '当前目录暂无自定义模板'
                            : '暂无系统模板'
                        }
                        description={
                          activeTab === 'CUSTOM'
                            ? '当前筛选条件下没有可展示的自定义规则模板'
                            : '当前筛选条件下没有可展示的系统规则模板'
                        }
                      />
                    </div>
                  ),
                }}
                columns={[
                  {
                    title: '模板名称 / 编码',
                    dataIndex: 'name',
                    width: 260,
                    render: (_, record) => (
                      <div className="min-w-0 py-1">
                        <button
                          type="button"
                          className={`block max-w-full truncate border-0 bg-transparent p-0 text-left font-medium ${
                            record.builtin
                              ? 'cursor-default text-[#172033]'
                              : 'cursor-pointer text-[var(--yak-brand-color)]'
                          }`}
                          onClick={() =>
                            !record.builtin && openEditTemplate(record)
                          }
                        >
                          {record.name}
                        </button>
                      </div>
                    ),
                  },
                  {
                    title: '质量维度',
                    dataIndex: 'dimension',
                    width: 110,
                    render: (value) => (
                      <Tag className="!m-0 !border-0 !bg-[#f2f4f7] !text-[#667085]">
                        {value}
                      </Tag>
                    ),
                  },
                  {
                    title: '关联范围',
                    dataIndex: 'scope',
                    width: 100,
                    render: (value) => (
                      <Tag
                        className="!m-0 !border-0"
                        style={{
                          color: BRAND_COLOR,
                          backgroundColor: BRAND_COLOR_SOFT,
                        }}
                      >
                        {value === 'TABLE' ? '表级' : '字段级'}
                      </Tag>
                    ),
                  },
                  {
                    title: '规则数',
                    dataIndex: 'ruleCount',
                    width: 90,
                    render: (value) => (
                      <span className="font-medium text-[#344054]">{value}</span>
                    ),
                  },
                  ...(activeTab === 'CUSTOM'
                    ? [
                        {
                          title: '所属目录',
                          dataIndex: 'folderName',
                          width: 130,
                          render: (value: string) => value || '未分类',
                        },
                      ]
                    : []),
                  {
                    title: '模板描述',
                    dataIndex: 'description',
                    render: (value) => (
                      <div className="line-clamp-2 leading-5 text-[#667085]">
                        {value || '--'}
                      </div>
                    ),
                  },
                  ...(activeTab === 'CUSTOM'
                    ? [
                        {
                          title: '操作',
                          fixed: 'right' as const,
                          width: 150,
                          render: (_: unknown, record: TemplateView) => (
                            <div className="flex items-center gap-1">
                              <Button
                                type="link"
                                size="small"
                                onClick={() => openEditTemplate(record)}
                              >
                                编辑
                              </Button>
                              <Dropdown
                                menu={{
                                  items: [
                                    {
                                      key: 'copy',
                                      icon: <Copy size={14} />,
                                      label: '复制模板',
                                      onClick: () => openCopy(record),
                                    },
                                    {
                                      key: 'delete',
                                      danger: true,
                                      icon: <Trash2 size={14} />,
                                      label: '删除模板',
                                      onClick: () => removeTemplate(record),
                                    },
                                  ],
                                }}
                              >
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<Ellipsis size={15} />}
                                />
                              </Dropdown>
                            </div>
                          ),
                        },
                      ]
                    : []),
                ]}
              />
            </Spin>
          </div>
        </section>
      </div>
    </main>
  );
}
