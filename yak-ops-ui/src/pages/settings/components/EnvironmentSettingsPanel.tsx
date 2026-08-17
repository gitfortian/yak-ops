import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Input, message, Modal, Popconfirm, Spin, Tag } from 'antd';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useState } from 'react';

import {
  deleteSystemEnvVar,
  listSystemEnvVars,
  saveSystemEnvVars,
  type EnvVarEntry,
} from '@/services/system/envVars';

const labelClassName = 'mb-2 block text-[13px] font-medium text-[#344054]';

interface SettingSectionProps {
  title: string;
  description: string;
  children: ReactNode;
}

const SettingSection = ({ title, description, children }: SettingSectionProps) => (
  <section className="border-t border-[#eaecf0] py-6 first:border-t-0 first:pt-0">
    <div className="mb-5">
      <div className="text-[14px] font-semibold text-[#1d2939]">{title}</div>
      <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">{description}</div>
    </div>
    {children}
  </section>
);

const EnvironmentSettingsPanel = () => {
  const [entries, setEntries] = useState<EnvVarEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [searchText, setSearchText] = useState('');

  // Add modal state
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');

  // Edit state: key being edited inline
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editingValue, setEditingValue] = useState('');

  const fetchEntries = useCallback(() => {
    setLoading(true);
    listSystemEnvVars()
      .then((response) => {
        setEntries(response.data || []);
      })
      .catch(() => message.warning('环境变量读取失败'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchEntries();
  }, [fetchEntries]);

  const handleAdd = async () => {
    const trimmedKey = newKey.trim();
    if (!trimmedKey) {
      message.warning('变量名不能为空');
      return;
    }
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(trimmedKey)) {
      message.warning('变量名只能包含字母、数字和下划线，且不能以数字开头');
      return;
    }
    const existing = entries.find((e) => e.key === trimmedKey);
    if (existing && existing.source === 'app') {
      message.warning(`变量 ${trimmedKey} 已存在，请直接编辑`);
      setAddModalOpen(false);
      setNewKey('');
      setNewValue('');
      return;
    }

    setSaving(true);
    try {
      await saveSystemEnvVars({ [trimmedKey]: newValue });
      message.success('环境变量已添加');
      setAddModalOpen(false);
      setNewKey('');
      setNewValue('');
      fetchEntries();
    } catch {
      message.error('添加失败');
    } finally {
      setSaving(false);
    }
  };

  const handleSaveEdit = async (key: string, value: string) => {
    setSaving(true);
    try {
      await saveSystemEnvVars({ [key]: value });
      setEditingKey(null);
      setEditingValue('');
      fetchEntries();
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (key: string) => {
    setSaving(true);
    try {
      await deleteSystemEnvVar(key);
      message.success('环境变量已删除');
      fetchEntries();
    } catch {
      message.error('删除失败');
    } finally {
      setSaving(false);
    }
  };

  const startEditing = (entry: EnvVarEntry) => {
    setEditingKey(entry.key);
    setEditingValue(entry.value);
  };

  const cancelEditing = () => {
    setEditingKey(null);
    setEditingValue('');
  };

  const filteredAppEntries = entries
    .filter((e) => e.source === 'app')
    .filter((e) => !searchText || e.key.toLowerCase().includes(searchText.toLowerCase()) || e.value.toLowerCase().includes(searchText.toLowerCase()));

  const filteredSystemEntries = entries
    .filter((e) => e.source === 'system')
    .filter((e) => !searchText || e.key.toLowerCase().includes(searchText.toLowerCase()));

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  return (
    <div className="text-[13px] text-[#344054]">
      <div className="mb-6 flex items-start justify-between gap-6">
        <div>
          <div className="text-[17px] font-semibold text-[#161823]">环境变量设置</div>
          <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">
            管理系统环境变量，配置后任务运行时将自动使用。应用配置优先级高于操作系统环境变量。
          </div>
        </div>
        <span className="shrink-0 pt-1 text-[11px] text-[#98a2b3]">
          {saving ? '保存中…' : '已同步'}
        </span>
      </div>

      {/* App-configured environment variables */}
      <SettingSection
        title="应用配置"
        description="通过设置页面管理的环境变量，保存后立即生效。运行时优先级高于系统默认值。"
      >
        <div className="mb-4 max-w-[520px]">
          <Input
            placeholder="搜索变量名或值…"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            allowClear
          />
        </div>

        {filteredAppEntries.length === 0 ? (
          <div className="rounded-md border border-dashed border-[#d1d5db] px-4 py-8 text-center text-[12px] text-[#98a2b3]">
            {searchText ? '没有匹配的环境变量' : '暂无应用配置的环境变量，点击下方按钮添加'}
          </div>
        ) : (
          <div className="space-y-2">
            {filteredAppEntries.map((entry) => (
              <div
                key={entry.key}
                className="group flex items-center gap-3 rounded-md border border-[#eaecf0] px-4 py-3 transition-colors hover:border-[#d1d5db]"
              >
                <div className="min-w-0 flex-1">
                  {editingKey === entry.key ? (
                    <div className="flex items-center gap-2">
                      <span className="shrink-0 font-mono text-[13px] font-semibold text-[#161823]">
                        {entry.key}
                      </span>
                      <span className="text-[#98a2b3]">=</span>
                      <Input
                        className="flex-1"
                        value={editingValue}
                        onChange={(e) => setEditingValue(e.target.value)}
                        onPressEnter={() => handleSaveEdit(entry.key, editingValue)}
                        size="small"
                      />
                      <Button
                        type="link"
                        size="small"
                        onClick={() => handleSaveEdit(entry.key, editingValue)}
                        loading={saving}
                      >
                        保存
                      </Button>
                      <Button type="link" size="small" onClick={cancelEditing}>
                        取消
                      </Button>
                    </div>
                  ) : (
                    <div
                      className="flex cursor-pointer items-center gap-2"
                      onClick={() => startEditing(entry)}
                    >
                      <span className="shrink-0 font-mono text-[13px] font-semibold text-[#161823]">
                        {entry.key}
                      </span>
                      <span className="text-[#98a2b3]">=</span>
                      <span className="min-w-0 flex-1 truncate font-mono text-[13px] text-[#475569]">
                        {entry.value}
                      </span>
                    </div>
                  )}
                </div>

                <Tag
                  color="blue"
                  className="shrink-0 border-0 bg-[#eff8ff] text-[11px] text-[#2563eb]"
                >
                  应用配置
                </Tag>

                <Popconfirm
                  title={`确定删除 ${entry.key}？`}
                  onConfirm={() => handleDelete(entry.key)}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    className="shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
                  />
                </Popconfirm>
              </div>
            ))}
          </div>
        )}

        <div className="mt-4">
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={() => setAddModalOpen(true)}
          >
            添加环境变量
          </Button>
        </div>
      </SettingSection>

      {/* System default environment variables (read-only) */}
      <SettingSection
        title="系统默认"
        description="操作系统级别的环境变量（只读）。可在应用配置中添加同名变量进行覆盖。"
      >
        {filteredSystemEntries.length === 0 ? (
          <div className="rounded-md border border-dashed border-[#d1d5db] px-4 py-8 text-center text-[12px] text-[#98a2b3]">
            {searchText ? '没有匹配的系统环境变量' : '未检测到系统环境变量'}
          </div>
        ) : (
          <div className="max-h-[360px] overflow-y-auto rounded-md border border-[#eaecf0]">
            {filteredSystemEntries.map((entry) => (
              <div
                key={entry.key}
                className="flex items-center gap-3 border-b border-[#f5f5f5] px-4 py-2.5 last:border-b-0"
              >
                <span className="shrink-0 font-mono text-[12px] font-semibold text-[#475569]">
                  {entry.key}
                </span>
                <span className="text-[#d1d5db]">=</span>
                <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-[#98a2b3]">
                  {entry.value}
                </span>
                <Tag
                  className="shrink-0 border-0 bg-[#f5f5f5] text-[11px] text-[#98a2b3]"
                >
                  系统默认
                </Tag>
              </div>
            ))}
          </div>
        )}
      </SettingSection>

      {/* Add Modal */}
      <Modal
        title="添加环境变量"
        open={addModalOpen}
        onOk={handleAdd}
        onCancel={() => {
          setAddModalOpen(false);
          setNewKey('');
          setNewValue('');
        }}
        okText="添加"
        cancelText="取消"
        confirmLoading={saving}
        width={520}
      >
        <div className="space-y-4 py-2">
          <div>
            <label className={labelClassName}>变量名</label>
            <Input
              placeholder="例如 PYTHON_HOME"
              value={newKey}
              onChange={(e) => setNewKey(e.target.value)}
              className="font-mono"
            />
            <div className="mt-1 text-[11px] text-[#98a2b3]">
              只能包含字母、数字和下划线，不能以数字开头
            </div>
          </div>
          <div>
            <label className={labelClassName}>变量值</label>
            <Input
              placeholder="例如 C:\Python312 或 /usr/bin/python3"
              value={newValue}
              onChange={(e) => setNewValue(e.target.value)}
              className="font-mono"
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default EnvironmentSettingsPanel;
