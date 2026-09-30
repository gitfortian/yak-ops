# AUTOMATION-002：实时同步进程重启自动恢复

## 验证目标

验证用户希望 REALTIME Task 持续运行时，Yak Ops 进程异常退出或重启后会：

```text
旧 Active Execution
        ↓
LOST
        ↓
desiredState = RUNNING
        ↓
新 AUTO_RECOVERY Execution
        ↓
复用同 taskId + definitionVersion 的 CDC state
        ↓
继续消费已完成 offset 之后的 Binlog
```

本用例重点证明：

- 用户没有点击 Stop，因此 `desiredState` 保持 `RUNNING`。
- 旧 Execution 不会被复活，而是变为 `LOST`。
- 重启后创建新的 `AUTO_RECOVERY` Execution。
- 新 Execution 使用相同 `definitionVersion`。
- 停机期间产生的 Binlog 可以在重启后继续消费。
- 不会静默退化成 fresh snapshot。
- 语义仍然是 at-least-once。

## 前置条件

- Yak Ops 正常运行。
- 源端 MySQL 满足 CDC 要求：
  - `log_bin = ON`
  - `binlog_format = ROW`
  - `binlog_row_image = FULL`
- CDC 账号权限满足当前能力契约。
- 已存在两个可用数据源：
  - 来源绑定 `yak_e2e_realtime_source`。
  - 目标绑定 `yak_e2e_realtime_target`。
- Yak Ops 的 `${yak.ops.home}/data` 在应用重启后仍然保留。
- 如果使用容器部署，只允许重启应用容器 / 服务，不得删除持久化数据卷。

## 1. 准备来源表

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_source;
USE yak_e2e_realtime_source;

DROP TABLE IF EXISTS e2e_realtime_auto_recovery;

CREATE TABLE e2e_realtime_auto_recovery (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_realtime_auto_recovery (id, name, updated_at) VALUES
(1, 'Alice', '2026-09-30 08:10:00'),
(2, 'Bob',   '2026-09-30 08:11:00'),
(3, 'Carol', '2026-09-30 08:12:00');
```

## 2. 准备目标表

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_target;
USE yak_e2e_realtime_target;

DROP TABLE IF EXISTS e2e_realtime_auto_recovery;

CREATE TABLE e2e_realtime_auto_recovery (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
```

确认目标端为空。

## 3. 创建并上线 REALTIME Task

打开：

```text
数据集成
  ↓
实时同步
  ↓
新建任务
```

配置：

```text
任务名称：e2e_realtime_auto_recovery
同步类型：REALTIME
来源表：e2e_realtime_auto_recovery
目标表：e2e_realtime_auto_recovery
```

确认主键、字段映射全部兼容。

点击：

```text
保存并上线
```

记录：

```text
definitionVersion = V
```

后续不要编辑 executable definition。

## 4. 启动并等待稳定 Checkpoint

打开：

```text
运维中心
  ↓
实时任务
  ↓
任务
```

点击：

```text
启动
```

记当前 Execution 为：

```text
Execution A
```

Operations Center 预期：

```text
Desired State：期望运行
运行状态：运行中
启动方式：手动
```

等待目标端出现 3 条初始化数据：

```sql
SELECT id, name, updated_at
FROM yak_e2e_realtime_target.e2e_realtime_auto_recovery
ORDER BY id;
```

然后继续等待：

```text
至少 2 × checkpointIntervalSeconds
```

期间不要修改源端。

## 5. 重启 Yak Ops，但不要点击 Stop

**不要在 UI 点击“停止”。**

直接按当前部署方式重启 Yak Ops 应用进程 / 容器。

要求：

- 数据库保持运行。
- `${yak.ops.home}/data` 保持不变。
- 不删除 CDC state。
- 不修改 Task。
- 不修改 `definitionVersion`。

应用停止期间，在源端新增一条数据：

```sql
INSERT INTO yak_e2e_realtime_source.e2e_realtime_auto_recovery (id, name, updated_at)
VALUES (4, 'David', '2026-09-30 08:13:00');
```

此时目标端应仍然只有 3 条。

## 6. 启动 Yak Ops

重新启动应用。

等待应用完成启动恢复。

打开：

```text
运维中心
  ↓
实时任务
  ↓
运行实例
```

预期看到：

### Execution A

```text
状态：LOST
```

### 新 Execution B

```text
Execution ID：与 A 不同
triggerType：AUTO_RECOVERY
任务版本：V
状态：PENDING → RUNNING
```

任务页应继续显示：

```text
Desired State：期望运行
最近由自动恢复拉起
```

## 7. 验证停机期间事件继续消费

等待目标端出现 `id = 4`：

```sql
SELECT id, name, updated_at
FROM yak_e2e_realtime_target.e2e_realtime_auto_recovery
WHERE id = 4;
```

预期：

| id | name |
| ---: | --- |
| 4 | David |

最终：

```sql
SELECT id, name, updated_at
FROM yak_e2e_realtime_target.e2e_realtime_auto_recovery
ORDER BY id;
```

预期恰好：

```text
1 / 2 / 3 / 4
```

## 8. 验证不是 Fresh Snapshot

在本用例中，重启前已经等待至少两个 Checkpoint 周期，并且停机期间只新增 1 个业务事件。

因此 Execution B 预期只处理停机期间的新事件。

Operations Center 中优先检查：

```text
Execution B readRows = 1
Execution B writeRows = 1
```

如果出现完整 4 行初始化快照再次执行，或者 Execution B 的读取/写入事件明显包含旧的 1 / 2 / 3，则按失败处理。

这条证据与 CI 的 MySQL CDC Cross-Database Acceptance 一起证明：

> AUTO_RECOVERY 继续使用同 taskId + definitionVersion 的持久化 CDC state，而不是 fresh snapshot。

## 9. 用户 Stop 后不得自动恢复

在 Execution B 正常运行时，通过 UI 点击：

```text
停止
```

预期：

```text
desiredState = STOPPED
Execution B = CANCELED
```

再次重启 Yak Ops。

预期：

```text
不会创建 Execution C
```

这一步证明：

> 用户主动 Stop 与进程异常中断的恢复语义不同。

## 10. 验收清单

- [ ] 初始 Source 恰好 3 条数据。
- [ ] 初始 Target 为空。
- [ ] Task 已上线并记录 `definitionVersion = V`。
- [ ] 手工 Start 后 Desired State 显示“期望运行”。
- [ ] Execution A 完成初始化快照并进入 RUNNING。
- [ ] 重启前至少等待两个 Checkpoint 周期。
- [ ] 重启应用时没有点击 Stop。
- [ ] 停机期间只新增 `id = 4`。
- [ ] 重启后 Execution A = LOST。
- [ ] 自动创建新的 Execution B。
- [ ] Execution B trigger = AUTO_RECOVERY。
- [ ] Execution B 仍使用 `definitionVersion = V`。
- [ ] `id = 4` 被继续同步。
- [ ] Execution B 没有退化成 fresh snapshot。
- [ ] UI Stop 后 Desired State = STOPPED。
- [ ] Stop 后再次重启不会创建新的自动恢复 Execution。

任意一项未通过，本用例失败。

## 11. 清理

先在 UI 停止运行中的 REALTIME Execution，再下线并删除 E2E Task。

然后执行：

```sql
DROP TABLE IF EXISTS yak_e2e_realtime_source.e2e_realtime_auto_recovery;
DROP TABLE IF EXISTS yak_e2e_realtime_target.e2e_realtime_auto_recovery;
```
