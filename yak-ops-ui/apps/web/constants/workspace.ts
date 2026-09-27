export const WORKSPACE_HEADER_NAME = "X-Workspace-Id";

/** 当前登录会话正在使用的 Workspace，HTTP Client 只读取这个 Key。 */
export const WORKSPACE_STORAGE_KEY = "yak-ops.current-workspace-id";

/** 用户级 Workspace 偏好在退出登录后保留，用于下次同账号登录恢复。 */
const WORKSPACE_PREFERENCE_STORAGE_PREFIX = "yak-ops.current-workspace-id.user";

export const workspacePreferenceStorageKey = (userId: string) =>
  `${WORKSPACE_PREFERENCE_STORAGE_PREFIX}.${userId}`;
