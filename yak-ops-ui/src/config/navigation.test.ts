import {
  appRoutes,
  canAccessNavigationRoute,
  getActiveNavigationId,
  getMainNavigationGroups,
  getQuickCreateRoutes,
  getStandaloneNavigationRoutes,
} from './navigation';

describe('permission-aware navigation', () => {
  const batchRead = ['task:batch:read'];

  it('uses route permission metadata and lets details inherit their parent', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;
    expect(canAccessNavigationRoute(list, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(list, [])).toBe(false);
    expect(canAccessNavigationRoute(detail, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(detail, [])).toBe(false);
    expect(getActiveNavigationId('/sync/batch-link-up/42/detail', batchRead)).toBe('batch-link-up');
  });

  it('keeps public groups while filtering permission-protected groups and quick-create independently', () => {
    expect(getMainNavigationGroups([]).map((group) => group.id)).toEqual([
      'development',
      'workflow',
    ]);
    expect(getMainNavigationGroups(batchRead).map((group) => group.id)).toEqual([
      'integration',
      'development',
      'workflow',
    ]);
    expect(getQuickCreateRoutes(batchRead)).toEqual([]);
    expect(getQuickCreateRoutes([...batchRead, 'task:batch:create']).map((route) => route.id)).toEqual(['batch-link-up']);
  });

  it('keeps sidebar groups contiguous by navigation section', () => {
    const groups = getMainNavigationGroups(['security:root']);
    expect(groups.map((group) => group.id)).toEqual([
      'integration',
      'development',
      'workflow',
      'resources',
      'data-quality',
      'system',
    ]);
    expect(groups.map((group) => group.section)).toEqual([
      'task',
      'task',
      'task',
      'management',
      'management',
      'system',
    ]);
  });

  it('registers home before other standalone navigation', () => {
    expect(getStandaloneNavigationRoutes(['security:root']).map((route) => route.id)).toEqual([
      'home',
      'data-source',
      'dashboard',
    ]);
    expect(getActiveNavigationId('/home', [])).toBe('home');
    expect(getActiveNavigationId('/dashboard', [])).toBe('dashboard');
  });

  it('keeps the personal settings page addressable without exposing it in the main sidebar', () => {
    expect(getStandaloneNavigationRoutes([]).map((route) => route.id)).not.toContain('settings');
    expect(getActiveNavigationId('/settings', [])).toBe('settings');
  });

  it('registers development task and execution history as sibling pages', () => {
    const development = getMainNavigationGroups([]).find((group) => group.id === 'development');
    expect(development?.routes.map((route) => route.id)).toEqual([
      'data-development',
      'data-development-execution',
    ]);
    expect(getActiveNavigationId('/data-development/executions', [])).toBe(
      'data-development-execution',
    );
  });

  it('registers the data-quality MVP pages and hidden monitor routes', () => {
    const qualityPermissions = [
      'quality:monitor:read',
      'quality:execution:read',
      'quality:template:read',
    ];
    const groups = getMainNavigationGroups(qualityPermissions);
    const qualityGroup = groups.find((group) => group.id === 'data-quality');
    expect(qualityGroup?.routes.map((route) => route.id)).toEqual([
      'data-quality-table-config',
      'data-quality-execution',
      'data-quality-rule-template',
    ]);
    expect(getActiveNavigationId('/data-quality/monitor/create', qualityPermissions)).toBe(
      'data-quality-table-config',
    );
    expect(getActiveNavigationId('/data-quality/monitor/42', qualityPermissions)).toBe(
      'data-quality-table-config',
    );
    expect(getActiveNavigationId('/data-quality/execution', qualityPermissions)).toBe(
      'data-quality-execution',
    );
    expect(
      getActiveNavigationId('/data-quality/execution/QM-20260807095619-ABC123', qualityPermissions),
    ).toBe('data-quality-execution');
  });

  it('does not expose removed modules', () => {
    expect(getActiveNavigationId('/sync/realtime-link-up', ['task:realtime:read'])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/workbench', batchRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-quality/report', ['quality:report:read'])).toBeUndefined();
  });
});
