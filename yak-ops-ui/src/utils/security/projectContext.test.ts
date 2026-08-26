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

  it('keeps all current routes legacy-global before a module opts into migration', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source')).toBe('LEGACY_GLOBAL');
  });

  it('uses the most specific project-aware route rule', () => {
    expect(resolveProjectRequestMode('/api/v1/data-source/1', rules)).toBe('PROJECT_OPTIONAL');
    expect(resolveProjectRequestMode('/api/v1/data-source/admin/1', rules)).toBe('PROJECT_REQUIRED');
  });

  it('never attaches a project header to a legacy-global route', () => {
    const headers = applyCurrentProjectHeader('/api/v1/data-source', {}, '7');
    expect(headers).toEqual({});
  });

  it('attaches the stored project only after a route opts into project migration', () => {
    storeProjectId(7);
    expect(readStoredProjectId()).toBe('7');

    const headers = applyCurrentProjectHeader('/api/v1/data-source/1', {}, undefined, rules);
    expect(headers).toEqual({ [PROJECT_ID_HEADER]: '7' });
  });

  it('drops invalid persisted project identifiers', () => {
    storeProjectId('not-a-project');
    expect(readStoredProjectId()).toBeUndefined();
  });
});
