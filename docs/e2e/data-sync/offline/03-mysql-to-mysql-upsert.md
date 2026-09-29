# OFFLINE-003：MySQL → MySQL / UPSERT

## 验证目标

验证 Yak Ops 离线同步的 `UPSERT` 写入语义。

本用例重点证明：

- `UPSERT` 要求目标表存在主键。
- Source 映射必须包含目标主键字段。
- 已存在主键会更新。
- 不存在主键会新增。
- 与本次源数据无关的目标端旧数据会保留。
- 实例指标与本次输入数据量一致。

## 前置条件

- Yak Ops 正常运行。
- 已准备一个非生产 MySQL 环境。
- Yak Ops 中已存在：
  - 绑定 `yak_e2e_source` 的源端数据源。
  - 绑定 `yak_e2e_target` 的目标端数据源。
- 两个数据源均通过连接校验。

## 1. 准备源端

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_source;
USE yak_e2e_source;

DROP TABLE IF EXISTS e2e_offline_user_upsert;

CREATE TABLE e2e_offline_user_upsert (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_upsert (id, name, balance, updated_at) VALUES
(1, 'Alice-New', 150.00, '2026-09-29 09:10:00'),
(2, 'Bob',       200.00, '2026-09-29 09:11:00'),
(3, 'Carol',     300.75, '2026-09-29 09:12:00');
```

## 2. 准备目标端

```sql
CREATE DATABASE IF NOT EXISTS yak_e2e_target;
USE yak_e2e_target;

DROP TABLE IF EXISTS e2e_offline_user_upsert;

CREATE TABLE e2e_offline_user_upsert (
    id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO e2e_offline_user_upsert (id, name, balance, updated_at) VALUES
(1,   'Alice-Old', 10.00,  '2026-09-29 08:10:00'),
(100, 'Existing',  999.99, '2026-09-29 08:11:00');
```

同步前目标端：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice-Old | 10.00 |
| 100 | Existing | 999.99 |

其中：

- `id = 1` 用于验证 UPDATE 语义。
- `id = 2 / 3` 用于验证 INSERT 语义。
- `id = 100` 用于验证非本次 Source 数据不会被清除。

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
任务名称：e2e_offline_mysql_upsert
同步类型：OFFLINE
源表：e2e_offline_user_upsert
目标表：e2e_offline_user_upsert
写入方式：UPSERT
```

确认字段映射：

```text
id         → id
name       → name
balance    → balance
updated_at → updated_at
```

必须确认目标表主键为：

```text
id
```

如果目标表没有主键，或者字段映射不包含主键 `id`，任务不应进入可运行状态。

点击：

```text
保存并上线
```

预期任务状态：

```text
已上线（PUBLISHED）
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

找到 `e2e_offline_mysql_upsert`，点击：

```text
运行
```

预期：

```text
PENDING
   ↓
RUNNING
   ↓
SUCCEEDED
```

实例最终：

```text
readRows = 3
writeRows = 3
```

## 5. 验证目标端

```sql
USE yak_e2e_target;

SELECT id, name, balance, updated_at
FROM e2e_offline_user_upsert
ORDER BY id;
```

预期：

| id | name | balance |
| ---: | --- | ---: |
| 1 | Alice-New | 150.00 |
| 2 | Bob | 200.00 |
| 3 | Carol | 300.75 |
| 100 | Existing | 999.99 |

验证 `id = 1` 只存在一条：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_offline_user_upsert
WHERE id = 1;
```

预期：

```text
1
```

验证总行数：

```sql
SELECT COUNT(*) AS row_count
FROM e2e_offline_user_upsert;
```

预期：

```text
4
```

## 6. 验收清单

- [ ] Source / Target 都存在主键字段 `id`。
- [ ] 字段映射包含目标主键 `id`。
- [ ] 任务写入方式为 `UPSERT`。
- [ ] 任务通过“保存并上线”进入 `PUBLISHED`。
- [ ] 运维中心可以运行该任务。
- [ ] 实例最终为 `SUCCEEDED`。
- [ ] `readRows = 3`。
- [ ] `writeRows = 3`。
- [ ] `id = 1` 从 `Alice-Old / 10.00` 更新为 `Alice-New / 150.00`。
- [ ] `id = 2 / 3` 被新增。
- [ ] `id = 100` 仍然保留。
- [ ] `id = 1` 没有产生重复记录。
- [ ] 目标端最终总行数为 `4`。

任意一项未通过，本用例即失败。

## 7. 清理

```sql
DROP TABLE IF EXISTS yak_e2e_source.e2e_offline_user_upsert;
DROP TABLE IF EXISTS yak_e2e_target.e2e_offline_user_upsert;
```

验证完成后，可以在 Yak Ops 中先下线再删除该 E2E 任务。
