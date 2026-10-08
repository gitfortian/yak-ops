# MAPPING-002：实时 MySQL CDC → PostgreSQL 主键改名

## 验证目标

验证 REALTIME Mapping：

```text
MySQL Source
name   → display_name
id PK  → user_id PK
note   → 不映射
```

在主键改名后仍能正确处理：

```text
Initial Snapshot
INSERT
UPDATE
DELETE
```

## 源端准备

MySQL：

```sql
DROP TABLE IF EXISTS e2e_mapping_realtime_source;

CREATE TABLE e2e_mapping_realtime_source (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    note VARCHAR(100)
);

INSERT INTO e2e_mapping_realtime_source VALUES
(1, 'alpha', 'ignored-1'),
(2, 'beta', 'ignored-2');
```

源端仍需满足 MySQL CDC 的 ROW Binlog 与复制权限前置条件。

## 目标端准备

PostgreSQL：

```sql
DROP TABLE IF EXISTS e2e_mapping_realtime_target;
```

目标表必须保持不存在，用于同时验证 Auto Create + 映射后主键建表。

## Yak Ops 操作

1. 新建实时同步任务。
2. Source 选择 `e2e_mapping_realtime_source`。
3. Target 开启“自动建表”，目标表名输入 `e2e_mapping_realtime_target`。
4. 在“去向字段映射”中配置：
   - `name → display_name`
   - `id → user_id`
5. 不映射 `note`。
6. 确认 Preview 显示兼容，并通过目标表 DDL 入口确认计划只包含 `display_name / user_id`，且 `user_id` 为主键。
7. 保存并上线。
8. 从实时任务列表点击“启动”。

## Auto Create + Snapshot 验证

先确认目标表已经由 Runtime 创建：

```sql
SELECT
    column_name,
    is_nullable,
    data_type
FROM information_schema.columns
WHERE table_name = 'e2e_mapping_realtime_target'
ORDER BY ordinal_position;
```

确认只有 `display_name / user_id` 两个业务字段。

再确认主键：

```sql
SELECT a.attname
FROM pg_index i
CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord)
JOIN pg_attribute a
  ON a.attrelid = i.indrelid
 AND a.attnum = k.attnum
WHERE i.indrelid = 'e2e_mapping_realtime_target'::regclass
  AND i.indisprimary
ORDER BY k.ord;
```

预期主键为：

```text
user_id
```

然后验证 Snapshot。

PostgreSQL：

```sql
SELECT user_id, display_name
FROM e2e_mapping_realtime_target
ORDER BY user_id;
```

预期：

```text
1 | alpha
2 | beta
```

## INSERT 验证

MySQL：

```sql
INSERT INTO e2e_mapping_realtime_source(id, name, note)
VALUES (3, 'gamma', 'ignored-3');
```

等待目标端出现：

```text
3 | gamma
```

## UPDATE 验证

MySQL：

```sql
UPDATE e2e_mapping_realtime_source
SET name = 'alpha-v2', note = 'ignored-v2'
WHERE id = 1;
```

等待 PostgreSQL：

```text
1 | alpha-v2
```

这一步重点验证 Source PK `id` 已改名为 Target PK `user_id` 后仍能正确定位记录。

## DELETE 验证

MySQL：

```sql
DELETE FROM e2e_mapping_realtime_source
WHERE id = 2;
```

等待 PostgreSQL 中 `user_id = 2` 被删除。

最终：

```sql
SELECT user_id, display_name
FROM e2e_mapping_realtime_target
ORDER BY user_id;
```

预期：

```text
1 | alpha-v2
3 | gamma
```

## 实时任务列表启停与续传验证

保持当前 Task / definitionVersion 不变。

从实时任务列表点击：

```text
停止
```

预期当前活动 Execution 进入：

```text
CANCELED / 已停止
```

用户主动 Stop 会把 `desiredState` 置为 `STOPPED`，因此按钮回到“启动”；这里不把它误写成“重新启动”。

停止期间在 MySQL 新增：

```sql
INSERT INTO e2e_mapping_realtime_source(id, name, note)
VALUES (4, 'delta', 'ignored-4');
```

确认目标端暂时没有 `user_id = 4`。

随后仍从实时任务列表点击：

```text
启动
```

等待目标端出现：

```text
4 | delta
```

再次查询：

```sql
SELECT user_id, display_name
FROM e2e_mapping_realtime_target
ORDER BY user_id;
```

预期：

```text
1 | alpha-v2
3 | gamma
4 | delta
```

不得重新出现已经 DELETE 的 `user_id = 2`。这一步验证同一个 Task / definitionVersion 停止后再次启动继续复用持久化 CDC state，而不是重新做 fresh snapshot。

说明：UI 文案“重新启动”专门用于 `desiredState=RUNNING` 但没有 Active Execution 的差异状态；该状态机由自动 Contract / Acceptance 覆盖，本手工场景验证用户主动 Stop 后的“停止 → 再次启动”产品路径。

## 验收清单

- [ ] 目标表在运行时自动创建。
- [ ] 自动建表后只有 `display_name / user_id` 业务字段。
- [ ] 自动建表后 `user_id` 为主键。
- [ ] Snapshot 使用目标字段名写入。
- [ ] `note` 未进入目标表。
- [ ] `id → user_id` 主键映射通过后端校验。
- [ ] INSERT 正确。
- [ ] UPDATE 能通过映射后的主键定位目标记录。
- [ ] DELETE 能通过映射后的主键删除目标记录。
- [ ] 实时任务列表可以停止当前活动 Execution。
- [ ] Stop 后按钮回到“启动”，没有错误显示为“重新启动”。
- [ ] 停止期间 Source 新增事件不会提前写入 Target。
- [ ] 再次启动后继续消费停止期间的 `id = 4`。
- [ ] 再次启动没有退化为 fresh snapshot，也没有恢复已删除的 `user_id = 2`。
- [ ] 没有把运行结果描述为 exactly-once。

## 清理

先在 Yak Ops 停止实时任务，再执行：

```sql
DROP TABLE IF EXISTS e2e_mapping_realtime_source;
```

```sql
DROP TABLE IF EXISTS e2e_mapping_realtime_target;
```
