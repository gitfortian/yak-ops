import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';
import {
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Spin,
  Switch,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { Database, Server, SquareTerminal } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import {
  computeEnvironmentApi,
  type ComputeEnvironment,
  type ComputeEnvironmentPayload,
} from '../services/computeEnvironments';

interface FormValues {
  name: string;
  enabled: boolean;
  makeDefault: boolean;
  restUrl: string;
  flinkHome: string;
  flinkCdcHome: string;
  javaHome?: string;
  flinkVersion: string;
  flinkCdcVersion: string;
}

const DEFAULT_FORM_VALUES: FormValues = {
  name: '',
  enabled: true,
  makeDefault: false,
  restUrl: 'http://127.0.0.1:8081',
  flinkHome: '/opt/flink',
  flinkCdcHome: '/opt/flink-cdc',
  javaHome: '',
  flinkVersion: '1.20.5',
  flinkCdcVersion: '3.6.0',
};

const errorText = (error: unknown, fallback: string): string =>
  error instanceof Error && error.message ? error.message : fallback;

const runtimeLabel = (environment?: ComputeEnvironment | null) =>
  environment?.submitterType === 'SSH' ? 'SSH 远程执行' : '本机执行';

const ComputeEngineSettingsPanel = () => {
  const [form] = Form.useForm<FormValues>();
  const [environments, setEnvironments] = useState<ComputeEnvironment[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ComputeEnvironment | null>(null);
  const [switchingId, setSwitchingId] = useState<number | null>(null);
  const [defaultingId, setDefaultingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await computeEnvironmentApi.list();
      setEnvironments(response.data ?? []);
    } catch (error) {
      setEnvironments([]);
      message.error(errorText(error, '运行环境加载失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue(DEFAULT_FORM_VALUES);
    setModalOpen(true);
  };

  const openEdit = (environment: ComputeEnvironment) => {
    setEditing(environment);
    form.setFieldsValue({
      name: environment.name,
      enabled: environment.enabled,
      makeDefault: environment.defaultEnvironment,
      restUrl: environment.config.restUrl,
      flinkHome: environment.config.flinkHome,
      flinkCdcHome: environment.config.flinkCdcHome,
      javaHome: environment.config.javaHome ?? '',
      flinkVersion: environment.config.flinkVersion,
      flinkCdcVersion: environment.config.flinkCdcVersion,
    });
    setModalOpen(true);
  };

  const save = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }

    const payload: ComputeEnvironmentPayload = {
      name: values.name.trim(),
      enabled: values.enabled,
      makeDefault: values.makeDefault,
      config: {
        restUrl: values.restUrl.trim(),
        flinkHome: values.flinkHome.trim(),
        flinkCdcHome: values.flinkCdcHome.trim(),
        javaHome: values.javaHome?.trim() || undefined,
        flinkVersion: values.flinkVersion.trim(),
        flinkCdcVersion: values.flinkCdcVersion.trim(),
      },
    };

    setSaving(true);
    try {
      if (editing) {
        await computeEnvironmentApi.update(editing.id, payload);
        message.success('运行环境已更新');
      } else {
        await computeEnvironmentApi.create(payload);
        message.success('运行环境已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error, editing ? '运行环境更新失败' : '运行环境创建失败'));
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (environment: ComputeEnvironment, enabled: boolean) => {
    setSwitchingId(environment.id);
    try {
      await computeEnvironmentApi.setEnabled(environment.id, enabled);
      message.success(enabled ? '运行环境已启用' : '运行环境已停用');
      await load();
    } catch (error) {
      message.error(errorText(error, '运行环境状态更新失败'));
    } finally {
      setSwitchingId(null);
    }
  };

  const setDefault = async (environment: ComputeEnvironment) => {
    setDefaultingId(environment.id);
    try {
      await computeEnvironmentApi.setDefault(environment.id);
      message.success(`已将 ${environment.name} 设为默认运行环境`);
      await load();
    } catch (error) {
      message.error(errorText(error, '默认运行环境切换失败'));
    } finally {
      setDefaultingId(null);
    }
  };

  const remove = async (environment: ComputeEnvironment) => {
    setDeletingId(environment.id);
    try {
      await computeEnvironmentApi.remove(environment.id);
      message.success('运行环境已删除');
      await load();
    } catch (error) {
      message.error(errorText(error, '运行环境删除失败'));
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="text-[13px] text-[#344054]">
      <div className="mb-6 flex items-start justify-between gap-6">
        <div>
          <div className="text-[18px] font-semibold text-[#161823]">计算引擎</div>
          <div className="mt-1.5 max-w-[680px] text-[12px] leading-5 text-[#667085]">
            集中管理实时同步使用的运行环境。任务默认使用标记为“默认”的环境，Flink 的连接与本机运行路径只需配置一次。
          </div>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建运行环境
        </Button>
      </div>

      <div className="mb-5 rounded-lg border border-[#e4e7ec] bg-[#f9fafb] px-4 py-3 text-[12px] leading-5 text-[#667085]">
        当前阶段采用 Flink CDC + Remote Cluster。切换或修改默认环境前，需要先停止正在运行的实时同步任务，避免任务被错误地管理到其他 Flink 集群。
      </div>

      {loading ? (
        <div className="flex h-56 items-center justify-center">
          <Spin size="small" />
        </div>
      ) : environments.length === 0 ? (
        <div className="rounded-xl border border-dashed border-[#d0d5dd] py-14">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无运行环境">
            <Button type="primary" onClick={openCreate}>
              新建运行环境
            </Button>
          </Empty>
        </div>
      ) : (
        <div className="space-y-4">
          {environments.map((environment) => (
            <div
              key={environment.id}
              className="rounded-xl border border-[#e4e7ec] bg-white px-5 py-5 shadow-[0_1px_2px_rgba(16,24,40,0.03)]"
            >
              <div className="flex items-start justify-between gap-5">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="truncate text-[15px] font-semibold text-[#101828]">
                      {environment.name}
                    </span>
                    {environment.defaultEnvironment && (
                      <Tag color="gold" icon={<StarFilled />} className="!mr-0">
                        默认
                      </Tag>
                    )}
                    <Tag color={environment.enabled ? 'green' : 'default'} className="!mr-0">
                      {environment.enabled ? '已启用' : '已停用'}
                    </Tag>
                  </div>

                  <div className="mt-3 flex flex-wrap gap-2">
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-[#f2f4f7] px-2.5 py-1 text-[12px] text-[#475467]">
                      <Database size={13} /> Flink CDC
                    </span>
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-[#f2f4f7] px-2.5 py-1 text-[12px] text-[#475467]">
                      <Server size={13} /> Remote Cluster
                    </span>
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-[#f2f4f7] px-2.5 py-1 text-[12px] text-[#475467]">
                      <SquareTerminal size={13} /> {runtimeLabel(environment)}
                    </span>
                  </div>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  <span className="text-[12px] text-[#98a2b3]">启用</span>
                  <Tooltip
                    title={
                      environment.defaultEnvironment
                        ? '默认运行环境必须保持启用'
                        : undefined
                    }
                  >
                    <Switch
                      size="small"
                      checked={environment.enabled}
                      disabled={environment.defaultEnvironment}
                      loading={switchingId === environment.id}
                      onChange={(checked) => void toggleEnabled(environment, checked)}
                    />
                  </Tooltip>
                </div>
              </div>

              <div className="mt-5 grid gap-x-8 gap-y-3 border-t border-[#f2f4f7] pt-4 md:grid-cols-2">
                <div>
                  <div className="text-[11px] text-[#98a2b3]">Flink REST</div>
                  <div
                    className="mt-1 truncate font-mono text-[12px] text-[#344054]"
                    title={environment.config.restUrl}
                  >
                    {environment.config.restUrl}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] text-[#98a2b3]">运行版本</div>
                  <div className="mt-1 text-[12px] text-[#344054]">
                    Flink {environment.config.flinkVersion} · CDC {environment.config.flinkCdcVersion}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] text-[#98a2b3]">Flink Home</div>
                  <div
                    className="mt-1 truncate font-mono text-[12px] text-[#344054]"
                    title={environment.config.flinkHome}
                  >
                    {environment.config.flinkHome}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] text-[#98a2b3]">Flink CDC Home</div>
                  <div
                    className="mt-1 truncate font-mono text-[12px] text-[#344054]"
                    title={environment.config.flinkCdcHome}
                  >
                    {environment.config.flinkCdcHome}
                  </div>
                </div>
              </div>

              <div className="mt-4 flex items-center justify-end gap-1 border-t border-[#f2f4f7] pt-3">
                {!environment.defaultEnvironment && (
                  <Tooltip title={!environment.enabled ? '请先启用该运行环境' : undefined}>
                    <Button
                      type="link"
                      size="small"
                      icon={<StarOutlined />}
                      disabled={!environment.enabled}
                      loading={defaultingId === environment.id}
                      onClick={() => void setDefault(environment)}
                    >
                      设为默认
                    </Button>
                  </Tooltip>
                )}
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEdit(environment)}
                >
                  编辑
                </Button>
                <Popconfirm
                  title="删除运行环境"
                  description={
                    environment.defaultEnvironment
                      ? '默认运行环境不能删除，请先切换默认环境。'
                      : `确定删除 ${environment.name} 吗？`
                  }
                  disabled={environment.defaultEnvironment}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true, loading: deletingId === environment.id }}
                  onConfirm={() => remove(environment)}
                >
                  <Tooltip title={environment.defaultEnvironment ? '请先切换默认环境' : undefined}>
                    <Button
                      type="link"
                      size="small"
                      danger
                      disabled={environment.defaultEnvironment}
                      icon={<DeleteOutlined />}
                    >
                      删除
                    </Button>
                  </Tooltip>
                </Popconfirm>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        title={editing ? '编辑运行环境' : '新建运行环境'}
        open={modalOpen}
        width={720}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => !saving && setModalOpen(false)}
      >
        <Form<FormValues>
          form={form}
          layout="vertical"
          variant="filled"
          initialValues={DEFAULT_FORM_VALUES}
          className="mt-5"
        >
          <div className="rounded-lg border border-[#eaecf0] px-4 py-4">
            <div className="mb-4 text-[13px] font-semibold text-[#1d2939]">基础信息</div>
            <Form.Item
              name="name"
              label="环境名称"
              rules={[
                { required: true, message: '请输入环境名称' },
                { max: 120, message: '环境名称不能超过 120 个字符' },
              ]}
            >
              <Input placeholder="例如：生产实时环境" />
            </Form.Item>
            <div className="grid grid-cols-2 gap-5">
              <Form.Item
                name="enabled"
                label="启用环境"
                valuePropName="checked"
                className="!mb-0"
              >
                <Switch disabled={editing?.defaultEnvironment} />
              </Form.Item>
              <Form.Item
                name="makeDefault"
                label="设为默认"
                valuePropName="checked"
                className="!mb-0"
              >
                <Switch disabled={editing?.defaultEnvironment} />
              </Form.Item>
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-[#eaecf0] px-4 py-4">
            <div className="mb-3 text-[13px] font-semibold text-[#1d2939]">运行方式</div>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-[#f9fafb] px-3 py-3">
                <div className="text-[11px] text-[#98a2b3]">引擎</div>
                <div className="mt-1 font-medium text-[#344054]">Flink CDC</div>
              </div>
              <div className="rounded-lg bg-[#f9fafb] px-3 py-3">
                <div className="text-[11px] text-[#98a2b3]">部署模式</div>
                <div className="mt-1 font-medium text-[#344054]">Remote Cluster</div>
              </div>
              <div className="rounded-lg bg-[#f9fafb] px-3 py-3">
                <div className="text-[11px] text-[#98a2b3]">任务提交方式</div>
                <div className="mt-1 font-medium text-[#344054]">{runtimeLabel(editing)}</div>
              </div>
            </div>
            {editing?.submitterType === 'SSH' && (
              <div className="mt-3 text-[11px] leading-5 text-[#667085]">
                此环境继承了现有 SSH 启动配置；本阶段只在页面管理通用 Flink 运行参数，SSH 凭据仍由启动配置提供。
              </div>
            )}
          </div>

          <div className="mt-4 rounded-lg border border-[#eaecf0] px-4 py-4">
            <div className="mb-4 text-[13px] font-semibold text-[#1d2939]">Flink 集群</div>
            <Form.Item
              name="restUrl"
              label="JobManager REST URL"
              rules={[{ required: true, message: '请输入 Flink REST URL' }]}
            >
              <Input placeholder="http://127.0.0.1:8081" />
            </Form.Item>
            <div className="grid gap-4 sm:grid-cols-2">
              <Form.Item
                name="flinkVersion"
                label="Flink 版本"
                rules={[{ required: true, message: '请输入 Flink 版本' }]}
              >
                <Input placeholder="1.20.5" />
              </Form.Item>
              <Form.Item
                name="flinkCdcVersion"
                label="Flink CDC 版本"
                rules={[{ required: true, message: '请输入 Flink CDC 版本' }]}
              >
                <Input placeholder="3.6.0" />
              </Form.Item>
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-[#eaecf0] px-4 py-4">
            <div className="mb-4 text-[13px] font-semibold text-[#1d2939]">本机运行路径</div>
            <Form.Item
              name="flinkHome"
              label="Flink Home"
              rules={[{ required: true, message: '请输入 Flink Home' }]}
            >
              <Input placeholder="/opt/flink" />
            </Form.Item>
            <Form.Item
              name="flinkCdcHome"
              label="Flink CDC Home"
              rules={[{ required: true, message: '请输入 Flink CDC Home' }]}
            >
              <Input placeholder="/opt/flink-cdc" />
            </Form.Item>
            <Form.Item name="javaHome" label="Java Home（可选）" className="!mb-0">
              <Input placeholder="/usr/lib/jvm/java-17" />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default ComputeEngineSettingsPanel;
