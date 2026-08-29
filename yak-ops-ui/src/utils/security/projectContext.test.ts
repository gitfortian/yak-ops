import {
  PROJECT_ID_HEADER,
  applyCurrentProjectHeader,
  clearStoredProjectId,
  readStoredProjectId,
  resolveProjectRequestMode,
  storeProjectId,
  type ProjectRequestRule,
} from './projectContext';

const rules: ProjectRequestRule[] = [
  { prefix: '/api/v1/data-source', mode: 'PROJECT_OPTIONAL' },
  { prefix: '/api/v1/data-source/admin', mode: 'PROJECT_REQUIRED' },
];

describe('Project Space request context', () => {
  afterEach(() => clearStoredProjectId());

  it('requires a project for datasource and other completed project planes', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-source/1')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-source/catalog/1/tables')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/sql-executions/page')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/resources/tree')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/datasets/1')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/home/cockpit')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/home/data-center/overview?period=7d')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-development/nodes')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-service')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-service/7')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/data-service/overview')).toBe('PROJECT_REQUIRED');
    expect(resolveProjectRequestMode('/api/v1/task-catalog/assets')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/job/batch-definition/page')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/job/batch-instance/11')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/realtime-sync/11')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/workflows/definitions')).toBe('PROJECT_OPTIONAL');
  });

  it('keeps datasource plugin metadata and public data service runtime global', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source/plugin/config?pluginType=MYSQL')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-source/plugin/config/install')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-service/runtime/orders')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/data-service/runtime/orders/by-id')).toBe('LEGACY_GLOBAL');
  });

  it('keeps platform-global capabilities outside the rollout table', () => {
    expect(resolveProjectRequestMode('/api/v1/compute-environments')).toBe('LEGACY_GLOBAL');
    expect(resolveProjectRequestMode('/api/v1/quality/templates')).toBe('LEGACY_GLOBAL');
  });

  it('uses the most specific project-aware route rule', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source/1', rules)).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/data-source/admin/1', rules)).toBe('PROJECT_REQUIRED');
  });

  it('never attaches a project header to a legacy-global route', () => {
    const headers = applyCurrentProjectHeader('/api/v1/compute-environments', {}, '7');
    expect(headers).toEqual({});
    const pluginHeaders = applyCurrentProjectHeader('/api/v1/data-source/plugin/config', {}, '7');
    expect(pluginHeaders).toEqual({});
    const runtimeHeaders = applyCurrentProjectHeader('/api/v1/data-service/runtime/orders', {}, '7');
    expect(runtimeHeaders).toEqual({});
  });

  it('attaches the stored project to required management routes', () => {
    storeProjectId(7);
    expect(readStoredProjectId()).toBe('7');

    expect(applyCurrentProjectHeader('/api/v1/data-source/42', {})).toEqual({
      [PROJECT_ID_HEADER]: '7',
    });
    expect(applyCurrentProjectHeader('/api/v1/sql-executions/page', {})).toEqual({
      [PROJECT_ID_HEADER]: '7',
    });
    expect(applyCurrentProjectHeader('/api/v1/home/cockpit', {})).toEqual({
      [PROJECT_ID_HEADER]: '7',
    });
    expect(applyCurrentProjectHeader('/api/v1/data-development/nodes', {})).toEqual({
      [PROJECT_ID_HEADER]: '7',
    });
    expect(applyCurrentProjectHeader('/api/v1/data-service/7', {})).toEqual({
      [PROJECT_ID_HEADER]: '7',
    });
  });

  it('still attaches the stored project to optional migrated routes', () => {
    storeProjectId(7);
    const headers = applyCurrentProjectHeader('/api/v1/workflows/definitions', {});
    expect(headers).toEqual({ [PROJECT_ID_HEADER]: '7' });
  });

  it('drops invalid persisted project identifiers', () => {
    storeProjectId('not-a-project');
    expect(readStoredProjectId()).toBeUndefined();
  });
});