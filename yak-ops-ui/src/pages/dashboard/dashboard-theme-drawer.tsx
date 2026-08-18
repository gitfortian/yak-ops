import { Button, Drawer } from 'antd';
import { Check, Moon, Sun } from 'lucide-react';
import {
  DASHBOARD_THEME_PRESETS,
  resolveDashboardTheme,
  themeFromPreset,
} from './dashboard-theme';
import type { DashboardTheme, DashboardThemePresetId } from './model';

const ThemePreview = ({ presetId }: { presetId: DashboardThemePresetId }) => {
  const theme = resolveDashboardTheme(themeFromPreset(presetId));
  const dark = presetId === 'yak-dark';
  return (
    <div
      className="h-[86px] overflow-hidden border border-[#e5e7eb]"
      style={{ backgroundColor: theme.canvas.backgroundColor }}
    >
      <div className="grid h-full grid-cols-[1.15fr_.85fr] gap-2 p-2.5">
        <div className="flex flex-col gap-2">
          <div
            className="h-6 border border-black/[.04]"
            style={{ backgroundColor: theme.component.backgroundColor }}
          >
            <div className="flex h-full items-end gap-1 px-2 pb-1.5">
              {[14, 20, 10, 24, 17].map((height, index) => (
                <span
                  key={index}
                  className="w-2"
                  style={{
                    height,
                    backgroundColor: theme.chart.palette[index % theme.chart.palette.length],
                    opacity: dark ? 0.9 : 0.78,
                  }}
                />
              ))}
            </div>
          </div>
          <div
            className="flex min-h-0 flex-1 items-center px-2"
            style={{ backgroundColor: theme.component.backgroundColor }}
          >
            <div className="w-full space-y-1.5">
              <div className="h-1 w-[78%] bg-[#d8dde5]" />
              <div className="h-1 w-[56%] bg-[#e5e9ef]" />
            </div>
          </div>
        </div>
        <div
          className="flex flex-col justify-between p-2"
          style={{ backgroundColor: theme.component.backgroundColor }}
        >
          <div className="h-1 w-10 bg-[#d8dde5]" />
          <div>
            <div
              className="text-[14px] font-semibold leading-none"
              style={{ color: theme.component.textColor }}
            >
              86.4%
            </div>
            <div className="mt-1.5 h-1 w-8 bg-[#e5e9ef]" />
          </div>
        </div>
      </div>
    </div>
  );
};

export function DashboardThemeDrawer({
  open,
  theme,
  onChange,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  theme?: DashboardTheme;
  onChange: (theme: DashboardTheme) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const selectedId = resolveDashboardTheme(theme).presetId;

  return (
    <Drawer
      title={<span className="text-[14px] font-semibold text-[#161823]">仪表盘样式</span>}
      width={376}
      open={open}
      onClose={onCancel}
      mask={false}
      destroyOnClose={false}
      styles={{
        header: { padding: '12px 16px', minHeight: 48 },
        body: { padding: 16, background: '#fff' },
        footer: { padding: '10px 16px' },
      }}
      footer={(
        <div className="flex justify-end gap-2">
          <Button size="small" className="!h-8 !px-4" onClick={onCancel}>取消</Button>
          <Button size="small" type="primary" className="!h-8 !px-4 !shadow-none" onClick={onConfirm}>
            确定
          </Button>
        </div>
      )}
    >
      <div className="mb-3 text-[12px] font-semibold text-[#344054]">预设主题</div>
      <div className="grid grid-cols-2 gap-3">
        {DASHBOARD_THEME_PRESETS.map((preset) => {
          const selected = selectedId === preset.presetId;
          const Icon = preset.presetId === 'yak-dark' ? Moon : Sun;
          return (
            <button
              key={preset.presetId}
              type="button"
              className={[
                'relative overflow-hidden border bg-white p-0 text-left transition-colors',
                selected
                  ? 'border-[var(--yak-brand-color)]'
                  : 'border-[#e4e7ec] hover:border-[#c8ced8]',
              ].join(' ')}
              onClick={() => onChange(themeFromPreset(preset.presetId))}
            >
              <ThemePreview presetId={preset.presetId} />
              <div className="flex h-9 items-center gap-1.5 px-2.5">
                <Icon size={13} className={selected ? 'text-[var(--yak-brand-color)]' : 'text-[#667085]'} />
                <span className="text-[12px] font-medium text-[#344054]">{preset.name}</span>
              </div>
              {selected ? (
                <span className="absolute right-2 top-2 flex h-5 w-5 items-center justify-center rounded-full bg-[var(--yak-brand-color)] text-white">
                  <Check size={12} strokeWidth={2.4} />
                </span>
              ) : null}
            </button>
          );
        })}
      </div>
    </Drawer>
  );
}
