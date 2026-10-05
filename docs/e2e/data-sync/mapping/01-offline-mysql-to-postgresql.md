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
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2)
);

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
8. 确认 CREATE TABLE 预览只包含目标字段 `display_name`、`user_id`，且 `user_id` 为主键。
9. 保存并上线任务。
10. 运行任务。
11. 等待本次实例进入 `SUCCEEDED`。

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

## 验收清单

- [ ] Schema Mapping Editor 可以改名。
- [ ] Mapping 顺序可以调整为 `name, id`。
- [ ] `amount` 未进入 Mapping。
- [ ] Preview 显示 Schema 兼容。
- [ ] CREATE TABLE 预览使用目标字段名。
- [ ] 自动建表后 `user_id` 为主键。
- [ ] 实例最终为 `SUCCEEDED`。
- [ ] 目标数据与预期一致。

## 清理

```sql
DROP TABLE IF EXISTS e2e_mapping_offline_source;
```

```sql
DROP TABLE IF EXISTS e2e_mapping_offline_target;
```
