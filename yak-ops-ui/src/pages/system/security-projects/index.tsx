import {
  ApartmentOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useModel } from '@umijs/max';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  TreeSelect,
  Typography,
  message,
  type TableColumnsType,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import YakButton from '@/components/YakButton';
import {
  AssignmentDrawer,
  SecurityQueryTable,
} from '@/components/security';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  getDepartmentTree,
  type DepartmentVO,
} from '@/services/security/departments';
import {
  assignSecurityProjectMembers,
  assignSecurityProjectOwner,
  checkSecurityProjectDeletion,
  createSecurityProject,
  deleteSecurityProject,
  getSecurityProject,
  getSecurityProjectMemberCandidates,
  pageSecurityProjects,
  type SecurityProjectDetail,
  type SecurityProjectInput,
  type SecurityProjectStatus,
  type SecurityProjectSummary,
  type SecurityProjectUser,
  updateSecurityProject,
  updateSecurityProjectStatus,
} from '@/services/security/projects';
import { hasPermission } from '@/utils/security/permission';

import SystemManagementPage from '../components/SystemManagementPage';
import {
  formatSystemDateTime,
  getSystemErrorMessage,
} from '../utils';
import {
  buildWorkspaceCreateInput,
  filterWorkspaceAssignmentCandidates,
  normalizeWorkspaceInput,
  toWorkspaceDepartmentTreeData,
  type WorkspaceAssignmentMode,
} from './workspace';

const ROOT_PERMISSION = 'security:root';
const DEFAULT_PAGE_SIZE = 10;

interface WorkspaceFilters {
  projectCode?: string;
  projectName?: string;
  ownerName?: string;
  status?: SecurityProjectStatus;
}

interface AssignmentState {
  project: SecurityProjectSummary;
  mode: WorkspaceAssignmentMode;
  value: number[];
}

const userDisplayName = (user?: SecurityProjectUser): string =>
  user?.realName || user?.nickName || user?.userName || '-';

const cleanFilter = (value?: string): string | undefined => {
  const normalized = value?.trim();
  return normalized || undefined;
};

export default function SecurityProjectsPage() {
  const { initialState } = useModel('@@initialState');
  const { currentProject, refreshProjects } = useSecurityProject();
  const permissionCodes = initialState?.currentUser?.permissionCodes ?? [];
  const canManage = hasPermission(permissionCodes, ROOT_PERMISSION);
  const currentUserId = Number(initialState?.currentUser?.id ?? 0);

  const [filterForm] = Form.useForm<WorkspaceFilters>();
  const [editorForm] = Form.useForm<SecurityProjectInput>();
  const [filters, setFilters] = useState<WorkspaceFilters>({});
  const [rows, setRows] = useState<SecurityProjectSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SecurityProjectSummary>();
  const [saving, setSaving] = useState(false);
  const [departmentRoot, setDepartmentRoot] = useState<DepartmentVO>();
  const [departmentLoading, setDepartmentLoading] = useState(false);

  const [detail, setDetail] = useState<SecurityProjectDetail>();
  const [detailLoading, setDetailLoading] = useState(false);

  const [assigning, setAssigning] = useState<AssignmentState>();
  const [candidates, setCandidates] = useState<SecurityProjectUser[]>([]);
  const [candidateLoading, setCandidateLoading] = useState(false);

  const loadProjects = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageSecurityProjects({
        pageNum,
        pageSize,
        projectCode: cleanFilter(filters.projectCode),
        projectName: cleanFilter(filters.projectName),
        ownerName: cleanFilter(filters.ownerName),
        status: filters.status,
      });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      setRows([]);
      setTotal(0);
      message.error(
        getSystemErrorMessage(error, '工作空间列表加载失败'),
      );
    } finally {
      setLoading(false);
    }
  }, [filters, pageNum, pageSize]);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  const loadDepartments = useCallback(async () => {
    if (departmentRoot) return departmentRoot;

    setDepartmentLoading(true);
    try {
      const root = await getDepartmentTree();
      setDepartmentRoot(root);
      return root;
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '部门列表加载失败'),
      );
      return undefined;
    } finally {
      setDepartmentLoading(false);
    }
  }, [departmentRoot]);

  const departmentTreeData = useMemo(
    () => toWorkspaceDepartmentTreeData(departmentRoot),
    [departmentRoot],
  );

  const closeEditor = () => {
    if (saving) return;
    setEditorOpen(false);
    setEditing(undefined);
    editorForm.resetFields();
  };

  const openEditor = async (project?: SecurityProjectSummary) => {
    if (!canManage) return;

    setEditing(project);
    setEditorOpen(true);
    editorForm.resetFields();
    editorForm.setFieldsValue({
      projectName: project?.projectName ?? '',
      description: project?.description ?? '',
      deptId: project?.deptId ?? undefined,
    });
    await loadDepartments();
  };

  const saveWorkspace = async (values: SecurityProjectInput) => {
    if (saving) return;

    setSaving(true);
    try {
      if (editing) {
        await updateSecurityProject(
          editing.id,
          normalizeWorkspaceInput(values),
        );
        message.success('工作空间已更新');
      } else {
        if (!currentUserId) {
          throw new Error('无法识别当前用户，不能创建工作空间');
        }
        await createSecurityProject(
          buildWorkspaceCreateInput(values, currentUserId),
        );
        message.success('工作空间已创建，你已成为负责人');
      }

      closeEditor();
      await refreshProjects();
      await loadProjects();
    } catch (error) {
      message.error(
        getSystemErrorMessage(
          error,
          editing ? '工作空间更新失败' : '工作空间创建失败',
        ),
      );
    } finally {
      setSaving(false);
    }
  };

  const showDetail = async (project: SecurityProjectSummary) => {
    setDetailLoading(true);
    try {
      setDetail(await getSecurityProject(project.id));
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '工作空间详情加载失败'),
      );
    } finally {
      setDetailLoading(false);
    }
  };

  const openAssignment = async (
    project: SecurityProjectSummary,
    mode: WorkspaceAssignmentMode,
  ) => {
    if (!canManage) return;

    setCandidateLoading(true);
    try {
      const [projectDetail, allCandidates] = await Promise.all([
        getSecurityProject(project.id),
        getSecurityProjectMemberCandidates(project.id),
      ]);
      const visibleCandidates = filterWorkspaceAssignmentCandidates(
        mode,
        projectDetail,
        allCandidates,
      );

      setCandidates(visibleCandidates);
      setAssigning({
        project,
        mode,
        value:
          mode === 'owner'
            ? projectDetail.owners.slice(0, 1).map((user) => user.id)
            : projectDetail.members.map((user) => user.id),
      });
    } catch (error) {
      setCandidates([]);
      message.error(
        getSystemErrorMessage(error, '可分配用户加载失败'),
      );
    } finally {
      setCandidateLoading(false);
    }
  };

  const submitAssignment = async (ids: number[]) => {
    if (!assigning) return;

    try {
      if (assigning.mode === 'owner') {
        if (!ids.length) {
          throw new Error('工作空间至少需要一名负责人');
        }
        await assignSecurityProjectOwner(
          assigning.project.id,
          ids.slice(0, 1),
        );
      } else {
        await assignSecurityProjectMembers(assigning.project.id, ids);
      }

      message.success(
        assigning.mode === 'owner' ? '负责人已更新' : '成员已更新',
      );
      setAssigning(undefined);
      setCandidates([]);
      await refreshProjects();
      await loadProjects();
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '成员关系更新失败'),
      );
      throw error;
    }
  };

  const changeStatus = (
    project: SecurityProjectSummary,
    checked: boolean,
  ) => {
    if (!canManage) return;

    const status: SecurityProjectStatus = checked
      ? 'ENABLED'
      : 'DISABLED';

    Modal.confirm({
      title: `确认${checked ? '启用' : '停用'}工作空间“${project.projectName}”？`,
      content: !checked
        ? '停用后，该工作空间将从成员的工作空间切换列表中移除。'
        : undefined,
      onOk: async () => {
        try {
          await updateSecurityProjectStatus(project.id, status);
          await refreshProjects();
          await loadProjects();
          message.success('工作空间状态已更新');
        } catch (error) {
          message.error(
            getSystemErrorMessage(error, '工作空间状态更新失败'),
          );
          throw error;
        }
      },
    });
  };

  const removeWorkspace = async (project: SecurityProjectSummary) => {
    if (!canManage) return;

    try {
      const check = await checkSecurityProjectDeletion(project.id);
      Modal.confirm({
        title: `删除工作空间“${project.projectName}”？`,
        okText: '删除',
        okButtonProps: {
          danger: true,
          disabled: !check.deletable,
        },
        content: check.deletable ? (
          <Alert
            showIcon
            type="warning"
            message="检查通过。删除后无法恢复，请确认该工作空间确实不再使用。"
          />
        ) : (
          <div className="space-y-3">
            <Alert
              showIcon
              type="error"
              message={
                check.reason ?? '工作空间仍有关联资源，无法删除。'
              }
            />
            <Space size={[4, 6]} wrap>
              {check.resourceNameList.map((name) => (
                <Tag key={name}>{name}</Tag>
              ))}
            </Space>
          </div>
        ),
        onOk: async () => {
          try {
            await deleteSecurityProject(project.id);
            await refreshProjects();
            await loadProjects();
            message.success('工作空间已删除');
          } catch (error) {
            message.error(
              getSystemErrorMessage(error, '工作空间删除失败'),
            );
            throw error;
          }
        },
      });
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '删除前检查失败'),
      );
    }
  };

  const columns = useMemo<TableColumnsType<SecurityProjectSummary>>(
    () => [
      {
        title: '工作空间',
        dataIndex: 'projectName',
        width: 240,
        render: (_, row) => (
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="truncate font-medium text-slate-800">
                {row.projectName}
              </span>
              {currentProject?.id === row.id ? (
                <Tag color="processing">当前</Tag>
              ) : null}
            </div>
            <div className="mt-1 text-xs text-slate-400">
              {row.projectCode || '-'}
            </div>
          </div>
        ),
      },
      {
        title: '所属部门',
        dataIndex: 'deptPath',
        width: 220,
        render: (_, row) => row.deptPath?.join(' / ') || '-',
      },
      {
        title: '负责人',
        dataIndex: 'owners',
        width: 180,
        render: (_, row) =>
          row.owners.length > 0 ? (
            <Space size={[4, 4]} wrap>
              {row.owners.map((owner) => (
                <Tag key={owner.id}>{userDisplayName(owner)}</Tag>
              ))}
            </Space>
          ) : (
            <span className="text-amber-600">未分配</span>
          ),
      },
      {
        title: '普通成员',
        dataIndex: 'memberCount',
        width: 100,
        align: 'center',
        render: (value) => Number(value ?? 0),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (_, row) =>
          canManage ? (
            <Switch
              size="small"
              checked={row.status === 'ENABLED'}
              checkedChildren="启用"
              unCheckedChildren="停用"
              onChange={(checked) => changeStatus(row, checked)}
            />
          ) : (
            <Tag color={row.status === 'ENABLED' ? 'success' : 'default'}>
              {row.status === 'ENABLED' ? '启用' : '停用'}
            </Tag>
          ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        width: 168,
        render: (value) => formatSystemDateTime(value as string | undefined),
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: canManage ? 300 : 80,
        render: (_, row) => (
          <Space size={4} wrap>
            <Button type="link" onClick={() => void showDetail(row)}>
              详情
            </Button>
            {canManage ? (
              <>
                <Button type="link" onClick={() => void openEditor(row)}>
                  编辑
                </Button>
                <Button
                  type="link"
                  onClick={() => void openAssignment(row, 'owner')}
                >
                  负责人
                </Button>
                <Button
                  type="link"
                  onClick={() => void openAssignment(row, 'member')}
                >
                  成员
                </Button>
                <Button
                  danger
                  type="link"
                  onClick={() => void removeWorkspace(row)}
                >
                  删除
                </Button>
              </>
            ) : null}
          </Space>
        ),
      },
    ],
    [canManage, currentProject?.id],
  );

  return (
    <SystemManagementPage
      title="工作空间"
      titleId="system-workspaces-title"
      icon={<ApartmentOutlined className="text-slate-500" />}
      className="min-h-full"
    >
      <Alert
        showIcon
        type="info"
        className="mb-4"
        message="工作空间是 Yak Ops 的业务数据隔离边界"
        description="角色与功能权限决定用户能做什么；工作空间成员关系决定用户能进入哪里；数据源等业务数据通过 project_id 归属当前工作空间。Stage 1 暂不启用资源级授权。"
      />

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <Form<WorkspaceFilters>
            form={filterForm}
            layout="inline"
            onFinish={(values) => {
              setPageNum(1);
              setFilters(values);
            }}
          >
            <Form.Item name="projectName" label="名称">
              <Input allowClear placeholder="工作空间名称" />
            </Form.Item>
            <Form.Item name="projectCode" label="编码">
              <Input allowClear placeholder="工作空间编码" />
            </Form.Item>
            <Form.Item name="ownerName" label="负责人">
              <Input allowClear placeholder="用户名或姓名" />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select
                allowClear
                className="w-28"
                placeholder="全部"
                options={[
                  { label: '启用', value: 'ENABLED' },
                  { label: '停用', value: 'DISABLED' },
                ]}
              />
            </Form.Item>
            <Form.Item>
              <Space>
                <YakButton type="primary" htmlType="submit">
                  查询
                </YakButton>
                <YakButton
                  onClick={() => {
                    filterForm.resetFields();
                    setPageNum(1);
                    setFilters({});
                  }}
                >
                  重置
                </YakButton>
              </Space>
            </Form.Item>
          </Form>

          <Space>
            <YakButton
              icon={<ReloadOutlined />}
              loading={loading}
              onClick={() => void loadProjects()}
            >
              刷新
            </YakButton>
            {canManage ? (
              <YakButton
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => void openEditor()}
              >
                新增工作空间
              </YakButton>
            ) : null}
          </Space>
        </div>
      </div>

      <SecurityQueryTable<SecurityProjectSummary>
        dataSource={rows}
        columns={columns}
        loading={loading}
        scroll={{ x: 1280 }}
        pagination={{
          current: pageNum,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 个工作空间`,
          onChange: (nextPage, nextPageSize) => {
            setPageNum(nextPageSize === pageSize ? nextPage : 1);
            setPageSize(nextPageSize);
          },
        }}
      />

      <Drawer
        open={editorOpen}
        title={editing ? '编辑工作空间' : '新增工作空间'}
        width={520}
        forceRender
        maskClosable={!saving}
        keyboard={!saving}
        closable={!saving}
        onClose={closeEditor}
        extra={
          <Space>
            <YakButton disabled={saving} onClick={closeEditor}>
              取消
            </YakButton>
            <YakButton
              type="primary"
              loading={saving}
              onClick={() => editorForm.submit()}
            >
              {editing ? '更新' : '创建'}
            </YakButton>
          </Space>
        }
      >
        <Alert
          showIcon
          type="info"
          className="mb-5"
          message={
            editing
              ? '修改名称或所属部门不会改变已有负责人和成员关系。'
              : '创建后当前用户会自动成为负责人，工作空间会立即出现在 Header 切换列表中。'
          }
        />

        {departmentTreeData.length === 0 && !departmentLoading ? (
          <Alert
            showIcon
            type="warning"
            className="mb-4"
            message="暂无可用部门"
            description="工作空间必须归属真实部门，请先在“系统管理 > 部门管理”创建部门。"
          />
        ) : null}

        <Form<SecurityProjectInput>
          form={editorForm}
          layout="vertical"
          preserve={false}
          disabled={saving}
          onFinish={(values) => void saveWorkspace(values)}
        >
          {editing ? (
            <Form.Item label="工作空间编码">
              <Input disabled value={editing.projectCode || '-'} />
            </Form.Item>
          ) : null}

          <Form.Item
            name="projectName"
            label="工作空间名称"
            rules={[
              {
                required: true,
                whitespace: true,
                message: '请输入工作空间名称',
              },
            ]}
          >
            <Input
              maxLength={128}
              showCount
              placeholder="例如：成都一院"
            />
          </Form.Item>

          <Form.Item
            name="deptId"
            label="所属部门"
            rules={[{ required: true, message: '请选择所属部门' }]}
          >
            <TreeSelect
              treeData={departmentTreeData}
              treeDefaultExpandAll
              showSearch
              allowClear={false}
              loading={departmentLoading}
              placeholder="请选择所属部门"
              filterTreeNode={(input, node) =>
                String(node.title ?? '')
                  .toLocaleLowerCase()
                  .includes(input.trim().toLocaleLowerCase())
              }
            />
          </Form.Item>

          <Form.Item name="description" label="工作空间描述">
            <Input.TextArea
              maxLength={500}
              showCount
              autoSize={{ minRows: 4, maxRows: 8 }}
              placeholder="说明这个工作空间对应的医院、团队或业务范围"
            />
          </Form.Item>
        </Form>
      </Drawer>

      <AssignmentDrawer
        open={Boolean(assigning)}
        title={assigning?.mode === 'owner' ? '分配负责人' : '分配普通成员'}
        mode={assigning?.mode === 'owner' ? 'single' : 'multiple'}
        options={candidates.map((user) => ({
          id: user.id,
          label: userDisplayName(user),
          description:
            user.realName && user.realName !== user.userName
              ? user.userName
              : undefined,
        }))}
        value={assigning?.value ?? []}
        loading={candidateLoading}
        allowEmpty={assigning?.mode === 'member'}
        onClose={() => {
          setAssigning(undefined);
          setCandidates([]);
        }}
        onSubmit={submitAssignment}
      />

      <Drawer
        open={Boolean(detail) || detailLoading}
        title="工作空间详情"
        width={600}
        loading={detailLoading}
        onClose={() => setDetail(undefined)}
      >
        {detail ? (
          <div className="space-y-6">
            <Descriptions
              column={1}
              bordered
              items={[
                {
                  key: 'code',
                  label: '编码',
                  children: detail.projectCode || '-',
                },
                {
                  key: 'name',
                  label: '名称',
                  children: detail.projectName,
                },
                {
                  key: 'dept',
                  label: '所属部门',
                  children: detail.deptPath?.join(' / ') || '-',
                },
                {
                  key: 'description',
                  label: '描述',
                  children: detail.description || '-',
                },
                {
                  key: 'status',
                  label: '状态',
                  children: (
                    <Tag color={detail.status === 'ENABLED' ? 'success' : 'default'}>
                      {detail.status === 'ENABLED' ? '启用' : '停用'}
                    </Tag>
                  ),
                },
                {
                  key: 'created',
                  label: '创建时间',
                  children: formatSystemDateTime(detail.createTime),
                },
              ]}
            />

            <div>
              <Typography.Title level={5}>负责人</Typography.Title>
              <Space wrap>
                {detail.owners.length > 0 ? (
                  detail.owners.map((owner) => (
                    <Tag key={owner.id}>{userDisplayName(owner)}</Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">暂未分配负责人</Typography.Text>
                )}
              </Space>
            </div>

            <div>
              <Typography.Title level={5}>普通成员</Typography.Title>
              <Space wrap>
                {detail.members.length > 0 ? (
                  detail.members.map((member) => (
                    <Tag key={member.id}>{userDisplayName(member)}</Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">暂无普通成员</Typography.Text>
                )}
              </Space>
            </div>
          </div>
        ) : null}
      </Drawer>
    </SystemManagementPage>
  );
}
