import { YAK_OPS_MENU_CODES } from '../constants/securityMenuCodes';
import {
  appRoutes,
  canAccessNavigationRoute,
  getActiveNavigationId,
  getMainNavigationGroups,
  getQuickCreateRoutes,
  getStandaloneNavigationRoutes,
  resolveNavigationMenuCode,
} from './navigation';

describe('permission-aware navigation', () => {
  const batchRead = ['task:batch:read'];
  const developmentRead = ['data-development:read'];
  const dataServiceRead = ['data-service:read'];
  const dataServiceAll = [
    'data-service:read',
    'data-service:runtime',
    'data-service:observe',
  ];

  it('uses route permission metadata and lets details inherit their parent', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;
    expect(canAccessNavigationRoute(list, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(list, [])).toBe(false);
    expect(canAccessNavigationRoute(detail, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(detail, [])).toBe(false);
    expect(getActiveNavigationId('/sync/batch-link-up/42/detail', batchRead)).toBe('batch-link-up');
  });

  it('uses stable menu codes for protected routes and hidden descendants', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;

    expect(list.menuCode).toBe(YAK_OPS_MENU_CODES.batchLinkUp);
    expect(resolveNavigationMenuCode(detail)).toBe(YAK_OPS_MENU_CODES.batchLinkUp);
    expect(
      canAccessNavigationRoute(list, batchRead, [YAK_OPS_MENU_CODES.batchLinkUp]),
    ).toBe(true);
    expect(
      canAccessNavigationRoute(list, batchRead, [YAK_OPS_MENU_CODES.dataSource]),
    ).toBe(false);
    expect(
      canAccessNavigationRoute(detail, batchRead, [YAK_OPS_MENU_CODES.batchLinkUp]),
    ).toBe(true);
    expect(getActiveNavigationId(
      '/sync/batch-link-up/42/detail',
      batchRead,
      [YAK_OPS_MENU_CODES.dataSource],
    )).toBeUndefined();
  });

  it('keeps public groups while filtering permission-protected groups and quick-create independently', () => {
    expect(getMainNavigationGroups([]).map((group) => group.id)).toEqual([
      'workflow',
      'data-analysis',
    ]);
    expect(getMainNavigationGroups(batchRead).map((group) => group.id)).toEqual([
      'integration',
      'workflow',
      'data-analysis',
    ]);
    expect(getMainNavigationGroups(developmentRead).map((group) => group.id)).toEqual([
      'development',
      'workflow',
      'data-analysis',
    ]);
    expect(getMainNavigationGroups(dataServiceRead).map((group) => group.id)).toEqual([
      'workflow',
      'data-analysis',
      'data-service',
    ]);
    expect(getQuickCreateRoutes(batchRead)).toEqual([]);
    expect(getQuickCreateRoutes([...batchRead, 'task:batch:create']).map((route) => route.id)).toEqual(['batch-link-up']);
  });

  it('uses menu grants when the backend menu contract is present', () => {
    expect(
      getMainNavigationGroups(batchRead, []).map((group) => group.id),
    ).toEqual(['workflow', 'data-analysis']);
    expect(
      getMainNavigationGroups(
        batchRead,
        [YAK_OPS_MENU_CODES.batchLinkUp],
      ).map((group) => group.id),
    ).toEqual(['integration', 'workflow', 'data-analysis']);
    expect(
      getQuickCreateRoutes(
        [...batchRead, 'task:batch:create'],
        [],
      ),
    ).toEqual([]);
    expect(
      getQuickCreateRoutes(
        [...batchRead, 'task:batch:create'],
        [YAK_OPS_MENU_CODES.batchLinkUp],
      ).map((route) => route.id),
    ).toEqual(['batch-link-up']);
  });

  it('keeps sidebar groups contiguous by navigation section', () => {
    const groups = getMainNavigationGroups(['security:root']);
    expect(groups.map((group) => group.id)).toEqual([
      'integration',
      'development',
      'workflow',
      'resources',
      'data-quality',
      'data-analysis',
      'data-service',
      'system',
    ]);
    expect(groups.map((group) => group.section)).toEqual([
      'task',
      'task',
      'task',
      'management',
      'management',
      'management',
      'management',
      'system',
    ]);
  });

  it('keeps current data-consumption entries while preserving dashboard editor routes', () => {
    const dataConsumption = getMainNavigationGroups([]).find(
      (group) => group.id === 'data-analysis',
    );
    expect(dataConsumption?.title).toBe('数据消费');
    expect(dataConsumption?.routes.map((route) => route.id)).toEqual([
      'dashboard',
      'dataset-management',
      'data-analysis-lineage',
      'digital-screen',
    ]);
    expect(getActiveNavigationId('/dashboard', [])).toBe('dashboard');
    expect(getActiveNavigationId('/dashboard/new', [])).toBe('dashboard');
    expect(getActiveNavigationId('/dashboard/42', [])).toBe('dashboard');
    expect(getActiveNavigationId('/data-analysis/chart-analysis', [])).toBe('dashboard');
  });

  it('gates data-service pages by read, runtime and observe permissions', () => {
    expect(getMainNavigationGroups([]).some((group) => group.id === 'data-service')).toBe(false);

    const readOnly = getMainNavigationGroups(dataServiceRead).find(
      (group) => group.id === 'data-service',
    );
    expect(readOnly?.routes.map((route) => route.id)).toEqual(['data-service-api']);
    expect(getActiveNavigationId('/data-service/api/42', dataServiceRead)).toBe('data-service-api');
    expect(getActiveNavigationId('/data-service/debug', dataServiceRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-service/debug', ['data-service:runtime'])).toBeUndefined();
    expect(getActiveNavigationId('/data-service/overview', dataServiceRead)).toBeUndefined();

    const full = getMainNavigationGroups(dataServiceAll).find(
      (group) => group.id === 'data-service',
    );
    expect(full?.title).toBe('数据服务');
    expect(full?.routes.map((route) => route.id)).toEqual([
      'data-service-api',
      'data-service-debug',
      'data-service-overview',
      'data-service-logs',
    ]);
    expect(getActiveNavigationId('/data-service', dataServiceAll)).toBe('data-service-api');
    expect(getActiveNavigationId('/data-service/debug', dataServiceAll)).toBe('data-service-debug');
    expect(getActiveNavigationId('/data-service/overview', dataServiceAll)).toBe('data-service-overview');
    expect(getActiveNavigationId('/data-service/logs', dataServiceAll)).toBe('data-service-logs');
  });

  it('registers home before other standalone navigation', () => {
    expect(getStandaloneNavigationRoutes(['security:root']).map((route) => route.id)).toEqual([
      'home',
      'data-source',
    ]);
    expect(getActiveNavigationId('/home', [])).toBe('home');
  });

  it('filters protected standalone navigation by menu grant', () => {
    expect(
      getStandaloneNavigationRoutes(
        ['resource:data-source:read'],
        [],
      ).map((route) => route.id),
    ).toEqual(['home']);
    expect(
      getStandaloneNavigationRoutes(
        ['resource:data-source:read'],
        [YAK_OPS_MENU_CODES.dataSource],
      ).map((route) => route.id),
    ).toEqual(['home', 'data-source']);
  });

  it('keeps the personal settings page addressable without exposing it in the main sidebar', () => {
    expect(getStandaloneNavigationRoutes([]).map((route) => route.id)).not.toContain('settings');
    expect(getActiveNavigationId('/settings', [])).toBe('settings');
  });

  it('requires data-development read permission for workbench, releases, executions and child routes', () => {
    const development = getMainNavigationGroups(developmentRead).find(
      (group) => group.id === 'development',
    );
    expect(development?.routes.map((route) => route.id)).toEqual([
      'data-development',
      'data-development-release',
      'data-development-execution',
    ]);
    expect(getMainNavigationGroups([]).some((group) => group.id === 'development')).toBe(false);
    expect(getActiveNavigationId('/data-development', [])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/task/42', [])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/task/42', developmentRead)).toBe(
      'data-development',
    );
    expect(getActiveNavigationId('/data-development/releases', developmentRead)).toBe(
      'data-development-release',
    );
    expect(getActiveNavigationId('/data-development/executions', developmentRead)).toBe(
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
      'data-quality-overview',
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
    expect(getActiveNavigationId('/data-development/workbench', developmentRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-quality/report', ['quality:report:read'])).toBeUndefined();
  });
});