# REALTIME-002：MySQL CDC 停止后续传

## 验证目标

验证同一个 `REALTIME` Task、同一个 `definitionVersion` 在用户主动停止后重新启动时，会复用已经持久化的 Debezium state，并从已完成 offset 继续消费。

本用例重点证明：

- 第一次启动完成初始化快照。
- 停止后旧 Instance 进入 `CANCELED`。
- 停止期间产生的新 Binlog 事件不会丢失。
- 不修改 executable definition 的情况下，第二次启动继续使用原 `definitionVersion`。
- 第二个 Instance 只消费停止期间新增的 1 个业务事件。
- 第二次运行不会静默退化成 fresh snapshot。
- 产品语义是 at-least-once，不声明 exactly-once。

## 前置条件

- Yak Ops 正常运行。
- 源端 MySQL 满足 MySQL CDC 要求：
  - `log_bin = ON`。
  - `binlog_format = ROW`。
  - `binlog_row_image = FULL`。
- CDC 账号权限满足当前能力契约。
- 已存在两个可用数据源：
  - 源端绑定 `yak_e2e_realtime_source`。
  - 目标端绑定 `yak_e2e_realtime_target`。
- 两个数据源连接校验通过。

## 1. 准备源端

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_source;
USE yak_e2e_realtime_source;

DROP TABLE IF EXISTS e2e_realtime_resume_user;

CREATE TABLE e2e_realtime_resume_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_realtime_resume_user (id, name, balance, updated_at) VALUES
(1, 'Alice', 100.50, '2026-09-29 09:20:00'),
(2, 'Bob',   200.00, '2026-09-29 09:21:00'),
(3, 'Carol', 300.75, '2026-09-29 09:22:00');
```

## 2. 准备目标端

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_target;
USE yak_e2e_realtime_target;

DROP TABLE IF EXISTS e2e_realtime_resume_user;

CREATE TABLE e2e_realtime_resume_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
```

确认目标端为空：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_realtime_resume_user;
```

预期：

```text
0
```

## 3. 创建并上线实时任务

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
任务名称：e2e_realtime_resume
同步类型：REALTIME
源表：e2e_realtime_resume_user
目标表：e2e_realtime_resume_user
```

确认：

- Source / Target 主键字段集合完全一致。
- 自动字段映射兼容。
- 记录当前 `checkpointIntervalSeconds`。
- 使用正常 V1 默认运行参数即可。

点击：

```text
保存并上线
```

记录当前任务版本：

```text
definitionVersion = V
```

后续不得修改 executable definition。

## 4. 第一次启动并完成初始化快照

打开：

```text
运维中心
  ↓
实时任务
  ↓
任务
```

找到 `e2e_realtime_resume`，点击：

```text
启动
```

记该实例为：

```text
Instance A
```

等待目标端出现初始化快照：

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_resume_user
ORDER BY id;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

Instance A 预期至少达到：

```text
状态：RUNNING
readRows = 3
writeRows = 3
```

为了让停止点尽量落在已经完成的 checkpoint 之后，目标数据稳定后继续等待：

```text
至少 2 × checkpointIntervalSeconds
```

期间不要再修改源表。

## 5. 停止 Instance A

在运维中心点击：

```text
停止
```

预期：

```text
RUNNING
   ↓
CANCELED
```

确认任务本身仍然：

```text
已上线（PUBLISHED）
definitionVersion = V
```

不要下线、不要编辑 executable definition。

## 6. 停止期间产生一条新数据

在源端执行：

```sql
USE yak_e2e_realtime_source;

INSERT INTO e2e_realtime_resume_user (id, name, balance, updated_at)
VALUES (4, 'David', 400.25, '2026-09-29 09:23:00');
```

此时目标端不应立刻出现 `id = 4`：

```sql
USE yak_e2e_realtime_target;

SELECT COUNT(*) AS row_count
FROM e2e_realtime_resume_user
WHERE id = 4;
```

预期：

```text
0
```

## 7. 第二次启动并验证续传

回到：

```text
运维中心
  ↓
实时任务
  ↓
任务
```

确认仍是同一个任务、同一个：

```text
definitionVersion = V
```

再次点击：

```text
启动
```

记新实例为：

```text
Instance B
```

Instance B 必须是新的 Instance，不是恢复旧的 `CANCELED` Instance。

等待 `id = 4` 出现在目标端：

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_resume_user
WHERE id = 4;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 4 | David | 400.25 |

在本测试前已经等待 checkpoint 稳定、且停止期间只新增 1 个业务事件，因此 Instance B 预期：

```text
readRows = 1
writeRows = 1
```

这个指标是本用例证明“续传而不是 fresh snapshot”的关键证据。

如果第二次运行重新执行完整初始化快照，通常会出现以下任一异常：

- Instance B 读取事件数明显大于 `1`。
- 目标端已有主键数据被再次处理。
- 运行失败。
- 结果与本用例预期不一致。

这些情况都按失败处理。

## 8. 验证最终数据

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_resume_user
ORDER BY id;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |
| 4 | David | 400.25 |

总行数：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_realtime_resume_user;
```

预期：

```text
4
```

验证完成后停止 Instance B。

## 9. at-least-once 边界

Yak Ops V1 的实时恢复语义是 at-least-once。

一般情况下，最新 checkpoint 之后少量未确认事件可能在恢复时再次消费。本用例通过“数据稳定后等待至少两个 checkpoint 周期，再停止”的方式，把测试停止点放到已经确认的 offset 之后，因此预期第二次运行只产生 `1 / 1` 事件计数。

如果不能得到该结果，不应把它解释为“正常重复”，而应先排查 checkpoint / offset continuation 是否符合当前 V1 契约。

## 10. 验收清单

- [ ] 第一次启动前源端恰好 3 条数据。
- [ ] 第一次启动前目标端为空。
- [ ] 任务通过“保存并上线”进入 `PUBLISHED`。
- [ ] 已记录初始 `definitionVersion = V`。
- [ ] Instance A 完成初始化快照，目标端出现 `1 / 2 / 3`。
- [ ] Instance A 达到 `readRows = 3 / writeRows = 3`。
- [ ] 稳定后至少等待两个 checkpoint 周期。
- [ ] 停止 Instance A 后状态为 `CANCELED`。
- [ ] 停止后任务仍为 `PUBLISHED`。
- [ ] 停止后 `definitionVersion` 仍为 `V`。
- [ ] 停止期间新增 `id = 4`，目标端当时不存在该数据。
- [ ] 第二次启动生成新的 Instance B。
- [ ] Instance B 仍对应 `definitionVersion = V`。
- [ ] Instance B 将 `id = 4` 同步到目标端。
- [ ] Instance B 达到 `readRows = 1 / writeRows = 1`。
- [ ] 目标端最终恰好存在 `1 / 2 / 3 / 4` 四条数据。
- [ ] 没有出现 fresh snapshot 行为。

任意一项未通过，本用例即失败。

## 11. 清理

```sql
DROP TABLE IF EXISTS yak_e2e_realtime_source.e2e_realtime_resume_user;
DROP TABLE IF EXISTS yak_e2e_realtime_target.e2e_realtime_resume_user;
```

验证完成后，停止运行中的实例，并按产品生命周期先下线再删除 E2E 任务。
