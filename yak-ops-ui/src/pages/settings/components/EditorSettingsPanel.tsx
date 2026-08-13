import { Button, Input, InputNumber, Radio, Select, Spin, message } from 'antd';
import type { ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';

import {
  DEFAULT_YAK_EDITOR_SETTINGS,
  YAK_EDITOR_FONTS,
  YAK_EDITOR_THEMES,
  setYakEditorSettings,
  type YakEditorSettings,
} from '../../data-development/editors/sql/editorSettings';
import {
  getDevelopmentEditorSettings,
  saveDevelopmentEditorSettings,
} from '../../data-development/service';

const labelClassName = 'mb-2 block text-[13px] font-medium text-[#344054]';
const rowClassName = 'grid grid-cols-1 gap-x-8 gap-y-5 lg:grid-cols-2';

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

const EditorSettingsPanel = () => {
  const [settings, setSettings] = useState<YakEditorSettings>(DEFAULT_YAK_EDITOR_SETTINGS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    let active = true;
    getDevelopmentEditorSettings()
      .then((response) => {
        if (!active) return;
        const next = { ...DEFAULT_YAK_EDITOR_SETTINGS, ...(response.data || {}) };
        setSettings(next);
        setYakEditorSettings(next);
      })
      .catch(() => message.warning('编辑器设置读取失败，已使用默认设置'))
      .finally(() => active && setLoading(false));

    return () => {
      active = false;
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, []);

  const persist = (next: YakEditorSettings) => {
    setSettings(next);
    setYakEditorSettings(next);
    if (saveTimer.current) clearTimeout(saveTimer.current);

    saveTimer.current = setTimeout(async () => {
      setSaving(true);
      try {
        const response = await saveDevelopmentEditorSettings(next);
        const saved = { ...DEFAULT_YAK_EDITOR_SETTINGS, ...(response.data || next) };
        setSettings(saved);
        setYakEditorSettings(saved);
      } catch {
        message.error('编辑器设置保存失败');
      } finally {
        setSaving(false);
      }
    }, 450);
  };

  const patch = <K extends keyof YakEditorSettings>(key: K, value: YakEditorSettings[K]) =>
    persist({ ...settings, [key]: value });

  const booleanRadio = (
    key: keyof YakEditorSettings,
    enabledText = '显示',
    disabledText = '隐藏',
  ) => (
    <Radio.Group
      value={settings[key] as boolean}
      onChange={(event) => patch(key, event.target.value as never)}
    >
      <Radio value>{enabledText}</Radio>
      <Radio value={false}>{disabledText}</Radio>
    </Radio.Group>
  );

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
          <div className="text-[17px] font-semibold text-[#161823]">编辑器设置</div>
          <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">
            SQL 编辑器全局偏好，修改后会应用到当前账号打开的所有 SQL 编辑器。
          </div>
        </div>
        <span className="shrink-0 pt-1 text-[11px] text-[#98a2b3]">
          {saving ? '保存中…' : '已同步'}
        </span>
      </div>

      <SettingSection
        title="外观"
        description="设置编辑器主题、字体和文本显示尺寸。"
      >
        <div className="space-y-5">
          <div className="max-w-[520px]">
            <label className={labelClassName}>编辑器主题</label>
            <Select
              className="w-full"
              value={settings.theme}
              options={YAK_EDITOR_THEMES.map((theme) => ({
                label: theme.name,
                value: theme.name,
              }))}
              onChange={(value) => patch('theme', value)}
            />
          </div>

          <div className={rowClassName}>
            <div>
              <label className={labelClassName}>编辑器字体</label>
              <Select
                className="w-full"
                value={settings.fontFamily}
                options={YAK_EDITOR_FONTS.map((font) => ({ label: font, value: font }))}
                onChange={(value) => patch('fontFamily', value)}
              />
            </div>
            <div>
              <label className={labelClassName}>自定义字体</label>
              <Input
                value={settings.customFontFamily}
                placeholder="例如 Cascadia Code"
                onChange={(event) => patch('customFontFamily', event.target.value)}
              />
            </div>
          </div>

          <div className={rowClassName}>
            <div>
              <label className={labelClassName}>字体大小</label>
              <InputNumber
                className="w-full"
                min={10}
                max={32}
                addonAfter="px"
                value={settings.fontSize}
                onChange={(value) => patch('fontSize', value || 14)}
              />
            </div>
            <div>
              <label className={labelClassName}>行高</label>
              <InputNumber
                className="w-full"
                min={1}
                max={3}
                step={0.1}
                value={settings.lineHeight}
                onChange={(value) => patch('lineHeight', value || 1.6)}
              />
            </div>
          </div>
        </div>
      </SettingSection>

      <SettingSection
        title="编辑器行为"
        description="控制行号、缩略图、换行、代码折叠和辅助显示。"
      >
        <div className={rowClassName}>
          <div>
            <label className={labelClassName}>显示行号</label>
            {booleanRadio('showLineNumber')}
          </div>
          <div>
            <label className={labelClassName}>显示缩略图</label>
            {booleanRadio('showMinimap')}
          </div>
          <div>
            <label className={labelClassName}>自动换行</label>
            {booleanRadio('wordWrap', '启用', '禁用')}
          </div>
          <div>
            <label className={labelClassName}>代码折叠</label>
            {booleanRadio('folding')}
          </div>
          <div>
            <label className={labelClassName}>行高亮</label>
            <Select
              className="w-full"
              value={settings.renderLineHighlight}
              options={[
                { label: 'LINE', value: 'line' },
                { label: 'GUTTER', value: 'gutter' },
                { label: 'ALL', value: 'all' },
                { label: 'NONE', value: 'none' },
              ]}
              onChange={(value) => patch('renderLineHighlight', value)}
            />
          </div>
          <div>
            <label className={labelClassName}>空白字符显示</label>
            <Select
              className="w-full"
              value={settings.renderWhitespace}
              options={[
                { label: '不显示', value: 'none' },
                { label: '边界', value: 'boundary' },
                { label: '选中区域', value: 'selection' },
                { label: '行尾', value: 'trailing' },
                { label: '全部', value: 'all' },
              ]}
              onChange={(value) => patch('renderWhitespace', value)}
            />
          </div>
        </div>
      </SettingSection>

      <SettingSection
        title="SQL"
        description="设置 SQL 关键字显示和元数据补全偏好。"
      >
        <div className={rowClassName}>
          <div>
            <label className={labelClassName}>关键字大小写</label>
            <Radio.Group
              value={settings.keywordCase}
              onChange={(event) => patch('keywordCase', event.target.value)}
            >
              <Radio value="upper">大写</Radio>
              <Radio value="lower">小写</Radio>
            </Radio.Group>
          </div>
          <div>
            <label className={labelClassName}>全限定补全</label>
            <Select
              className="w-full"
              value={settings.sqlCompletionFQN}
              options={[
                { label: '不补全限定名', value: 'none' },
                { label: '补全表名', value: 'table' },
                { label: '补全完整限定名', value: 'all' },
              ]}
              onChange={(value) => patch('sqlCompletionFQN', value)}
            />
          </div>
        </div>
      </SettingSection>

      <div className="border-t border-[#eaecf0] pt-6">
        <Button onClick={() => persist(DEFAULT_YAK_EDITOR_SETTINGS)}>
          恢复默认设置
        </Button>
      </div>
    </div>
  );
};

export default EditorSettingsPanel;
