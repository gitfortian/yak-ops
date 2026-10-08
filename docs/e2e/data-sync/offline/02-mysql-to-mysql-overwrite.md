# OFFLINE-002：MySQL → MySQL / OVERWRITE

## 验证目标

验证 Yak Ops 离线同步的 `OVERWRITE` 写入语义。

本用例重点证明：

- 任务可以通过“保存并上线”进入可运行状态。
- 运行入口只在运维中心。
- `OVERWRITE` 会在写入源端数据前清空目标表。
- 目标端原有数据不会被保留。
- 实例最终到达 `SUCCEEDED`。
- `readRows / writeRows` 与本次实际同步数据量一致。

`OVERWRITE` 是破坏性写入方式。本用例必须使用非生产数据库。

## 前置条件

- Yak Ops 正常运行。
- 已准备一个非生产 MySQL 环境。
- Yak Ops 中已存在两个可用数据源：
  - 源端绑定 `yak_e2e_source`。
  - 目标端绑定 `yak_e2e_target`。
- 两个数据源均通过连接校验。

## 1. 准备源端

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_source;
USE yak_e2e_source;

DROP TABLE IF EXISTS e2e_offline_user_overwrite;

CREATE TABLE e2e_offline_user_overwrite (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_overwrite (id, name, balance, created_at) VALUES
(1, 'Alice', 100.50, '2026-09-29 09:00:00'),
(2, 'Bob',   200.00, '2026-09-29 09:01:00'),
(3, 'Carol', 300.75, '2026-09-29 09:02:00');

SELECT id, name, balance, created_at
FROM e2e_offline_user_overwrite
ORDER BY id;
```

预期源端恰好有 3 条数据。

## 2. 准备目标端

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_target;
USE yak_e2e_target;

DROP TABLE IF EXISTS e2e_offline_user_overwrite;

CREATE TABLE e2e_offline_user_overwrite (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_overwrite (id, name, balance, created_at) VALUES
(100, 'Legacy-A', 900.00, '2026-09-29 08:00:00'),
(200, 'Legacy-B', 800.00, '2026-09-29 08:01:00');
```

这里故意预置 `100 / 200` 两条数据，用于证明运行后目标表确实被覆盖。

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
任务名称：e2e_offline_mysql_overwrite
同步类型：OFFLINE
源端：绑定 yak_e2e_source 的 MySQL 数据源
源表：e2e_offline_user_overwrite
目标端：绑定 yak_e2e_target 的 MySQL 数据源
目标表：e2e_offline_user_overwrite
写入方式：OVERWRITE
```

确认字段映射：

```text
id         → id
name       → name
balance    → balance
created_at → created_at
```

选择 `OVERWRITE` 后，页面应出现覆盖写入风险提示，明确目标表会先被清空，失败时原数据不会自动恢复。

点击：

```text
保存并上线
```

预期：

```text
任务保存成功
   ↓
任务状态：已上线（PUBLISHED）
```

## 4. 从运维中心运行任务

打开：

```text
运维中心
  ↓
离线任务
  ↓
任务
```

找到 `e2e_offline_mysql_overwrite`，点击：

```text
运行
```

预期实例生命周期：

```text
PENDING
   ↓
RUNNING
   ↓
SUCCEEDED
```

实例详情最终应显示：

```text
readRows = 3
writeRows = 3
```

## 5. 验证目标端

```sql
USE yak_e2e_target;

SELECT id, name, balance, created_at
FROM e2e_offline_user_overwrite
ORDER BY id;
```

预期只剩源端 3 条数据：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

验证总行数：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_offline_user_overwrite;
```

预期：

```text
3
```

验证旧数据已经被清除：

```sql
SELECT COUNT(*) AS legacy_count
FROM e2e_offline_user_overwrite
WHERE id IN (100, 200);
```

预期：

```text
0
```

## 6. 验收清单

- [ ] 执行前源表恰好包含 3 条数据。
- [ ] 执行前目标表存在 `100 / 200` 两条旧数据。
- [ ] `OVERWRITE` 页面风险提示正常展示。
- [ ] 任务通过“保存并上线”进入 `PUBLISHED`。
- [ ] 数据集成页面不直接提供运行入口。
- [ ] 运维中心可以运行该任务。
- [ ] 实例最终为 `SUCCEEDED`。
- [ ] `readRows = 3`。
- [ ] `writeRows = 3`。
- [ ] 目标端最终恰好 3 条数据。
- [ ] `100 / 200` 旧数据均不存在。
- [ ] 目标端 `1 / 2 / 3` 与源端字段值一致。

任意一项未通过，本用例即失败。

## 7. 清理

```sql
DROP TABLE IF EXISTS yak_e2e_source.e2e_offline_user_overwrite;
DROP TABLE IF EXISTS yak_e2e_target.e2e_offline_user_overwrite;
```

验证完成后，可以在 Yak Ops 中先下线再删除该 E2E 任务。
