import { API_SUCCESS_CODE } from '@/services/http/response';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { fetchDataSourceAll } from '@/pages/data-source/service';
import type { DataSourceRecord } from '@/pages/data-source/types';
import { history } from '@umijs/max';
import {
  Alert,
  Button,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  ArrowLeft,
  CircleStop,
  Play,
  Plus,
  Save,
  Send,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import SqlCodeEditor from '../components/SqlCodeEditor';
import {
  cancelSqlTaskExecution,
  createSqlTask,
  getSqlTask,
  getSqlTaskExecution,
  listDevelopmentDirectories,
  listSqlTaskVersions,
  publishSqlTask,
  runSqlTask,
  updateSqlTask,
} from '../service';
import {
  SQL_PARAMETER_TYPES,
  TERMINAL_EXECUTION_STATUSES,
  type DevelopmentDirectory,
  type SqlParameterDefinition,
  type SqlTaskDefinition,
  type SqlTaskExecution,
  type SqlTaskSavePayload,
  type SqlTaskVersion,
} from '../types';

interface SqlTaskEditorProps {
  taskId?: number;
  initialProjectId?: number;
  initialDirectoryId?: number;
}

interface DraftState {
  id?: number;
  name: string;
  description: string;
  projectId?: number;
  directoryId?: number;
  dataSourceId?: number;
  sql: string;
  parameters: SqlParameterDefinition[];
  draftRevision: number;
  publishedVersionId?: number | null;
  latestVersionNo: number;
}

const EMPTY_DRAFT: DraftState = {
  name: '',
  description: '',
  sql: '',
  parameters: [],
  draftRevision: 0,
  latestVersionNo: 0,
};

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const toDraft = (task: SqlTaskDefinition): DraftState => ({
  id: Number(task.id),
  name: task.name || '',
  description: task.description || '',
  projectId: task.projectId ? Number(task.projectId) : undefined,
  directoryId: task.directoryId ? Number(task.directoryId) : undefined,
  dataSourceId: Number(task.dataSourceId),
  sql: task.sql || '',
  parameters: Array.isArray(task.parameters) ? task.parameters : [],
  draftRevision: Number(task.draftRevision || 0),
  publishedVersionId: task.publishedVersionId,
  latestVersionNo: Number(task.latestVersionNo || 0),
});

const formatTime = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const executionStatusText: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '运行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
  CANCELED: '已取消',
  TIMED_OUT: '超时',
  LOST: '执行丢失',
};

export default function SqlTaskEditor({
  taskId,
  initialProjectId,
  initialDirectoryId,
}: SqlTaskEditorProps) {
  const { projects } = useSecurityProject();
  const [draft, setDraft] = useState<DraftState>(() => ({
    ...EMPTY_DRAFT,
    projectId: initialProjectId,
    directoryId: initialDirectoryId,
  }));
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [versions, setVersions] = useState<SqlTaskVersion[]>([]);
  const [execution, setExecution] = useState<SqlTaskExecution>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [running, setRunning] = useState(false);
  const [runModalOpen, setRunModalOpen] = useState(false);
  const [runInput, setRunInput] = useState<Record<string, unknown>>({});

  const projectOptions = useMemo(
    () => projects.map((project) => ({
      label: project.projectName,
      value: Number(project.id),
    })),
    [projects],
  );
  const directoryOptions = useMemo(
    () => [
      { label: '/', value: 0 },
      ...directories.map((directory) => ({
        label: directory.path,
        value: Number(directory.id),
      })),
    ],
    [directories],
  );
  const dataSourceOptions = useMemo(
    () => dataSources
      .filter((item) => item.id !== undefined)
      .map((item) => ({
        value: Number(item.id),
        label: `${item.name || `#${item.id}`} · ${item.dbType || '-'}`,
      })),
    [dataSources],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [dataSourceResponse, directoryResponse] = await Promise.all([
        fetchDataSourceAll(),
        listDevelopmentDirectories(),
      ]);
      const sourceResult = responseData(dataSourceResponse, '查询数据源失败');
      setDataSources(sourceResult?.bizData || []);
      setDirectories(responseData(directoryResponse, '查询数据开发目录失败') || []);

      if (!taskId) return;
      const [taskResponse, versionResponse] = await Promise.all([
        getSqlTask(taskId),
        listSqlTaskVersions(taskId),
      ]);
      setDraft(toDraft(responseData(taskResponse, '查询 SQL 任务失败')));
      setVersions(responseData(versionResponse, '查询 SQL 版本失败') || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 SQL 任务失败');
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setRunInput((previous) => {
      const next: Record<string, unknown> = {};
      draft.parameters.forEach((parameter) => {
        if (Object.prototype.hasOwnProperty.call(previous, parameter.name)) {
          next[parameter.name] = previous[parameter.name];
        } else if (parameter.defaultValue !== undefined && parameter.defaultValue !== null) {
          next[parameter.name] = parameter.defaultValue;
        }
      });
      return next;
    });
  }, [draft.parameters]);

  useEffect(() => {
    if (!execution?.id || TERMINAL_EXECUTION_STATUSES.has(execution.status?.toUpperCase())) {
      return undefined;
    }
    const timer = window.setInterval(async () => {
      try {
        const response = await getSqlTaskExecution(Number(execution.id));
        setExecution(responseData(response, '查询 SQL 执行状态失败'));
      } catch {
        // Keep the last known execution state and retry on the next interval.
      }
    }, 1200);
    return () => window.clearInterval(timer);
  }, [execution?.id, execution?.status]);

  const updateDraftField = <K extends keyof DraftState>(
    key: K,
    value: DraftState[K],
  ) => setDraft((previous) => ({ ...previous, [key]: value }));

  const validateDraft = () => {
    if (!draft.name.trim()) throw new Error('请输入任务名称');
    if (!draft.dataSourceId) throw new Error('请选择数据源');
    if (!draft.sql.trim()) throw new Error('请输入 SQL');
    const names = new Set<string>();
    draft.parameters.forEach((parameter) => {
      const name = parameter.name.trim();
      if (!name) throw new Error('参数名称不能为空');
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) {
        throw new Error(`参数名称不合法：${name}`);
      }
      if (names.has(name)) throw new Error(`参数名称重复：${name}`);
      names.add(name);
    });
  };

  const savePayload = (): SqlTaskSavePayload => ({
    name: draft.name.trim(),
    description: draft.description.trim() || undefined,
    projectId: draft.projectId ? Number(draft.projectId) : draft.id ? 0 : undefined,
    directoryId: draft.directoryId ? Number(draft.directoryId) : 0,
    dataSourceId: Number(draft.dataSourceId),
    sql: draft.sql,
    parameters: draft.parameters.map((parameter) => ({
      ...parameter,
      name: parameter.name.trim(),
    })),
  });

  const persistDraft = async (showSuccess = true): Promise<SqlTaskDefinition> => {
    validateDraft();
    setSaving(true);
    try {
      const payload = savePayload();
      const response = draft.id
        ? await updateSqlTask(draft.id, {
            ...payload,
            baseRevision: draft.draftRevision,
          })
        : await createSqlTask(payload);
      const saved = responseData(response, '保存 SQL 草稿失败');
      setDraft(toDraft(saved));
      if (!draft.id) history.replace(`/data-development/task/${saved.id}`);
      if (showSuccess) message.success('草稿已保存');
      return saved;
    } finally {
      setSaving(false);
    }
  };

  const handleSave = async () => {
    try {
      await persistDraft(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 SQL 草稿失败');
    }
  };

  const executeRun = async (input: Record<string, unknown>) => {
    setRunning(true);
    try {
      const saved = await persistDraft(false);
      const response = await runSqlTask(Number(saved.id), input);
      setExecution(responseData(response, '启动 SQL 执行失败'));
      setRunModalOpen(false);
      message.success('SQL 已提交执行');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '启动 SQL 执行失败');
    } finally {
      setRunning(false);
    }
  };

  const handleRun = () => {
    try {
      validateDraft();
      if (draft.parameters.length > 0) {
        setRunModalOpen(true);
        return;
      }
      void executeRun({});
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'SQL 配置不完整');
    }
  };

  const handlePublish = async () => {
    setPublishing(true);
    try {
      const saved = await persistDraft(false);
      const published = responseData(
        await publishSqlTask(Number(saved.id), Number(saved.draftRevision)),
        '发布 SQL 版本失败',
      );
      const [taskResponse, versionResponse] = await Promise.all([
        getSqlTask(Number(saved.id)),
        listSqlTaskVersions(Number(saved.id)),
      ]);
      setDraft(toDraft(responseData(taskResponse, '刷新 SQL 任务失败')));
      setVersions(responseData(versionResponse, '刷新版本列表失败') || []);
      message.success(`已发布 V${published.versionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 SQL 版本失败');
    } finally {
      setPublishing(false);
    }
  };

  const handleCancelExecution = async () => {
    if (!execution?.id) return;
    try {
      setExecution(responseData(
        await cancelSqlTaskExecution(Number(execution.id)),
        '取消 SQL 执行失败',
      ));
      message.success('已提交取消');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消 SQL 执行失败');
    }
  };

  const addParameter = () => {
    setDraft((previous) => ({
      ...previous,
      parameters: [
        ...previous.parameters,
        { name: '', type: 'STRING', required: false, defaultValue: undefined },
      ],
    }));
  };

  const updateParameter = (
    index: number,
    patch: Partial<SqlParameterDefinition>,
  ) => {
    setDraft((previous) => ({
      ...previous,
      parameters: previous.parameters.map((parameter, itemIndex) =>
        itemIndex === index ? { ...parameter, ...patch } : parameter,
      ),
    }));
  };

  const removeParameter = (index: number) => {
    setDraft((previous) => ({
      ...previous,
      parameters: previous.parameters.filter((_, itemIndex) => itemIndex !== index),
    }));
  };

  const parameterColumns: ColumnsType<SqlParameterDefinition> = [
    {
      title: '参数名称',
      width: 220,
      render: (_, parameter, index) => (
        <Input
          value={parameter.name}
          placeholder="例如 biz_date"
          onChange={(event) => updateParameter(index, { name: event.target.value })}
        />
      ),
    },
    {
      title: '类型',
      width: 150,
      render: (_, parameter, index) => (
        <Select
          value={parameter.type}
          className="w-full"
          options={SQL_PARAMETER_TYPES.map((type) => ({ label: type, value: type }))}
          onChange={(type) => updateParameter(index, { type })}
        />
      ),
    },
    {
      title: '必填',
      width: 90,
      align: 'center',
      render: (_, parameter, index) => (
        <Switch
          size="small"
          checked={parameter.required}
          onChange={(required) => updateParameter(index, { required })}
        />
      ),
    },
    {
      title: '默认值',
      render: (_, parameter, index) => (
        <Input
          value={
            parameter.defaultValue === undefined || parameter.defaultValue === null
              ? ''
              : String(parameter.defaultValue)
          }
          placeholder="可选"
          onChange={(event) =>
            updateParameter(index, {
              defaultValue: event.target.value === '' ? undefined : event.target.value,
            })
          }
        />
      ),
    },
    {
      title: '',
      width: 50,
      align: 'center',
      render: (_, __, index) => (
        <Button
          type="text"
          icon={<Trash2 size={14} />}
          onClick={() => removeParameter(index)}
        />
      ),
    },
  ];

  const versionColumns: ColumnsType<SqlTaskVersion> = [
    {
      title: '版本',
      dataIndex: 'versionNo',
      width: 100,
      render: (value: number) => <span className="font-medium">V{value}</span>,
    },
    {
      title: '数据源',
      dataIndex: 'dataSourceId',
      width: 180,
      render: (value: number) =>
        dataSourceOptions.find((item) => item.value === Number(value))?.label || `#${value}`,
    },
    {
      title: '摘要',
      dataIndex: 'contentDigest',
      ellipsis: true,
      render: (value: string) => (
        <span className="font-mono text-[12px] text-[rgba(22,24,35,.55)]">
          {value}
        </span>
      ),
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      width: 180,
      render: (value?: string) => formatTime(value),
    },
  ];

  const renderExecutionResult = () => {
    if (!execution) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="运行 SQL 后在这里查看结果"
        />
      );
    }

    const status = String(execution.status || '').toUpperCase();
    const active = !TERMINAL_EXECUTION_STATUSES.has(status);
    const output = execution.output || {};
    const kind = String(output.kind || '').toUpperCase();

    const header = (
      <div className="mb-4 flex items-center justify-between gap-3">
        <Space size={8}>
          <Tag className="!m-0">{executionStatusText[status] || status}</Tag>
          <Typography.Text className="text-[12px] text-[rgba(22,24,35,.45)]">
            Execution #{execution.id}
          </Typography.Text>
          {execution.startTime && (
            <Typography.Text className="text-[12px] text-[rgba(22,24,35,.45)]">
              {formatTime(execution.startTime)}
            </Typography.Text>
          )}
        </Space>
        {active && (
          <Button
            size="small"
            icon={<CircleStop size={14} />}
            onClick={() => void handleCancelExecution()}
          >
            取消
          </Button>
        )}
      </div>
    );

    if (active) {
      return (
        <div>
          {header}
          <div className="flex min-h-[180px] items-center justify-center">
            <Spin tip="SQL 执行中..." />
          </div>
        </div>
      );
    }

    if (status !== 'SUCCEEDED') {
      return (
        <div>
          {header}
          <Alert
            type="error"
            showIcon
            message={executionStatusText[status] || status}
            description={execution.errorMessage || '执行未成功完成'}
          />
        </div>
      );
    }

    if (kind === 'UPDATE') {
      return (
        <div>
          {header}
          <div className="rounded-lg border border-[#e4e7ec] bg-[#fafafa] px-5 py-6">
            <div className="text-[12px] text-[rgba(22,24,35,.45)]">影响行数</div>
            <div className="mt-1 text-[26px] font-semibold text-[#161823]">
              {Number(output.affectedRows ?? execution.affectedRows ?? 0)}
            </div>
          </div>
        </div>
      );
    }

    const resultColumns = Array.isArray(output.columns) ? (output.columns as string[]) : [];
    const rows = Array.isArray(output.rows) ? (output.rows as Record<string, unknown>[]) : [];
    return (
      <div>
        {header}
        {output.truncated === true && (
          <Alert
            className="mb-3"
            type="info"
            showIcon
            message="结果已截断，当前最多预览 200 行"
          />
        )}
        <Table<Record<string, unknown>>
          rowKey={(_, index) => String(index)}
          size="small"
          bordered
          pagination={false}
          scroll={{ x: 'max-content', y: 320 }}
          dataSource={rows}
          columns={resultColumns.map((column) => ({
            title: column,
            dataIndex: column,
            key: column,
            minWidth: 140,
            render: (value: unknown) => {
              if (value === null || value === undefined) {
                return <span className="text-[rgba(22,24,35,.3)]">NULL</span>;
              }
              if (typeof value === 'object') return JSON.stringify(value);
              return String(value);
            },
          }))}
          locale={{ emptyText: '查询成功，无返回数据' }}
        />
      </div>
    );
  };

  const editorTabs = [
    {
      key: 'parameters',
      label: `参数配置${draft.parameters.length ? ` (${draft.parameters.length})` : ''}`,
      children: (
        <div>
          <div className="mb-3 flex items-center justify-between">
            <Typography.Text className="text-[12px] text-[rgba(22,24,35,.45)]">
              SQL 中使用 :name 引用值参数；第一阶段不支持动态表名替换。
            </Typography.Text>
            <Button size="small" icon={<Plus size={13} />} onClick={addParameter}>
              添加参数
            </Button>
          </div>
          <Table<SqlParameterDefinition>
            rowKey={(_, index) => String(index)}
            size="small"
            bordered
            pagination={false}
            columns={parameterColumns}
            dataSource={draft.parameters}
            locale={{ emptyText: '当前 SQL 无参数' }}
          />
        </div>
      ),
    },
    {
      key: 'result',
      label: '运行结果',
      children: renderExecutionResult(),
    },
    {
      key: 'versions',
      label: `版本历史${versions.length ? ` (${versions.length})` : ''}`,
      children: (
        <Table<SqlTaskVersion>
          rowKey="id"
          size="small"
          pagination={false}
          columns={versionColumns}
          dataSource={versions}
          locale={{ emptyText: '尚未发布版本' }}
        />
      ),
    },
  ];

  return (
    <section className="m-4 overflow-hidden rounded-xl border border-[#e4e7ec] bg-white">
      <Spin spinning={loading}>
        <header className="flex min-h-[64px] items-center justify-between gap-4 border-b border-[#e4e7ec] px-5 py-3">
          <div className="flex min-w-0 items-center gap-3">
            <Button
              type="text"
              icon={<ArrowLeft size={17} />}
              onClick={() => history.push('/data-development')}
            />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <Typography.Text className="truncate text-[15px] font-semibold text-[#161823]">
                  {draft.name || '新建 SQL 任务'}
                </Typography.Text>
                <Tag className="!m-0 !border-[#e4e7ec] !bg-[#f7f7f8] !text-[#161823]">
                  SQL
                </Tag>
                {draft.latestVersionNo > 0 ? (
                  <Tag className="!m-0">已发布 V{draft.latestVersionNo}</Tag>
                ) : (
                  <Tag className="!m-0">草稿</Tag>
                )}
              </div>
              <div className="mt-0.5 text-[11px] text-[rgba(22,24,35,.38)]">
                {draft.id ? `Task #${draft.id} · Draft r${draft.draftRevision}` : '配置完成后保存为草稿'}
              </div>
            </div>
          </div>
          <Space>
            <Button icon={<Save size={14} />} loading={saving} onClick={() => void handleSave()}>
              保存
            </Button>
            <Button icon={<Play size={14} />} loading={running} onClick={handleRun}>
              运行
            </Button>
            <Button
              type="primary"
              icon={<Send size={14} />}
              loading={publishing}
              onClick={() => void handlePublish()}
            >
              发布
            </Button>
          </Space>
        </header>

        <main className="p-5">
          <div className="mb-5 grid grid-cols-12 gap-4">
            <div className="col-span-3">
              <div className="mb-1.5 text-[12px] font-medium text-[#161823]">任务名称</div>
              <Input
                value={draft.name}
                maxLength={200}
                placeholder="请输入任务名称"
                onChange={(event) => updateDraftField('name', event.target.value)}
              />
            </div>
            <div className="col-span-3">
              <div className="mb-1.5 text-[12px] font-medium text-[#161823]">所属项目（可选）</div>
              <Select
                allowClear
                value={draft.projectId}
                className="w-full"
                showSearch
                optionFilterProp="label"
                placeholder="未归属项目"
                options={projectOptions}
                onChange={(value) => updateDraftField('projectId', value ? Number(value) : undefined)}
              />
            </div>
            <div className="col-span-3">
              <div className="mb-1.5 text-[12px] font-medium text-[#161823]">所属目录</div>
              <Select
                value={draft.directoryId ?? 0}
                className="w-full"
                showSearch
                optionFilterProp="label"
                placeholder="/"
                options={directoryOptions}
                onChange={(value) =>
                  updateDraftField('directoryId', Number(value) > 0 ? Number(value) : undefined)
                }
              />
            </div>
            <div className="col-span-3">
              <div className="mb-1.5 text-[12px] font-medium text-[#161823]">数据源</div>
              <Select
                value={draft.dataSourceId}
                className="w-full"
                showSearch
                optionFilterProp="label"
                placeholder="请选择数据源"
                options={dataSourceOptions}
                onChange={(value) => updateDraftField('dataSourceId', Number(value))}
              />
            </div>
            <div className="col-span-12">
              <div className="mb-1.5 text-[12px] font-medium text-[#161823]">任务描述</div>
              <Input
                value={draft.description}
                maxLength={1000}
                placeholder="可选，说明任务用途或数据加工逻辑"
                onChange={(event) => updateDraftField('description', event.target.value)}
              />
            </div>
          </div>

          <div className="mb-5">
            <div className="mb-2 flex items-center justify-between">
              <div>
                <div className="text-[13px] font-semibold text-[#161823]">SQL 配置</div>
                <div className="mt-0.5 text-[11px] text-[rgba(22,24,35,.38)]">
                  当前阶段一个任务绑定一个数据源和一段 SQL。
                </div>
              </div>
              <Typography.Text className="text-[11px] text-[rgba(22,24,35,.38)]">
                使用 :param 绑定参数
              </Typography.Text>
            </div>
            <SqlCodeEditor
              value={draft.sql}
              minHeight={360}
              onChange={(value) => updateDraftField('sql', value)}
            />
          </div>

          <div className="border-t border-[#eceef1] pt-2">
            <Tabs items={editorTabs} />
          </div>
        </main>
      </Spin>

      <Modal
        open={runModalOpen}
        title="运行参数"
        okText="运行"
        cancelText="取消"
        confirmLoading={running}
        onCancel={() => setRunModalOpen(false)}
        onOk={() => void executeRun(runInput)}
      >
        <div className="space-y-4 pt-2">
          {draft.parameters.map((parameter, index) => (
            <div key={`${parameter.name || 'parameter'}-${index}`}>
              <div className="mb-1.5 flex items-center gap-2 text-[12px] font-medium text-[#161823]">
                {parameter.name || '未命名参数'}
                <Tag className="!m-0">{parameter.type}</Tag>
                {parameter.required && <span className="text-[rgba(254,44,85,1)]">*</span>}
              </div>
              <Input
                value={
                  runInput[parameter.name] === undefined || runInput[parameter.name] === null
                    ? ''
                    : String(runInput[parameter.name])
                }
                placeholder={
                  parameter.defaultValue === undefined
                    ? '请输入运行参数'
                    : `默认值：${String(parameter.defaultValue)}`
                }
                onChange={(event) =>
                  setRunInput((previous) => ({
                    ...previous,
                    [parameter.name]: event.target.value,
                  }))
                }
              />
            </div>
          ))}
        </div>
      </Modal>
    </section>
  );
}
