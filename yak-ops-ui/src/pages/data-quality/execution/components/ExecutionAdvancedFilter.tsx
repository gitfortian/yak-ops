import YakButton from '@/components/YakButton';
import { Input, Select } from 'antd';
import { Search } from 'lucide-react';

import type { ExecutionAdvancedFilterState } from '../hooks/useQualityExecutionPage';

const DIMENSION_OPTIONS = [
  '完整性',
  '唯一性',
  '有效性',
  '准确性',
  '自定义',
].map((value) => ({ value, label: value }));

interface ExecutionAdvancedFilterProps {
  value: ExecutionAdvancedFilterState;
  onChange: (value: ExecutionAdvancedFilterState) => void;
  onApply: () => void;
  onReset: () => void;
}

export default function ExecutionAdvancedFilter({
  value,
  onChange,
  onApply,
  onReset,
}: ExecutionAdvancedFilterProps) {
  const patch = (next: Partial<ExecutionAdvancedFilterState>) =>
    onChange({ ...value, ...next });

  return (
    <div className="w-[340px]">
      <div className="mb-3 text-[13px] font-semibold text-[#161823]">
        高级搜索
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <div className="mb-1.5 text-xs text-[#667085]">数据对象</div>
          <Input
            allowClear
            variant="filled"
            value={value.objectKeyword}
            onChange={(event) => patch({ objectKeyword: event.target.value })}
            onPressEnter={onApply}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder="搜索表名或数据对象"
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">运行状态</div>
          <Select
            allowClear
            variant="filled"
            value={value.executionStatus}
            placeholder="全部状态"
            className="w-full"
            onChange={(executionStatus) => patch({ executionStatus })}
            options={[
              { value: 'WAITING', label: '等待中' },
              { value: 'RUNNING', label: '运行中' },
              { value: 'SUCCESS', label: '已完成' },
              { value: 'FAILED', label: '执行失败' },
            ]}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">质量结果</div>
          <Select
            allowClear
            variant="filled"
            value={value.checkResult}
            placeholder="全部结果"
            className="w-full"
            onChange={(checkResult) => patch({ checkResult })}
            options={[
              { value: 'PASSED', label: '通过' },
              { value: 'NOT_PASSED', label: '未通过' },
              { value: 'ERROR', label: '异常' },
              { value: 'RUNNING', label: '运行中' },
            ]}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">问题情况</div>
          <Select
            allowClear
            variant="filled"
            value={value.hasIssues}
            placeholder="全部"
            className="w-full"
            onChange={(hasIssues) => patch({ hasIssues })}
            options={[
              { value: true, label: '存在问题' },
              { value: false, label: '无问题' },
            ]}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">质量维度</div>
          <Select
            allowClear
            variant="filled"
            value={value.dimension}
            placeholder="全部维度"
            className="w-full"
            onChange={(dimension) => patch({ dimension })}
            options={DIMENSION_OPTIONS}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">关联范围</div>
          <Select
            allowClear
            variant="filled"
            value={value.scope}
            placeholder="全部范围"
            className="w-full"
            onChange={(scope) => patch({ scope })}
            options={[
              { value: 'TABLE', label: '表级' },
              { value: 'COLUMN', label: '字段级' },
            ]}
          />
        </div>

        <div className="col-span-2">
          <div className="mb-1.5 text-xs text-[#667085]">触发方式</div>
          <Select
            allowClear
            variant="filled"
            value={value.triggerType}
            placeholder="全部触发方式"
            className="w-full"
            onChange={(triggerType) => patch({ triggerType })}
            options={[
              { value: 'MANUAL', label: '手动触发' },
              { value: 'SCHEDULE', label: '调度触发' },
            ]}
          />
        </div>
      </div>

      <div className="mt-4 flex justify-end gap-2 border-t border-[#f0f1f3] pt-3">
        <YakButton
          size="small"
          type="text"
          className="!text-[#667085]"
          onClick={onReset}
        >
          重置
        </YakButton>
        <YakButton size="small" type="primary" onClick={onApply}>
          应用
        </YakButton>
      </div>
    </div>
  );
}
