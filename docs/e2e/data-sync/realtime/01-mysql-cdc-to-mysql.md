# REALTIME-001：MySQL CDC → MySQL

## 验证目标

验证 Yak Ops 实时同步完整核心路径：

```text
MySQL 源端
   ↓ 初始化快照 + binlog
Yak Ops 实时同步
   ↓
YakFlow 本地执行引擎
   ↓
JdbcSink 变更日志（CHANGELOG）
   ↓
MySQL 目标端
```

本用例验证：

- 可以从 UI 配置 `REALTIME` 任务。
- 初始化快照数据能够写入目标端。
- 源端 INSERT 能够应用到目标端。
- 源端 UPDATE 能够应用到目标端。
- 源端 DELETE 能够应用到目标端。
- 实例事件计数会随 CDC 事件增加。
- 运行中的任务可以从产品 UI 停止。

本用例不验证重启续传，也不声明 exactly-once 语义。

## 前置条件

- Yak Ops 正常运行。
- 已准备一个非生产 MySQL CDC 源端。
- 已准备一个 MySQL 目标端。
- Yak Ops 中已存在两个数据源资源：
  - 源端绑定 `yak_e2e_realtime_source`。
  - 目标端绑定 `yak_e2e_realtime_target`。
- 两个数据源都能通过连接校验。
- 源端账号拥有当前 Debezium MySQL 连接器契约要求的权限：`SELECT`、`RELOAD`、`SHOW DATABASES`、`REPLICATION SLAVE`、`REPLICATION CLIENT`。
- 某些托管 MySQL 在不同锁模型下，初始化快照还可能要求 `LOCK TABLES` 等锁表权限。
- 源端 MySQL 已开启支持行级 CDC 的 binlog。

检查源端 MySQL 运行参数：

```sql
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'binlog_format';
SHOW VARIABLES LIKE 'binlog_row_image';
```

预期：

```text
log_bin          = ON
binlog_format    = ROW
binlog_row_image = FULL
```

如果当前环境采用其它有效的 CDC 配置，执行本用例前必须记录差异。

能力级前置要求参见 [MySQL CDC 源端要求](../../../capabilities/data-sync/README.md#mysql-cdc-source-requirements)。

## 1. 准备源端

在源端 MySQL 服务上执行：

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_source;
USE yak_e2e_realtime_source;

DROP TABLE IF EXISTS e2e_realtime_user;

CREATE TABLE e2e_realtime_user (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_realtime_user (id, name, balance, updated_at) VALUES
(1, 'Alice', 100.50, '2026-09-28 11:00:00'),
(2, 'Bob',   200.00, '2026-09-28 11:01:00'),
(3, 'Carol', 300.75, '2026-09-28 11:02:00');

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
ORDER BY id;
```

预期源端数据：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

当前 `REALTIME` 产品契约要求源表存在主键。目标表必须在大小写不敏感的同名字段映射下，提供完全相同的主键字段集合。

## 2. 准备目标端

在目标端 MySQL 服务上执行：

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_realtime_target;
USE yak_e2e_realtime_target;

DROP TABLE IF EXISTS e2e_realtime_user;

CREATE TABLE e2e_realtime_user (
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
FROM e2e_realtime_user;
```

预期：

```text
0
```

## 3. 创建实时同步任务

打开 Yak Ops：

```text
数据集成
  ↓
实时同步
  ↓
新建任务
```

按下面内容配置。

### 基本信息

```text
任务名称：e2e_realtime_mysql_cdc
同步类型：REALTIME
```

### 数据源

源端：

```text
数据源：绑定 yak_e2e_realtime_source 的 MySQL 数据源
```

目标端：

```text
数据源：绑定 yak_e2e_realtime_target 的 MySQL 数据源
```

### 数据来源

选择：

```text
表：e2e_realtime_user
```

### 数据去向

选择：

```text
表：e2e_realtime_user
```

### 字段映射

确认自动映射兼容：

```text
id         → id
name       → name
balance    → balance
updated_at → updated_at
```

如果映射预览提示不兼容、无法识别源端主键，或者目标端主键字段集合与源端不同，不要继续执行。后端会在运行前拒绝目标端主键缺失、多出或字段不同的情况。

### 运行参数

实时核心路径使用正常默认值，主要包括：

- checkpoint 间隔。
- CDC 队列容量。
- 轮询批次大小。
- JDBC 写入批次大小。
- 超时时间。

本核心路径验证产品行为，不验证运行参数调优效果。

## 4. 上线、启动并验证初始化快照

先在任务编辑页点击：

```text
保存并上线
```

预期：

```text
任务保存成功
   ↓
任务状态：已上线（PUBLISHED）
```

然后打开：

```text
运维中心
  ↓
实时任务
  ↓
任务
```

找到 `e2e_realtime_mysql_cdc`，点击：

```text
启动
```

预期实例生命周期：

```text
PENDING
   ↓
RUNNING
```

实时任务会持续保持 `RUNNING`，直到被停止或运行失败。

等待初始化快照出现在目标端后执行：

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
ORDER BY id;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice | 100.50 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |

此时实例应该已经处理 3 个初始化快照 INSERT 事件：

```text
readRows = 3
writeRows = 3
```

确认快照结果正确后，再执行下一次源端变更。

## 5. 验证 INSERT

在源端执行：

```sql
USE yak_e2e_realtime_source;

INSERT INTO e2e_realtime_user (id, name, balance, updated_at)
VALUES (4, 'David', 400.25, '2026-09-28 11:03:00');
```

等待 CDC 传播完成后，在目标端执行：

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
WHERE id = 4;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 4 | David | 400.25 |

INSERT 应用完成后的预期事件计数：

```text
readRows = 4
writeRows = 4
```

## 6. 验证 UPDATE

在源端执行：

```sql
USE yak_e2e_realtime_source;

UPDATE e2e_realtime_user
SET name = 'Bobby',
    balance = 250.00,
    updated_at = '2026-09-28 11:04:00'
WHERE id = 2;
```

等待 CDC 传播完成后，在目标端执行：

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
WHERE id = 2;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 2 | Bobby | 250.00 |

当前 YakFlow CDC 协议会把一次 UPDATE 发送成两个变更事件：

```text
UPDATE_BEFORE
UPDATE_AFTER
```

UPDATE 完整应用后的预期计数：

```text
readRows = 6
writeRows = 6
```

这里的计数表示变更事件数量，不是业务数据行数。

## 7. 验证 DELETE

在源端执行：

```sql
USE yak_e2e_realtime_source;

DELETE FROM e2e_realtime_user
WHERE id = 1;
```

等待 CDC 传播完成后，在目标端执行：

```sql
USE yak_e2e_realtime_target;

SELECT COUNT(*) AS row_count
FROM e2e_realtime_user
WHERE id = 1;
```

预期：

```text
0
```

DELETE 应用完成后的预期计数：

```text
readRows = 7
writeRows = 7
```

## 8. 验证目标端最终状态

执行：

```sql
USE yak_e2e_realtime_target;

SELECT id, name, balance, updated_at
FROM e2e_realtime_user
ORDER BY id;
```

预期最终业务数据：

| id | name | balance |
| ---: | --- | ---: |
| 2 | Bobby | 250.00 |
| 3 | Carol | 300.75 |
| 4 | David | 400.25 |

验证最终总行数：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_realtime_user;
```

预期：

```text
3
```

## 9. 停止实时实例

在运行中的实例页面点击：

```text
停止
```

预期：

```text
RUNNING
   ↓
CANCELED
```

实时同步 UI 可能会把 `CANCELED` 展示为“已停止”。

本步骤只验证用户主动停止，不验证下一次运行是否复用 offset；续传能力应由独立的实时续传 E2E 用例验证。

## 10. 验收清单

- [ ] MySQL CDC 源端前置条件满足，包括 binlog 模式和 CDC 账号权限。
- [ ] 源端与目标端主键字段集合在大小写不敏感的同名映射下完全一致。
- [ ] 任务启动前，源端恰好包含 3 条初始化数据。
- [ ] 任务启动前，目标端为空。
- [ ] `REALTIME` 任务通过“保存并上线”进入 `PUBLISHED`。
- [ ] 运维中心启动后实例到达 `RUNNING`。
- [ ] 初始化快照在目标端生成数据 `1 / 2 / 3`。
- [ ] 快照计数达到 `3 / 3`。
- [ ] 源端 INSERT 的数据 `4` 出现在目标端。
- [ ] INSERT 后计数达到 `4 / 4`。
- [ ] 源端 UPDATE 后，目标端数据 `2` 更新为 `Bobby / 250.00`。
- [ ] UPDATE 后计数达到 `6 / 6`。
- [ ] 源端 DELETE 数据 `1` 后，目标端数据 `1` 被删除。
- [ ] DELETE 后计数达到 `7 / 7`。
- [ ] 最终目标端只包含数据 `2 / 3 / 4`。
- [ ] 停止后，活动实例状态变为 `CANCELED / 已停止`。

任意一项未通过，都表示本 E2E 用例未通过。

## 11. 清理

删除表之前，先停止 `REALTIME` 实例。

然后执行：

```sql
DROP TABLE IF EXISTS yak_e2e_realtime_source.e2e_realtime_user;
DROP TABLE IF EXISTS yak_e2e_realtime_target.e2e_realtime_user;
```

如果不再需要，可以在 Yak Ops 中先下线再删除该 E2E 任务。

不要在本核心路径中手工删除产品管理的实时状态目录。状态生命周期和续传能力由专门的实时续传用例验证。
