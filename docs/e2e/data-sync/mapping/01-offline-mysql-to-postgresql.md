# MAPPING-001：离线 MySQL → PostgreSQL 字段映射

## 验证目标

验证离线任务可以通过 Schema Mapping Editor 配置：

```text
MySQL Source
name   → display_name
id     → user_id
amount → 不映射
```

并调整顺序为：

```text
name, id
```

目标 PostgreSQL 表不存在时开启自动建表，最终只生成并写入 `display_name`、`user_id`。

## 源端准备

MySQL：

```sql
DROP TABLE IF EXISTS e2e_mapping_offline_source;

CREATE TABLE e2e_mapping_offline_source (
    id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '显示名称',
    amount DECIMAL(10,2) COMMENT '交易金额',
    PRIMARY KEY (id)
) COMMENT='离线字段映射源表';

INSERT INTO e2e_mapping_offline_source VALUES
(1, 'yak', 10.25),
(2, 'flow', 20.50),
(3, 'mapping', 30.75);
```

## 目标端准备

PostgreSQL：

```sql
DROP TABLE IF EXISTS e2e_mapping_offline_target;
```

目标表必须保持不存在状态，用于验证自动建表。

## Yak Ops 操作

1. 准备可连接上述 MySQL / PostgreSQL 的数据源。
2. 新建离线同步任务。
3. 来源表选择 `e2e_mapping_offline_source`。
4. 目标表名输入 `e2e_mapping_offline_target`，开启“自动建表”。
5. 在“去向字段映射”中清空默认映射。
6. 建立以下映射，并保持顺序：
   - `name → display_name`
   - `id → user_id`
7. 不映射 `amount`。
8. 点击目标表右侧 DDL 入口，确认完整 DDL：
   - 只包含目标字段 `display_name`、`user_id`。
   - `user_id` 为主键。
   - PostgreSQL Comment DDL 保留来源表 / 字段 Comment。
9. 保存并上线任务。
10. 运行任务。
11. 等待本次实例进入 `SUCCEEDED`。
12. 打开本次 Execution 的“配置快照”，确认运行策略为 `AUTO`，并能看到冻结后的 Effective Runtime Config / Planning Summary；不要在前端重新计算这些参数。

## 结果验证

PostgreSQL：

```sql
SELECT display_name, user_id
FROM e2e_mapping_offline_target
ORDER BY user_id;
```

预期：

```text
yak      | 1
flow     | 2
mapping  | 3
```

再确认目标表不存在 `amount` 字段。

验证 PostgreSQL Comment：

```sql
SELECT obj_description('e2e_mapping_offline_target'::regclass) AS table_comment;

SELECT
    a.attname,
    col_description(a.attrelid, a.attnum) AS column_comment
FROM pg_attribute a
WHERE a.attrelid = 'e2e_mapping_offline_target'::regclass
  AND a.attnum > 0
  AND NOT a.attisdropped
ORDER BY a.attnum;
```

预期至少确认：

```text
table_comment = 离线字段映射源表

display_name → 显示名称
user_id      → 用户ID
```

Execution “配置快照”中还应确认：

```text
Runtime Policy = AUTO
Effective Runtime Config = 已冻结
Planning Summary = 已记录
```

不要求人工用固定数字断言 batch size / parallelism；它们由 Planner 根据 Schema / Statistics / Target 类型推导。

## 验收清单

- [ ] Schema Mapping Editor 可以改名。
- [ ] Mapping 顺序可以调整为 `name, id`。
- [ ] `amount` 未进入 Mapping。
- [ ] Preview 显示 Schema 兼容。
- [ ] DDL 预览使用目标字段名并展示完整 CREATE / COMMENT 计划。
- [ ] 自动建表后 `user_id` 为主键。
- [ ] Target Table Comment 已保留。
- [ ] `display_name` / `user_id` Column Comment 已按 Mapping 后字段名保留。
- [ ] 实例最终为 `SUCCEEDED`。
- [ ] Execution 配置快照显示 `AUTO` Runtime Policy。
- [ ] Effective Runtime Config / Planning Summary 已冻结并可观察。
- [ ] 目标数据与预期一致。

## 清理

```sql
DROP TABLE IF EXISTS e2e_mapping_offline_source;
```

```sql
DROP TABLE IF EXISTS e2e_mapping_offline_target;
```
