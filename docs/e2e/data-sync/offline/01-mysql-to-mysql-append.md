# OFFLINE-001：MySQL → MySQL / APPEND

## 验证目标

验证 Yak Ops 离线同步完整核心路径：

```text
MySQL 源端
   ↓
Yak Ops 离线同步
   ↓
YakFlow 本地执行引擎
   ↓
MySQL 目标端
```

本用例验证：

- 源端 / 目标端数据源可以正常选择。
- 可以发现 Catalog 表，并完成同名字段自动映射。
- 已保存的 `OFFLINE` 任务可以从 UI 启动。
- `APPEND` 不会清空目标端已有数据。
- 实例最终到达 `SUCCEEDED`。
- 持久化的 `readRows` / `writeRows` 与实际传输的源端数据行数一致。
- 目标端业务数据正确。

## 前置条件

- Yak Ops 正常运行。
- 已准备一个非生产 MySQL 环境。
- Yak Ops 中已存在两个数据源资源：
  - 一个绑定 `yak_e2e_source`。
  - 一个绑定 `yak_e2e_target`。
- 两个数据源都能通过连接校验。

两个数据源可以指向同一个 MySQL 服务，但本用例中必须使用不同数据库。

## 1. 准备源端

在 MySQL 服务上执行：

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_source;
USE yak_e2e_source;

DROP TABLE IF EXISTS e2e_offline_user_append;

CREATE TABLE e2e_offline_user_append (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_append (id, name, balance, created_at) VALUES
(1, 'Alice', 100.50, '2026-09-28 10:00:00'),
(2, 'Bob',   200.00, '2026-09-28 10:01:00'),
(3, 'Carol', 300.75, '2026-09-28 10:02:00');

SELECT id, name, balance, created_at
FROM e2e_offline_user_append
ORDER BY id;
```

预期源端数据：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

预期源端行数：

```text
3
```

## 2. 准备目标端

在 MySQL 服务上执行：

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_target;
USE yak_e2e_target;

DROP TABLE IF EXISTS e2e_offline_user_append;

CREATE TABLE e2e_offline_user_append (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_append (id, name, balance, created_at) VALUES
(100, 'Existing', 999.99, '2026-09-28 09:00:00');

SELECT id, name, balance, created_at
FROM e2e_offline_user_append
ORDER BY id;
```

同步前预期目标端数据：

| id | name | balance |
| ---: | --- | ---: |
| 100 | Existing | 999.99 |

这里故意预置 `id = 100`。它用于证明 `APPEND` 不会清空目标端已有数据。

## 3. 创建离线同步任务

打开 Yak Ops：

```text
数据集成
  ↓
离线同步
  ↓
新建任务
```

按下面内容配置。

### 基本信息

```text
任务名称：e2e_offline_mysql_append
同步类型：OFFLINE
```

### 数据源

源端：

```text
数据源：绑定 yak_e2e_source 的 MySQL 数据源
```

目标端：

```text
数据源：绑定 yak_e2e_target 的 MySQL 数据源
```

### 数据来源

选择：

```text
表：e2e_offline_user_append
```

### 数据去向

选择：

```text
表：e2e_offline_user_append
写入模式：APPEND
```

### 字段映射

确认所有字段的自动映射都兼容：

```text
id         → id
name       → name
balance    → balance
created_at → created_at
```

如果映射预览提示不兼容，不要继续执行。

### 运行参数

核心路径使用正常默认值。

保持：

```text
sourceParallelism = 1
splitSize = 空 / 禁用
```

本用例验证基础产品链路，不验证 split / 并行 Reader 行为。

## 4. 启动任务

点击：

```text
保存并运行
```

预期产品行为：

```text
任务保存成功
   ↓
实例创建
   ↓
PENDING
   ↓
RUNNING
   ↓
SUCCEEDED
```

由于数据量很小，`PENDING` 和 `RUNNING` 可能只会短暂出现。

打开实例详情，确认最终状态：

```text
状态：SUCCEEDED
readRows：3
writeRows：3
```

## 5. 验证目标端结果

执行：

```sql
USE yak_e2e_target;

SELECT id, name, balance, created_at
FROM e2e_offline_user_append
ORDER BY id;
```

预期结果：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |
| 100 | Existing | 999.99 |

验证总行数：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_offline_user_append;
```

预期：

```text
4
```

验证已同步 ID 对应的源端与目标端字段值一致：

```sql
SELECT id, name, balance, created_at
FROM e2e_offline_user_append
WHERE id IN (1, 2, 3)
ORDER BY id;
```

预期：

```text
3 条源端数据都存在，并且字段值完全一致。
```

## 6. 验收清单

- [ ] 执行前，源表恰好包含 3 条初始化数据。
- [ ] 执行前，目标表包含已有的 `id = 100` 数据。
- [ ] 可以从 UI 选择源端 / 目标端数据源和表。
- [ ] 自动字段映射兼容。
- [ ] 任务保存成功。
- [ ] 手工运行后成功创建实例。
- [ ] 实例最终状态为 `SUCCEEDED`。
- [ ] 最终 `readRows = 3`。
- [ ] 最终 `writeRows = 3`。
- [ ] 目标端包含源端数据 `1 / 2 / 3`。
- [ ] 目标端仍然保留已有数据 `100`。
- [ ] 目标端总行数恰好为 `4`。

任意一项未通过，都表示本 E2E 用例未通过。

## 7. 清理

验证完成后执行：

```sql
DROP TABLE IF EXISTS yak_e2e_source.e2e_offline_user_append;
DROP TABLE IF EXISTS yak_e2e_target.e2e_offline_user_append;
```

如果不再需要，可以在 Yak Ops 中删除该 E2E 任务。

历史实例记录可以按照数据同步产品生命周期契约继续保留。
