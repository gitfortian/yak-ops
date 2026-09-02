import { YakButton } from '@/components/ui';
import { BRAND_THEME } from '@/styles/brand';
import {
  ConfigProvider,
  Form,
  Input,
  InputNumber,
  Switch,
  type FormInstance,
} from 'antd';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';

export interface ConsumerEditorValues {
  name: string;
  description?: string;
  enabled: boolean;
  defaultRateLimitPerMinute: number;
}

interface ConsumerEditorProps {
  form: FormInstance<ConsumerEditorValues>;
  editing: boolean;
  saving: boolean;
  onCancel: () => void;
  onSubmit: (values: ConsumerEditorValues) => void | Promise<void>;
}

const SECTION_ITEMS = [
  { key: 'consumer-basic', label: '基本信息' },
  { key: 'consumer-runtime', label: '调用配置' },
] as const;

type SectionKey = (typeof SECTION_ITEMS)[number]['key'];

function EditorSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="overflow-hidden rounded-xl bg-white">
      <header className="px-7 pt-5">
        <h2 className="m-0 text-[17px] font-semibold leading-6 text-[#161823]">
          {title}
        </h2>
      </header>
      <div className="px-7 py-6">{children}</div>
    </section>
  );
}

function EditorField({
  label,
  required = false,
  children,
}: {
  label: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="grid grid-cols-[116px_minmax(0,1fr)] items-start gap-5 max-md:grid-cols-1 max-md:gap-2">
      <div className="pt-2.5 text-[13px] font-medium text-[#344054]">
        {label}
        {required ? <span className="ml-1 text-[var(--yak-brand-color)]">*</span> : null}
      </div>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

function SectionNavigator({
  activeKey,
  onSelect,
}: {
  activeKey: SectionKey;
  onSelect: (key: SectionKey) => void;
}) {
  return (
    <nav className="rounded-xl bg-white px-3 py-4" aria-label="配置区块定位">
      <div className="mb-3 px-2 text-[12px] font-semibold text-[#344054]">快速定位</div>
      <div className="relative">
        <span className="absolute bottom-4 left-[13px] top-4 w-px bg-[#e4e7ec]" aria-hidden />
        <div className="space-y-1">
          {SECTION_ITEMS.map((item) => {
            const active = activeKey === item.key;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => onSelect(item.key)}
                className={[
                  'group relative flex w-full cursor-pointer items-center gap-3 rounded-lg border-0 px-2 py-2 text-left transition-colors',
                  active ? 'bg-[rgba(254,44,85,0.08)]' : 'bg-transparent hover:bg-[#f7f8fa]',
                ].join(' ')}
              >
                <span
                  className={[
                    'relative z-10 h-[11px] w-[11px] shrink-0 rounded-full border transition-all duration-200',
                    active
                      ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color)] shadow-[0_0_0_3px_rgba(254,44,85,0.12)]'
                      : 'border-[#d0d5dd] bg-[#98a2b3]',
                  ].join(' ')}
                  aria-hidden
                />
                <span
                  className={[
                    'text-[12px] leading-5 transition-colors',
                    active
                      ? 'font-semibold text-[var(--yak-brand-color)]'
                      : 'font-normal text-[#667085] group-hover:text-[#344054]',
                  ].join(' ')}
                >
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

export default function ConsumerEditor({
  form,
  editing,
  saving,
  onCancel,
  onSubmit,
}: ConsumerEditorProps) {
  const pageRootRef = useRef<HTMLDivElement>(null);
  const [activeSection, setActiveSection] = useState<SectionKey>('consumer-basic');

  const updateActiveSection = useCallback(() => {
    const container = pageRootRef.current;
    if (!container) return;

    const threshold = container.getBoundingClientRect().top + 120;
    let next: SectionKey = SECTION_ITEMS[0].key;
    SECTION_ITEMS.forEach((item) => {
      const element = document.getElementById(item.key);
      if (element && element.getBoundingClientRect().top <= threshold) next = item.key;
    });
    setActiveSection(next);
  }, []);

  useEffect(() => {
    const container = pageRootRef.current;
    if (!container) return undefined;
    container.addEventListener('scroll', updateActiveSection, { passive: true });
    updateActiveSection();
    return () => container.removeEventListener('scroll', updateActiveSection);
  }, [updateActiveSection]);

  const locateSection = (key: SectionKey) => {
    const container = pageRootRef.current;
    const element = document.getElementById(key);
    if (!container || !element) return;

    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    container.scrollTo({
      top: container.scrollTop + elementRect.top - containerRect.top - 24,
      behavior: 'smooth',
    });
    setActiveSection(key);
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="h-full min-h-[calc(100vh-64px)] overflow-hidden bg-[#f7f8fa] text-[#161823]">
        <div ref={pageRootRef} className="h-full overflow-y-auto overscroll-contain scroll-smooth">
          <div className="mx-auto grid w-full max-w-[1280px] grid-cols-1 gap-6 px-6 pb-6 pt-6 max-xl:max-w-[1040px] xl:grid-cols-[minmax(0,1fr)_160px]">
            <div className="min-w-0">
              <Form<ConsumerEditorValues>
                form={form}
                layout="vertical"
                onFinish={(values) => void onSubmit(values)}
              >
                <main className="space-y-5 pb-4">
                  <div id="consumer-basic" className="scroll-mt-6">
                    <EditorSection title="基本信息">
                      <div className="space-y-5">
                        <EditorField label="调用方名称" required>
                          <Form.Item
                            name="name"
                            className="!mb-0"
                            rules={[{ required: true, message: '请输入调用方名称' }]}
                          >
                            <Input
                              variant="filled"
                              maxLength={128}
                              showCount
                              placeholder="请输入调用方名称"
                            />
                          </Form.Item>
                        </EditorField>

                        <EditorField label="说明">
                          <Form.Item name="description" className="!mb-0">
                            <Input.TextArea
                              variant="filled"
                              rows={4}
                              maxLength={500}
                              showCount
                              placeholder="请输入说明"
                            />
                          </Form.Item>
                        </EditorField>
                      </div>
                    </EditorSection>
                  </div>

                  <div id="consumer-runtime" className="scroll-mt-6">
                    <EditorSection title="调用配置">
                      <div className="space-y-5">
                        <EditorField label="默认限流" required>
                          <Form.Item
                            name="defaultRateLimitPerMinute"
                            className="!mb-0"
                            rules={[{ required: true, message: '请输入调用上限' }]}
                          >
                            <InputNumber
                              variant="filled"
                              min={1}
                              max={100000}
                              addonAfter="次 / 分钟"
                              className="w-full"
                            />
                          </Form.Item>
                        </EditorField>

                        <EditorField label="状态">
                          <Form.Item name="enabled" valuePropName="checked" className="!mb-0">
                            <Switch checkedChildren="可调用" unCheckedChildren="停用" />
                          </Form.Item>
                        </EditorField>
                      </div>
                    </EditorSection>
                  </div>
                </main>

                <footer className="sticky bottom-0 z-50 overflow-hidden rounded-t-lg border border-b-0 border-[#eaecf0] bg-white shadow-[0_-8px_16px_rgba(0,0,0,0.06)]">
                  <div className="flex min-h-[80px] items-center gap-3 px-8 py-4">
                    <YakButton
                      type="primary"
                      htmlType="submit"
                      loading={saving}
                      className="!h-9 !min-w-[120px] !rounded-lg !px-6 !font-medium"
                    >
                      {editing ? '保存配置' : '创建调用方'}
                    </YakButton>
                    <YakButton
                      disabled={saving}
                      onClick={onCancel}
                      className="!h-9 !min-w-[120px] !rounded-lg !border-0 !bg-[#f2f3f5] !px-5 !font-medium !text-[#344054] hover:!bg-[#e9eaec]"
                    >
                      取消
                    </YakButton>
                  </div>
                </footer>
              </Form>
            </div>

            <aside className="hidden xl:block">
              <div className="sticky top-6">
                <SectionNavigator activeKey={activeSection} onSelect={locateSection} />
              </div>
            </aside>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
