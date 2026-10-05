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

CREATE TABLE e2e_mapping_realtime_target (
    user_id BIGINT PRIMARY KEY,
    display_name VARCHAR(100)
);
```

## Yak Ops 操作

1. 新建实时同步任务。
2. Source 选择 `e2e_mapping_realtime_source`。
3. Target 选择 `e2e_mapping_realtime_target`。
4. 在“Schema 映射”中配置：
   - `name → display_name`
   - `id → user_id`
5. 不映射 `note`。
6. 确认 Preview 显示兼容。
7. 保存并上线。
8. 启动任务。

## Snapshot 验证

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

## 验收清单

- [ ] Snapshot 使用目标字段名写入。
- [ ] `note` 未进入目标表。
- [ ] `id → user_id` 主键映射通过后端校验。
- [ ] INSERT 正确。
- [ ] UPDATE 能通过映射后的主键定位目标记录。
- [ ] DELETE 能通过映射后的主键删除目标记录。
- [ ] 没有把运行结果描述为 exactly-once。

## 清理

先在 Yak Ops 停止实时任务，再执行：

```sql
DROP TABLE IF EXISTS e2e_mapping_realtime_source;
```

```sql
DROP TABLE IF EXISTS e2e_mapping_realtime_target;
```
