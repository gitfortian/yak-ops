import { appRoutes, getMainNavigationGroups } from './navigation';
import { productFeatures } from './productFeatures';

const systemPermissions = [
  'security:user:read',
  'security:department:read',
  'security:role:read',
  'security:permission:read',
  'security:project:read',
  'security:resource-permission:read',
  'security:config:read',
  'security:operation-log:read',
];

describe('system management rollout', () => {
  it('keeps the default sidebar focused on current Yak Ops product capabilities', () => {
    const systemGroup = getMainNavigationGroups(systemPermissions).find(
      (group) => group.id === 'system',
    );

    expect(systemGroup?.routes.map((route) => route.id)).toEqual([
      'system-users',
      'system-departments',
      'system-roles',
      'system-operation-logs',
    ]);
    expect(systemGroup?.routes.map((route) => route.title)).toEqual([
      '用户管理',
      '部门管理',
      '角色与权限',
      '操作日志',
    ]);
  });

  it('keeps unfinished framework capabilities addressable but hidden by rollout gates', () => {
    expect(productFeatures).toEqual({
      projectSpace: false,
      resourceAuthorization: false,
      systemConfig: false,
    });

    const route = (id: string) =>
      appRoutes.find((candidate) => candidate.id === id);

    expect(route('system-permissions')?.hidden).toBe(true);
    expect(route('system-security-projects')).toMatchObject({
      title: '项目空间',
      hidden: true,
    });
    expect(route('system-resource-permissions')?.hidden).toBe(true);
    expect(route('system-configs')?.hidden).toBe(true);
  });
});
