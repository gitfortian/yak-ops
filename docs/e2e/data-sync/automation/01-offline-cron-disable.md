# AUTOMATION-001：离线 Cron 调度与停用

## 验证目标

验证已上线的 `OFFLINE` Task 可以按 Cron 自动创建 Execution，并且停用 Schedule 后不再继续触发。

本用例重点证明：

- Schedule 使用显式时区。
- Operations Center 能看到 Cron、下次运行时间和调度状态。
- 到点后创建新的 `SCHEDULE` Execution。
- Execution 仍走正常 Offline Runtime。
- 停用 Schedule 后 `Next Run` 消失。
- 停用后跨过至少两个原计划触发点，不再产生新的 Execution。

`SKIP_IF_RUNNING` 由自动化 Acceptance 强制验证，本手工用例不通过人为制造慢任务来重复证明。

## 前置条件

- Yak Ops 正常运行。
- 已存在两个可用 MySQL 数据源：
  - 来源绑定 `yak_e2e_automation_source`。
  - 目标绑定 `yak_e2e_automation_target`。
- 两个数据源连接校验通过。
- 当前版本尚未提供 Schedule 配置 UI，因此**仅 Schedule 配置步骤**允许使用对外 Data Sync HTTP 接口。
- Task 创建、上线、运行态观察和结果验证仍通过 Yak Ops UI / 数据库完成。
- 不允许直接修改 `yak_ops_data_sync_schedule`。

## 1. 准备来源表

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_automation_source;
USE yak_e2e_automation_source;

DROP TABLE IF EXISTS e2e_automation_cron_user;

CREATE TABLE e2e_automation_cron_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL
);

INSERT INTO e2e_automation_cron_user (id, name, created_at) VALUES
(1, 'Alice', '2026-09-30 08:00:00'),
(2, 'Bob',   '2026-09-30 08:01:00'),
(3, 'Carol', '2026-09-30 08:02:00');
```

确认：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_automation_cron_user;
```

预期：

```text
3
```

## 2. 准备目标表

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_automation_target;
USE yak_e2e_automation_target;

DROP TABLE IF EXISTS e2e_automation_cron_user;

CREATE TABLE e2e_automation_cron_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL
);
```

确认目标端为空。

## 3. 创建并上线离线任务

打开：

```text
数据集成
  ↓
离线同步
  ↓
新建任务
```

配置：

```text
任务名称：e2e_automation_offline_cron
同步类型：OFFLINE
来源表：e2e_automation_cron_user
目标表：e2e_automation_cron_user
写入方式：APPEND
```

字段映射全部兼容后点击：

```text
保存并上线
```

记录 Task ID：

```text
TASK_ID
```

## 4. 配置 Schedule

使用与当前登录用户相同的授权上下文调用公开 HTTP 接口。

保存 Schedule：

```text
PUT /api/v1/data-sync/tasks/{TASK_ID}/schedule
```

请求体：

```json
{
  "cronExpression": "0/30 * * * * ?",
  "timeZone": "Asia/Shanghai"
}
```

然后启用：

```text
POST /api/v1/data-sync/tasks/{TASK_ID}/schedule/enable
```

这里使用每 30 秒一次，仅为了缩短人工验收时间。

## 5. 在 Operations Center 验证调度运行态

打开：

```text
运维中心
  ↓
离线任务
  ↓
任务
```

找到 `e2e_automation_offline_cron`。

预期至少看到：

```text
自动化：调度开启
Cron：0/30 * * * * ?
Time Zone：Asia/Shanghai
下次运行：有明确时间
```

`Next Run` 必须来自 Scheduler Runtime，不能只显示 Cron 文本。

## 6. 等待第一次自动触发

等待进入下一个 30 秒触发点。

打开“运行实例”。

预期出现新的 Execution：

```text
triggerType = SCHEDULE
PENDING
  ↓
RUNNING
  ↓
SUCCEEDED
```

最终应看到：

```text
Attempt：1 / 1
readRows = 3
writeRows = 3
```

## 7. 验证目标端

```sql
USE yak_e2e_automation_target;

SELECT id, name, created_at
FROM e2e_automation_cron_user
ORDER BY id;
```

预期：

| id | name |
| ---: | --- |
| 1 | Alice |
| 2 | Bob |
| 3 | Carol |

## 8. 停用 Schedule

在第二个触发点到来前执行：

```text
POST /api/v1/data-sync/tasks/{TASK_ID}/schedule/disable
```

回到 Operations Center。

预期：

```text
自动化：调度关闭
下次运行：-
```

记录当前该 Task 的 Execution 数量：

```text
N
```

## 9. 验证停用后不再触发

等待至少：

```text
70 秒
```

也就是跨过至少两个原来的 30 秒触发点。

刷新该 Task 的运行实例。

预期：

```text
Execution 数量仍然 = N
```

不得新增 `SCHEDULE` Execution。

## 10. 验收清单

- [ ] 来源端初始化数据恰好 3 条。
- [ ] 目标端开始为空。
- [ ] Task 已上线。
- [ ] Schedule 保存成功。
- [ ] Schedule 启用成功。
- [ ] Operations Center 显示 Cron / Time Zone / Next Run。
- [ ] 到点后自动创建 `SCHEDULE` Execution。
- [ ] 自动 Execution 最终 `SUCCEEDED`。
- [ ] `readRows = 3 / writeRows = 3`。
- [ ] 目标端数据正确。
- [ ] 停用 Schedule 后 Operations Center 显示调度关闭。
- [ ] 停用后 `Next Run = -`。
- [ ] 跨过至少两个原触发点后没有新增 Execution。

任意一项未通过，本用例失败。

## 11. 清理

```sql
DROP TABLE IF EXISTS yak_e2e_automation_source.e2e_automation_cron_user;
DROP TABLE IF EXISTS yak_e2e_automation_target.e2e_automation_cron_user;
```

最后按产品生命周期下线并删除 E2E Task。
