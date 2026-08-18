import type {
  DashboardTheme,
  DashboardThemePresetId,
} from './model';

export interface ResolvedDashboardTheme {
  presetId: DashboardThemePresetId;
  name: string;
  canvas: {
    backgroundColor: string;
  };
  component: {
    backgroundColor: string;
    textColor: string;
  };
  chart: {
    palette: string[];
    textColor: string;
    axisColor: string;
    gridColor: string;
  };
}

export const DASHBOARD_THEME_PRESETS: ResolvedDashboardTheme[] = [
  {
    presetId: 'yak-light',
    name: '默认亮色',
    canvas: { backgroundColor: '#f3f4f6' },
    component: {
      backgroundColor: '#ffffff',
      textColor: '#344054',
    },
    chart: {
      palette: ['#fe2c55', '#5b8ff9', '#61d9a5', '#f6bd16', '#7262fd'],
      textColor: '#475467',
      axisColor: '#98a2b3',
      gridColor: '#eaecf0',
    },
  },
  {
    presetId: 'yak-dark',
    name: '深色主题',
    canvas: { backgroundColor: '#161d2b' },
    // Phase 1 deliberately keeps chart cards light. Phase 2 will make charts/tables consume
    // the full dashboard palette so the entire component surface can become dark safely.
    component: {
      backgroundColor: '#ffffff',
      textColor: '#273142',
    },
    chart: {
      palette: ['#35d0ff', '#5b8cff', '#8b5cf6', '#2dd4bf', '#fbbf24'],
      textColor: '#d6deea',
      axisColor: '#718096',
      gridColor: '#2b364a',
    },
  },
];

const presetById = (presetId?: DashboardThemePresetId) => (
  DASHBOARD_THEME_PRESETS.find((item) => item.presetId === presetId)
  ?? DASHBOARD_THEME_PRESETS[0]
);

export const normalizeDashboardTheme = (theme?: DashboardTheme): DashboardTheme => ({
  presetId: theme?.presetId === 'yak-dark' ? 'yak-dark' : 'yak-light',
  canvas: theme?.canvas ? { ...theme.canvas } : undefined,
  component: theme?.component ? { ...theme.component } : undefined,
  chart: theme?.chart ? {
    ...theme.chart,
    palette: theme.chart.palette ? [...theme.chart.palette] : undefined,
  } : undefined,
});

export const resolveDashboardTheme = (theme?: DashboardTheme): ResolvedDashboardTheme => {
  const normalized = normalizeDashboardTheme(theme);
  const preset = presetById(normalized.presetId);
  return {
    ...preset,
    presetId: normalized.presetId || preset.presetId,
    canvas: {
      ...preset.canvas,
      ...normalized.canvas,
    },
    component: {
      ...preset.component,
      ...normalized.component,
    },
    chart: {
      ...preset.chart,
      ...normalized.chart,
      palette: normalized.chart?.palette?.length
        ? [...normalized.chart.palette]
        : [...preset.chart.palette],
    },
  };
};

export const themeFromPreset = (presetId: DashboardThemePresetId): DashboardTheme => ({
  presetId,
});
