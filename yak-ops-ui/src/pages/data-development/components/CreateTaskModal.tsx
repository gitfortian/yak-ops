import { Modal, Select, Tag, Typography } from 'antd';
import { Braces, Code2, Globe2, TerminalSquare } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';

import type { DevelopmentTaskType } from '../types';

interface CreateTaskModalProps {
  open: boolean;
  projects: API.ProjectBrief[];
  defaultProjectId?: number;
  onCancel: () => void;
  onNext: (type: DevelopmentTaskType, projectId: number) => void;
}

const taskTypes: Array<{
  type: DevelopmentTaskType;
  title: string;
  description: string;
  icon: ReactNode;
  enabled: boolean;
}> = [
  {
    type: 'SQL',
    title: 'SQL',
    description: '执行数据库 SQL，支持参数、测试运行和版本发布。',
    icon: <Code2 size={20} strokeWidth={1.8} />,
    enabled: true,
  },
  {
    type: 'SHELL',
    title: 'Shell',
    description: '执行 Shell 脚本，后续通过统一执行器接入。',
    icon: <TerminalSquare size={20} strokeWidth={1.8} />,
    enabled: false,
  },
  {
    type: 'HTTP',
    title: 'HTTP',
    description: '调用 HTTP API，后续通过统一执行器接入。',
    icon: <Globe2 size={20} strokeWidth={1.8} />,
    enabled: false,
  },
  {
    type: 'PYTHON',
    title: 'Python',
    description: '执行 Python 脚本，后续通过统一执行器接入。',
    icon: <Braces size={20} strokeWidth={1.8} />,
    enabled: false,
  },
];

export default function CreateTaskModal({
  open,
  projects,
  defaultProjectId,
  onCancel,
  onNext,
}: CreateTaskModalProps) {
  const [type, setType] = useState<DevelopmentTaskType>('SQL');
  const [projectId, setProjectId] = useState<number>();

  const projectOptions = useMemo(
    () => projects.map((project) => ({
      label: project.projectName,
      value: Number(project.id),
    })),
    [projects],
  );

  useEffect(() => {
    if (!open) return;
    setType('SQL');
    setProjectId(
      defaultProjectId ??
        (projects[0]?.id === undefined ? undefined : Number(projects[0].id)),
    );
  }, [defaultProjectId, open, projects]);

  return (
    <Modal
      open={open}
      title="新建数据开发任务"
      width={720}
      okText="下一步"
      cancelText="取消"
      okButtonProps={{ disabled: !projectId }}
      destroyOnClose
      onCancel={onCancel}
      onOk={() => {
        if (projectId) onNext(type, projectId);
      }}
    >
      <div className="pt-2">
        <div className="mb-5">
          <Typography.Text className="mb-2 block text-[13px] font-medium text-[#161823]">
            所属项目
          </Typography.Text>
          <Select
            value={projectId}
            options={projectOptions}
            className="w-full"
            placeholder="请选择项目"
            showSearch
            optionFilterProp="label"
            onChange={(value) => setProjectId(Number(value))}
          />
        </div>

        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#161823]">
          任务类型
        </Typography.Text>
        <div className="grid grid-cols-2 gap-3">
          {taskTypes.map((item) => {
            const selected = type === item.type;
            return (
              <button
                key={item.type}
                type="button"
                disabled={!item.enabled}
                onClick={() => item.enabled && setType(item.type)}
                className={[
                  'relative min-h-[104px] rounded-xl border p-4 text-left transition-all',
                  item.enabled
                    ? 'cursor-pointer bg-white hover:border-[#b8bec8]'
                    : 'cursor-not-allowed bg-[#fafafa] opacity-65',
                  selected && item.enabled
                    ? 'border-[#161823] shadow-[0_0_0_1px_#161823]'
                    : 'border-[#e4e7ec]',
                ].join(' ')}
              >
                <div className="mb-3 flex items-center justify-between">
                  <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#f5f5f5] text-[#161823]">
                    {item.icon}
                  </span>
                  {!item.enabled && <Tag className="!m-0">后续</Tag>}
                </div>
                <div className="text-[14px] font-semibold text-[#161823]">
                  {item.title}
                </div>
                <div className="mt-1 text-[12px] leading-5 text-[rgba(22,24,35,.5)]">
                  {item.description}
                </div>
              </button>
            );
          })}
        </div>
      </div>
    </Modal>
  );
}
