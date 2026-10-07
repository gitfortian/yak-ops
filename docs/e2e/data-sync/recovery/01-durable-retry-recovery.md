# RECOVERY-001：Durable Retry Recovery

## 验证目标

验证 OFFLINE Execution 已进入 `RETRY_WAITING` 后重启 Yak Ops：

```text
Execution E1 / Attempt 1 FAILED
        ↓
RETRY_WAITING + nextRetryTime
        ↓
重启 Yak Ops
        ↓
仍然是 Execution E1
        ↓
等待原 nextRetryTime
        ↓
Attempt 2
        ↓
SUCCEEDED
```

本用例重点证明：

- `RETRY_WAITING` 不因为进程退出被改成 `LOST`。
- 重启后不创建新的 Execution Root。
- `taskVersion / definitionSnapshot / retryPolicy / nextRetryTime` 继续使用原 Execution 的冻结事实。
- 下一次执行使用递增的 Attempt No。

## 为什么这一步允许使用 HTTP

普通 Task Editor 已经内化 Retry Policy，不再向普通用户暴露 `maxAttempts / backoffSeconds`。

为了让人工验收能够稳定停留在 `RETRY_WAITING`，本用例只在“准备测试专用 Retry Policy”这一步使用已经公开的 Data Sync HTTP Endpoint：

```text
FIXED
maxAttempts = 3
backoffSeconds = 300
```

任务创建、上线、运行、详情观察和最终结果验证仍优先使用 Yak Ops UI；禁止直接修改 `yak_ops_data_sync_*` 表。

## 1. 准备 Source

MySQL：

```sql
DROP TABLE IF EXISTS e2e_durable_retry_source;

CREATE TABLE e2e_durable_retry_source (
    id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_durable_retry_source (id, name) VALUES
(1, 'alpha'),
(2, 'beta'),
(3, 'gamma');
```

## 2. 准备 Target

PostgreSQL：

```sql
DROP TABLE IF EXISTS e2e_durable_retry_target;

CREATE TABLE e2e_durable_retry_target (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
```

执行：

```sql
SELECT COUNT(*) FROM e2e_durable_retry_target;
```

预期为 `0`。

## 3. 创建 OFFLINE Task

通过 Yak Ops UI：

```text
数据集成
→ 离线同步
→ 新建任务
```

配置：

```text
任务名称：e2e_durable_retry_recovery
Source：MySQL / e2e_durable_retry_source
Target：PostgreSQL / e2e_durable_retry_target
写入模式：UPSERT
自动建表：关闭
```

保存但**暂时不要上线**。

记录 Task ID。Task ID 可以从编辑页 URL 或浏览器 Network 中取得；不要读取 Yak Ops 数据库内部表。

## 4. 设置测试专用 FIXED Retry

在已经登录 Yak Ops 的浏览器页面打开 DevTools Console。

替换下面的 `taskId`：

```js
const taskId = "替换为当前 Task ID";
const workspaceId = localStorage.getItem("yak-ops.current-workspace-id");
if (!workspaceId) throw new Error("当前 Workspace 未加载，请先在 Yak Ops 中选择 Workspace");

const headers = {
  "Content-Type": "application/json",
  "X-Workspace-Id": workspaceId,
};

const taskResponse = await fetch(`/api/v1/data-sync/tasks/${taskId}`, {
  credentials: "include",
  headers,
}).then((response) => response.json());

const task = taskResponse.data;

const payload = {
  name: task.name,
  syncType: task.syncType,
  writeMode: task.writeMode,
  sourceDataSourceId: task.sourceDataSourceId,
  sourceDatabase: task.sourceDatabase,
  sourceSchema: task.sourceSchema,
  sourceTable: task.sourceTable,
  targetDataSourceId: task.targetDataSourceId,
  targetDatabase: task.targetDatabase,
  targetSchema: task.targetSchema,
  targetTable: task.targetTable,
  autoCreateTable: Boolean(task.autoCreateTable),
  mapping: task.mapping,
  runtimeConfig: task.runtimeConfig,
  retryPolicy: {
    mode: "FIXED",
    maxAttempts: 3,
    backoffSeconds: 300,
  },
  remark: task.remark,
};

await fetch(`/api/v1/data-sync/tasks/${taskId}`, {
  method: "PUT",
  credentials: "include",
  headers,
  body: JSON.stringify(payload),
}).then((response) => response.json());
```

重新打开任务详情，确认保存成功。

这一配置只用于构造稳定人工验收窗口，不代表产品重新开放 Retry Policy UI。

## 5. 上线后制造第一次连接失败

先通过 UI 上线 Task。

确认 Source / Target 数据库此时都正常。

然后**只停止 PostgreSQL Target 服务**，MySQL Source 与 Yak Ops 保持运行。

立即从离线任务列表点击：

```text
运行
```

打开 Task Detail，等待当前 Execution 进入：

```text
RETRY_WAITING
```

Attempt History 预期：

```text
Attempt 1 = FAILED
Execution = RETRY_WAITING
maxAttempts = 3
nextRetryTime != null
```

## 6. 记录 Execution Root

为了验证“同一 Root 恢复”，可以使用公开分页 Endpoint 读取当前 Task 的 Execution ID。

仍在登录页面 DevTools Console：

```js
const page = await fetch("/api/v1/data-sync/instances/page", {
  method: "POST",
  credentials: "include",
  headers,
  body: JSON.stringify({
    pageNo: 1,
    pageSize: 20,
    taskId,
    sorts: [],
  }),
}).then((response) => response.json());

console.table(
  page.data.bizData.map((item) => ({
    id: item.id,
    status: item.status,
    currentAttempt: item.currentAttempt,
    maxAttempts: item.maxAttempts,
    nextRetryTime: item.nextRetryTime,
  })),
);
```

这是新建的测试 Task，因此此时应只有一个根 Execution。

记录：

```text
Execution ID = E
currentAttempt = 1
status = RETRY_WAITING
nextRetryTime = T
```

## 7. 在 RETRY_WAITING 期间重启 Yak Ops

保持 PostgreSQL Target 仍然停止。

在 `nextRetryTime` 之前重启 Yak Ops 应用进程 / 容器。

要求：

- 不点击 Cancel。
- 不重新点击 Run。
- 不编辑 Task。
- 不修改 Task definitionVersion。
- 不删除 Yak Ops 数据库。

Yak Ops 恢复健康后，立即查看 Task Detail。

预期：

```text
Execution 仍为 RETRY_WAITING
Attempt 1 仍为 FAILED
没有创建新的 Execution Root
```

再次查询实例分页，确认：

```text
Execution ID 仍然 = E
currentAttempt 仍然 = 1
nextRetryTime 仍然 = T
status 不是 LOST
```

如果部署重启预计可能超过 5 分钟，请重新执行本用例，并把测试 Retry Policy 的 `backoffSeconds` 调大，但不得超过接口允许的 3600 秒。

## 8. 恢复 Target，并等待原 Retry Chain 继续

在 `nextRetryTime` 到达前恢复 PostgreSQL Target 服务。

确认数据库可连接后，不做任何新的 Run 操作。

等待 `T` 到达。

预期同一个 Execution E 自动继续：

```text
Attempt 2 = PENDING → RUNNING → SUCCEEDED
Execution E = SUCCEEDED
```

Attempt History 最终应包含：

```text
Attempt 1 = FAILED
Attempt 2 = SUCCEEDED
```

不能出现：

```text
新的 MANUAL Execution
新的 AUTO_RECOVERY Execution
旧 Execution = LOST
Attempt No 重新从 1 开始
```

## 9. 验证业务结果

PostgreSQL：

```sql
SELECT id, name
FROM e2e_durable_retry_target
ORDER BY id;
```

预期：

```text
1 | alpha
2 | beta
3 | gamma
```

再确认没有重复主键或缺失数据。

## 10. 验收清单

- [ ] Task 使用测试专用 `FIXED / 3 / 300s` Retry Policy。
- [ ] Task 已上线。
- [ ] PostgreSQL Target 停止后第一次运行失败。
- [ ] Attempt 1 = `FAILED`。
- [ ] Execution 进入 `RETRY_WAITING`。
- [ ] 已记录原 Execution ID = E。
- [ ] 已记录原 `nextRetryTime = T`。
- [ ] 在 T 之前重启 Yak Ops。
- [ ] 重启后 Execution ID 仍为 E。
- [ ] 重启后状态没有变成 `LOST`。
- [ ] 重启后没有创建新的 Execution Root。
- [ ] PostgreSQL 在 T 前恢复。
- [ ] 到达 T 后自动创建 Attempt 2。
- [ ] Attempt 2 最终 `SUCCEEDED`。
- [ ] Execution E 最终 `SUCCEEDED`。
- [ ] 目标端数据与预期一致。

任意一项不满足，本用例失败。

## 11. 清理

下线并删除 E2E Task。

MySQL：

```sql
DROP TABLE IF EXISTS e2e_durable_retry_source;
```

PostgreSQL：

```sql
DROP TABLE IF EXISTS e2e_durable_retry_target;
```
