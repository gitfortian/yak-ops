import {
  analysisThemeFromDashboardTheme,
  DASHBOARD_THEME_PRESETS,
  normalizeDashboardTheme,
  resolveDashboardTheme,
  themeFromPreset,
} from './dashboard-theme';

describe('dashboard themes', () => {
  it('ships a compact curated preset library', () => {
    expect(DASHBOARD_THEME_PRESETS.map((item) => item.presetId)).toEqual([
      'yak-light',
      'yak-dark',
      'ocean-night',
      'graphite',
      'mist-blue',
    ]);
    DASHBOARD_THEME_PRESETS.forEach((preset) => {
      expect(preset.chart.palette.length).toBeGreaterThanOrEqual(5);
      expect(preset.component.backgroundColor).toBeTruthy();
      expect(preset.canvas.backgroundColor).toBeTruthy();
    });
  });

  it('preserves the new preset ids during normalization', () => {
    expect(normalizeDashboardTheme(themeFromPreset('ocean-night')).presetId).toBe('ocean-night');
    expect(normalizeDashboardTheme(themeFromPreset('graphite')).presetId).toBe('graphite');
    expect(normalizeDashboardTheme(themeFromPreset('mist-blue')).presetId).toBe('mist-blue');
  });

  it('turns dashboard tokens into renderer tokens without mutating the dashboard config', () => {
    const dashboardTheme = {
      presetId: 'ocean-night' as const,
      chart: { palette: ['#111111', '#222222'] },
    };
    const before = JSON.stringify(dashboardTheme);
    const rendererTheme = analysisThemeFromDashboardTheme(dashboardTheme);

    expect(rendererTheme.palette).toEqual(['#111111', '#222222']);
    expect(rendererTheme.backgroundColor).toBe(resolveDashboardTheme(dashboardTheme).component.backgroundColor);
    expect(rendererTheme.metricValueColor).toBe('#7dd3fc');
    expect(JSON.stringify(dashboardTheme)).toBe(before);
  });
});
