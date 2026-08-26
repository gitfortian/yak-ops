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

  it('opts the first migrated business routes into optional project context', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/resources/tree')).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/datasets/1')).toBe('PROJECT_OPTIONAL');
  });

  it('keeps modules outside the rollout table legacy-global', () => {
    expect(resolveProjectRequestMode('/api/v1/workflows')).toBe('LEGACY_GLOBAL');
  });

  it('uses the most specific project-aware route rule', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source/1', rules)).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/data-source/admin/1', rules)).toBe('PROJECT_REQUIRED');
  });

  it('never attaches a project header to a legacy-global route', () => {
    const headers = applyCurrentProjectHeader('/api/v1/workflows', {}, '7');
    expect(headers).toEqual({});
  });

  it('attaches the stored project to migrated routes', () => {
    storeProjectId(7);
    expect(readStoredProjectId()).toBe('7');

    const headers = applyCurrentProjectHeader('/api/v1/data-source/1', {});
    expect(headers).toEqual({ [PROJECT_ID_HEADER]: '7' });
  });

  it('drops invalid persisted project identifiers', () => {
    storeProjectId('not-a-project');
    expect(readStoredProjectId()).toBeUndefined();
  });
});
