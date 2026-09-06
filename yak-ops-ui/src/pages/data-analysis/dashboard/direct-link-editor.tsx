import { Button, Select, Tooltip } from 'antd';
import { Link2, Plus, Trash2 } from 'lucide-react';
import type {
  AnalysisAsset,
  DashboardCrossFilterRule,
  DashboardInlineAnalysisSpec,
  DashboardWidget,
  DashboardWidgetBehavior,
  PublishedDataset,
} from './model';

const createRuleId = () => `cross-${Date.now()}-${Math.round(Math.random() * 1000)}`;

const behaviorHasMeaning = (behavior: DashboardWidgetBehavior) => Boolean(
  behavior.crossFilters?.length
  || (behavior.clickAction && behavior.clickAction !== 'none'),
);

export function DashboardDirectCrossFilterEditor({
  widget,
  widgets,
  spec,
  dataset,
  datasets,
  analyses,
  onChange,
}: {
  widget: DashboardWidget;
  widgets: DashboardWidget[];
  spec: DashboardInlineAnalysisSpec;
  dataset: PublishedDataset;
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  onChange: (behavior: DashboardWidgetBehavior | undefined) => void;
}) {
  const behavior = spec.dashboardBehavior || {};
  const rules = behavior.crossFilters || [];
  // AnalysisSelection currently emits the primary category dimension. Keep the editor
  // honest by only offering that field as a direct-link source.
  const sourceFields = spec.dimensions.slice(0, 1);
  const sourceOptions = sourceFields.map((fieldId) => {
    const field = dataset.fields.find((item) => item.key === fieldId);
    return { label: field?.label || fieldId, value: fieldId };
  });

  const targetDescriptors = widgets.flatMap((targetWidget) => {
    if (targetWidget.id === widget.id) return [];
    const targetSpec = targetWidget.analysisId
      ? analyses.find((item) => item.id === targetWidget.analysisId)
      : targetWidget.inlineAnalysis;
    if (!targetSpec) return [];
    const targetDataset = datasets.find((item) => item.id === targetSpec.datasetId);
    if (!targetDataset) return [];
    const targetFields = targetDataset.fields.filter((field) => field.role === 'dimension');
    if (!targetFields.length) return [];
    const targetAnalysis = targetWidget.analysisId
      ? analyses.find((item) => item.id === targetWidget.analysisId)
      : undefined;
    return [{
      widget: targetWidget,
      title: targetWidget.analysisId
        ? targetAnalysis?.name ?? '历史图表'
        : targetWidget.title?.trim() || '未命名图表',
      dataset: targetDataset,
      fields: targetFields,
    }];
  });

  const targetOptions = targetDescriptors.map((item) => ({
    label: `${item.title} · ${item.dataset.name}`,
    value: item.widget.id,
  }));

  const patchRules = (nextRules: DashboardCrossFilterRule[]) => {
    const nextBehavior: DashboardWidgetBehavior = {
      ...behavior,
      crossFilters: nextRules.length ? nextRules : undefined,
    };
    onChange(behaviorHasMeaning(nextBehavior) ? nextBehavior : undefined);
  };

  const addRule = () => {
    const sourceField = sourceFields[0];
    const target = targetDescriptors[0];
    if (!sourceField || !target) return;
    const targetField = target.fields.find((field) => field.key === sourceField)?.key
      || target.fields[0]?.key;
    if (!targetField) return;
    patchRules([
      ...rules,
      {
        id: createRuleId(),
        sourceField,
        targetWidgetId: target.widget.id,
        targetField,
      },
    ]);
  };

  const updateRule = (id: string, patch: Partial<DashboardCrossFilterRule>) => {
    patchRules(rules.map((rule) => rule.id === id ? { ...rule, ...patch } : rule));
  };

  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
            <Link2 size={12} />
            直接图表联动
          </div>
          <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
            点击当前图表分类后，直接把值映射为目标图表的运行时筛选，不需要先创建全局筛选器。
          </div>
        </div>
        <Button
          size="small"
          type="text"
          icon={<Plus size={12} />}
          disabled={!sourceOptions.length || !targetOptions.length}
          onClick={addRule}
        >
          添加
        </Button>
      </div>

      {!sourceOptions.length ? (
        <div className="mt-2 rounded-[6px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
          当前图表没有可点击的主分类维度，暂时不能作为直接联动来源。
        </div>
      ) : !targetOptions.length ? (
        <div className="mt-2 rounded-[6px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
          当前仪表盘没有其他可映射的维度图表。
        </div>
      ) : rules.length ? (
        <div className="mt-2 space-y-2">
          {rules.map((rule) => {
            const target = targetDescriptors.find((item) => item.widget.id === rule.targetWidgetId);
            const targetFieldOptions = target?.fields.map((field) => ({
              label: `${field.label} · ${field.dataType}`,
              value: field.key,
            })) ?? [];
            const targetStillAvailable = Boolean(target);
            return (
              <div key={rule.id} className="rounded-[7px] border border-[#edf0f3] bg-[#fafbfc] p-2.5">
                <div className="grid grid-cols-[1fr_28px] items-end gap-2">
                  <div>
                    <div className="mb-1 text-[9px] text-[#98a2b3]">点击字段</div>
                    <Select
                      size="small"
                      className="w-full"
                      value={rule.sourceField}
                      options={sourceOptions}
                      onChange={(sourceField) => updateRule(rule.id, { sourceField })}
                    />
                  </div>
                  <Tooltip title="删除联动">
                    <Button
                      size="small"
                      type="text"
                      danger
                      className="w-7 px-0"
                      icon={<Trash2 size={12} />}
                      onClick={() => patchRules(rules.filter((item) => item.id !== rule.id))}
                    />
                  </Tooltip>
                </div>

                <div className="mt-2 grid grid-cols-2 gap-2">
                  <div>
                    <div className="mb-1 text-[9px] text-[#98a2b3]">目标图表</div>
                    <Select
                      size="small"
                      className="w-full"
                      status={targetStillAvailable ? undefined : 'error'}
                      value={targetStillAvailable ? rule.targetWidgetId : undefined}
                      placeholder={targetStillAvailable ? '选择目标图表' : '目标已失效'}
                      options={targetOptions}
                      onChange={(targetWidgetId) => {
                        const nextTarget = targetDescriptors.find((item) => item.widget.id === targetWidgetId);
                        if (!nextTarget) return;
                        const targetField = nextTarget.fields.find((field) => field.key === rule.sourceField)?.key
                          || nextTarget.fields[0]?.key;
                        if (!targetField) return;
                        updateRule(rule.id, { targetWidgetId, targetField });
                      }}
                    />
                  </div>
                  <div>
                    <div className="mb-1 text-[9px] text-[#98a2b3]">映射字段</div>
                    <Select
                      size="small"
                      className="w-full"
                      disabled={!targetStillAvailable}
                      value={targetStillAvailable ? rule.targetField : undefined}
                      options={targetFieldOptions}
                      onChange={(targetField) => updateRule(rule.id, { targetField })}
                    />
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="mt-2 rounded-[6px] bg-[#fafbfc] px-2.5 py-2 text-[10px] leading-4 text-[#98a2b3]">
          暂无直接联动。添加后，来源和目标可以使用不同 Dataset，只要显式选择对应的目标维度字段即可。
        </div>
      )}
    </div>
  );
}
