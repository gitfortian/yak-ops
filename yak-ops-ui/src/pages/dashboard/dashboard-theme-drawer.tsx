import { Button, Drawer } from 'antd';
import { Check, Moon, Sun } from 'lucide-react';
import {
  DASHBOARD_THEME_PRESETS,
  resolveDashboardTheme,
  themeFromPreset,
  type ResolvedDashboardTheme,
} from './dashboard-theme';
import type { DashboardTheme, DashboardThemePresetId } from './model';

const ThemePreview = ({ preset }: { preset: ResolvedDashboardTheme }) => {
  const theme = resolveDashboardTheme(themeFromPreset(preset.presetId));
  return (
    <div
      className="h-[96px] overflow-hidden border-b"
      style={{
        backgroundColor: theme.canvas.backgroundColor,
        borderColor: theme.component.borderColor,
      }}
    >
      <div className="grid h-full grid-cols-[1.15fr_.85fr] gap-2 p-2.5">
        <div className="flex min-h-0 flex-col gap-2">
          <div
            className="flex min-h-0 flex-1 items-end gap-1.5 border px-2 pb-2"
            style={{
              backgroundColor: theme.component.backgroundColor,
              borderColor: theme.component.borderColor,
            }}
          >
            {[18, 30, 14, 38, 24].map((height, index) => (
              <span
                key={index}
                className="min-w-0 flex-1"
                style={{
                  height,
                  maxWidth: 9,
                  backgroundColor: theme.chart.palette[index % theme.chart.palette.length],
                }}
              />
            ))}
          </div>
          <div className="flex h-2 items-center gap-1">
            {theme.chart.palette.slice(0, 5).map((color) => (
              <span key={color} className="h-1.5 flex-1" style={{ backgroundColor: color }} />
            ))}
          </div>
        </div>

        <div
          className="flex flex-col justify-between border p-2"
          style={{
            backgroundColor: theme.component.backgroundColor,
            borderColor: theme.component.borderColor,
          }}
        >
          <div
            className="h-1 w-10"
            style={{ backgroundColor: theme.component.mutedTextColor, opacity: 0.55 }}
          />
          <div>
            <div
              className="text-[15px] font-semibold leading-none"
              style={{ color: theme.chart.metricValueColor }}
            >
              86.4%
            </div>
            <div
              className="mt-2 h-1 w-9"
              style={{ backgroundColor: theme.component.mutedTextColor, opacity: 0.35 }}
            />
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
      width={412}
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
          const Icon = preset.tone === 'dark' ? Moon : Sun;
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
              onClick={() => onChange(themeFromPreset(preset.presetId as DashboardThemePresetId))}
            >
              <ThemePreview preset={preset} />
              <div className="flex h-9 items-center gap-1.5 px-2.5">
                <Icon
                  size={13}
                  className={selected ? 'text-[var(--yak-brand-color)]' : 'text-[#667085]'}
                />
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
