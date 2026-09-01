import { Radio, Select, Switch, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  getSecurityProject,
  type SecurityProjectUser,
} from '@/services/security/projects';

import type { SyncEditorState } from '../model';
import EditorSection from './EditorSection';

interface NotificationConfigSectionProps {
  editor: SyncEditorState;
  onChange: (value: SyncEditorState) => void;
}

const uniqueUsers = (
  users: SecurityProjectUser[],
): SecurityProjectUser[] => {
  const result = new Map<number, SecurityProjectUser>();
  users.forEach((user) => {
    if (Number.isSafeInteger(user.id) && user.id > 0) {
      result.set(user.id, user);
    }
  });
  return Array.from(result.values());
};

export default function NotificationConfigSection({
  editor,
  onChange,
}: NotificationConfigSectionProps) {
  const { currentProject } = useSecurityProject();
  const [projectUsers, setProjectUsers] = useState<SecurityProjectUser[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const projectId = Number(currentProject?.id);
    if (!Number.isSafeInteger(projectId) || projectId <= 0) {
      setProjectUsers([]);
      return undefined;
    }

    setLoadingUsers(true);
    getSecurityProject(projectId)
      .then((project) => {
        if (cancelled) return;
        setProjectUsers(
          uniqueUsers([
            ...(project.owners || []),
            ...(project.members || []),
          ]),
        );
      })
      .catch(() => {
        if (!cancelled) setProjectUsers([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingUsers(false);
      });

    return () => {
      cancelled = true;
    };
  }, [currentProject?.id]);

  const updateNotification = (
    patch: Partial<SyncEditorState['notification']>,
  ) => {
    onChange({
      ...editor,
      notification: {
        ...editor.notification,
        ...patch,
      },
    });
  };

  const userOptions = useMemo(() => {
    const options = projectUsers.map((user) => ({
      value: user.id,
      label: user.realName?.trim()
        ? `${user.realName.trim()} (${user.userName})`
        : user.userName,
    }));
    const known = new Set(options.map((item) => item.value));
    editor.notification.recipientUserIds.forEach((id) => {
      if (!known.has(id)) {
        options.push({
          value: id,
          label: `用户 #${id}（已不在当前项目）`,
        });
      }
    });
    return options;
  }, [editor.notification.recipientUserIds, projectUsers]);

  const active = editor.notification.enabled;
  const explicit =
    editor.notification.recipientType === 'EXPLICIT_USERS';

  return (
    <EditorSection title="通知设置">
      <div className="space-y-5">
        <div className="flex items-start justify-between gap-6 rounded-lg bg-[#f7f8fa] px-4 py-3">
          <div>
            <div className="text-[13px] font-medium text-[#344054]">
              开启任务通知
            </div>
            <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
              当前仅在重试策略耗尽或不可重试的最终失败时触发，不会为每次失败重试重复发送。
            </div>
          </div>
          <Switch
            checked={active}
            onChange={(enabled) => updateNotification({ enabled })}
          />
        </div>

        <div className="grid grid-cols-2 gap-5 max-md:grid-cols-1">
          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">
              触发条件
            </div>
            <div className="flex h-8 items-center">
              <Tag color="error">最终执行失败</Tag>
            </div>
            <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
              Stage 4.2 只开放高价值失败通知，成功通知暂不启用。
            </div>
          </div>

          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">
              通知方式
            </div>
            <div className="flex h-8 items-center gap-3">
              <Switch
                disabled={!active}
                checked={editor.notification.inAppEnabled}
                onChange={(inAppEnabled) =>
                  updateNotification({ inAppEnabled })
                }
              />
              <span className="text-[12px] text-[#344054]">站内消息</span>
            </div>
            <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
              钉钉、邮件和 Webhook 等外部渠道将在 Alert 集成阶段接入。
            </div>
          </div>
        </div>

        <div>
          <div className="mb-2 text-[12px] font-medium text-[#475467]">
            接收人
          </div>
          <Radio.Group
            disabled={!active || !editor.notification.inAppEnabled}
            value={editor.notification.recipientType}
            onChange={(event) =>
              updateNotification({
                recipientType: event.target.value,
                recipientUserIds:
                  event.target.value === 'PROJECT_OWNER'
                    ? []
                    : editor.notification.recipientUserIds,
              })
            }
          >
            <Radio value="PROJECT_OWNER">项目负责人</Radio>
            <Radio value="EXPLICIT_USERS">指定用户</Radio>
          </Radio.Group>

          {explicit ? (
            <div className="mt-3 max-w-[640px]">
              <Select
                mode="multiple"
                allowClear
                showSearch
                optionFilterProp="label"
                loading={loadingUsers}
                disabled={!active || !editor.notification.inAppEnabled}
                value={editor.notification.recipientUserIds}
                options={userOptions}
                placeholder={
                  currentProject
                    ? '选择当前项目中的接收用户'
                    : '当前没有可用的项目空间'
                }
                className="w-full"
                status={
                  active &&
                  editor.notification.inAppEnabled &&
                  editor.notification.recipientUserIds.length === 0
                    ? 'error'
                    : undefined
                }
                onChange={(recipientUserIds) =>
                  updateNotification({ recipientUserIds })
                }
              />
              <Typography.Text
                type={
                  active &&
                  editor.notification.inAppEnabled &&
                  editor.notification.recipientUserIds.length === 0
                    ? 'danger'
                    : 'secondary'
                }
                className="mt-1.5 block !text-[11px]"
              >
                {active &&
                editor.notification.inAppEnabled &&
                editor.notification.recipientUserIds.length === 0
                  ? '指定用户模式至少选择一个当前项目成员'
                  : '仅展示当前 Project 的负责人和成员；保存时只记录稳定的用户 ID。'}
              </Typography.Text>
            </div>
          ) : (
            <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
              最终失败时通知当前 Project 的 OWNER 用户，保持历史默认行为。
            </div>
          )}
        </div>
      </div>
    </EditorSection>
  );
}
