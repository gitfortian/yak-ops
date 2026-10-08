# Flyway Rules

Scope:
- `yak-ops-dao/src/main/resources/db/migration/yak-ops/**`
- `yak-ops-dao/src/main/java/io/yak/ops/dao/config/FlywayConfiguration.java`

Depends On:
- `/ARCHITECTURE.md`
- `./DAO_RULES.md`
- `./ENTITY_RULES.md`
- nearest capability rules for the schema being changed

Owns:
- Yak Ops 单一数据库 Schema 历史
- 表 / 字段 / 索引定义
- Migration 顺序
- Schema 与 Entity 的持久化契约

## Ownership

所有 Yak Ops Schema 统一由 `yak-ops-dao` 管理。

```text
yak-ops-dao
└── src/main/resources/db/migration/yak-ops
    ├── V1__baseline.sql
    ├── V2__...
    └── ...
```

Security、Datasource、Boot、Business、Core、SPI 和 Plugin 模块不得创建自己的 Flyway Bean、history table 或 migration 目录。

## Baseline Mode

已发布的 `v1.0.0` 使用 `V1__baseline.sql` 作为永久冻结基线。v1.1.0 在正式发布前把未发布、只用于可重建开发 / E2E 环境的 V2 ~ V5 Draft Migration 收口为唯一的 `V2__v1_1_0.sql` Release Migration。v1.2.0 Release Freeze 同样把未发布 V3 / V4 Draft 按原顺序收口为唯一的 `V3__v1_2_0.sql`，该 Migration 已随 v1.2.0 正式发布并永久冻结。v1.3 开发从 V4 开始使用 Draft Migration，Release Freeze 前再按本规则收口。

Migration 是否允许修改取决于它是否已经成为共享历史，而不是取决于文件编号：

- 已正式发布的 Migration 永久冻结，保持内容、顺序、文件名和 checksum。
- 已进入不可重建共享环境并需要继续保留升级历史的 Migration，同样视为冻结。
- 当前未发布 Product Version 的 Draft Migration 只允许服务可重建开发 / E2E 环境，可以在 Release Freeze 前调整、删除或 squash。
- Draft Migration 发生 checksum 变化时，开发环境应重建数据库；禁止用 Flyway `repair` 掩盖不兼容历史。
- 新环境从正式 Release Migration 链初始化；已发布升级环境保留 `flyway_schema_history`，后续只做 forward migration。
- 已删除能力的表或基础数据通过新的正式 Release Migration 清理，不能回改负责创建它们的已发布 Migration。

## Draft Migration vs Release Migration

Yak Ops 区分开发过程和正式发布历史：

```text
Development
  ↓
Draft Migrations
  ↓
Release Freeze
  ↓
0 or 1 Release Migration for the Product Version
  ↓
Publish
  ↓
Immutable
```

### Draft Migration

同一个尚未发布的 Product Version 开发期间允许存在多个 Draft Migration：

- Draft 可以按能力 / PR 拆分，便于并行开发、Review 与联调。
- Draft 文件沿用 `V{version}__{lower_snake_description}.sql`，一个 Draft 聚焦一个明确开发主题。
- Draft 只允许进入可以清库重建的开发 / E2E 环境。
- Draft 不代表最终用户升级历史，不要求长期保留它的 Flyway checksum。
- Draft 调整后统一重建对应开发数据库，不通过 `repair` 把旧 checksum 强行修成新 checksum。

### Release Migration

进入 Release Freeze 后，同一个尚未发布 Product Version 的 Draft Migration 应收口为**最多一个**正式 Release Migration：

```text
V1__baseline.sql
V2__v1_1_0.sql
V3__v1_2_0.sql
```

规则：

- Product Version 没有 Schema 变化时不创建空 Migration。
- 有 Schema 变化时，同一 Product Version 最多一个正式 Release Migration。
- 正式文件名使用 `V{flywayVersion}__v{major}_{minor}_{patch}.sql`。
- 一个 Release Migration 可以包含多个 Schema 主题，但必须使用清晰注释分段。
- Squash 应保留已验证 Draft SQL 的执行顺序和语义，不借 Release Freeze 顺手重写业务 Schema。
- Release Migration 的 Flyway Version 必须高于最后一个已发布 Migration。
- Release Migration 一旦发布或进入不可重建共享环境，永久冻结，不得 rename、删除、重排、修改或参与后续 squash。
- 正式 Release Gate 使用 `scripts/release/check-release-migration.sh` 机械拒绝未收口 Draft、同版本多个 Release Migration，以及高于目标 Product Version 的未来 Migration。

Product Version 与 Flyway Version 是两个不同概念；文件名中的 `v1_1_0` 只记录该 Flyway Migration 属于哪个 Product Release。

## Migration Contract

- 所有数据库结构变化统一由 Flyway 管理。
- 禁止为不同模块分配 V1000 / V2000 等版本段；所有能力共享一条连续 Flyway Version 序列。
- 禁止用 `IF EXISTS / IF NOT EXISTS` 掩盖异常 Schema。
- 删除表 / 字段、修改字段类型等破坏性变化必须明确评估数据迁移与回滚风险。
- Schema 演进只引入当前产品需要的结构和基础数据。
- Draft / Release 的修改权限严格遵守上一节状态模型，不以“本地能修”作为改写共享历史的理由。

## Table Contract

Must:
- 表名使用 lower_snake_case。
- Yak Ops 自有表使用 `yak_` 前缀，并按能力继续细分，例如 `yak_ops_`、`yak_security_`。
- 当前 Yak Ops 主键统一使用 `VARCHAR(64)`，Java 使用 `String`，由 Common 的统一雪花 ID 能力生成，禁止数据库自增。
- 主键字段统一命名为 `id`。
- 使用 InnoDB。
- 字符集统一 `utf8mb4`。
- 排序规则统一 `utf8mb4_unicode_ci`，兼容当前 MariaDB 10.6 运行环境。
- 每张表必须有中文 COMMENT。
- 每个业务字段必须有中文 COMMENT。
- 时间字段统一使用 `DATETIME(3)`。
- 常规审计时间字段统一命名为 `create_time / update_time`。
- 表结构必须与对应 Entity 的持久化字段保持一致。
- 只有真实查询需要的字段才创建索引。

主键：

```sql
id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成'
```

表配置：

```sql
ENGINE=InnoDB
DEFAULT CHARACTER SET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='业务表中文说明';
```

## Column Contract

- 字段名使用 lower_snake_case。
- `VARCHAR(n)` 必须来自真实业务上限，禁止无脑使用 255。
- 名称通常按 64 / 128 等真实上限选择。
- URL、JSON、密码哈希等字段根据真实数据长度选择 VARCHAR / TEXT / LONGTEXT。
- 必填字段使用 `NOT NULL`；只有真实可选字段允许 `NULL`。
- 禁止为了避免判空设置无意义默认值。
- 默认值必须表达真实业务默认状态。
- 状态 / 类型字段的 COMMENT 必须写清取值语义。
- 敏感字段 COMMENT 要明确其用途，但不得包含真实凭证示例。
- Java 枚举的存储形式必须与 SQL 类型匹配；现有字符串枚举不得在无迁移方案时静默改成数字。

## Relationship / Index Contract

- 禁止数据库物理外键。
- 表之间只保存关联 ID，关系完整性由 Business / Service 事务维护。
- 真实用于 `WHERE / ORDER BY` 的字段必须评估索引。
- 联合索引按真实查询设计：等值字段在前，范围 / 排序字段在后。
- 联合索引已覆盖的前缀字段不重复创建无意义索引。
- 唯一索引命名 `uk_<table_without_yak_prefix>_<field>`。
- 普通索引命名 `idx_<table_without_yak_prefix>_<field>`。
- 低区分度状态字段只有存在真实筛选需求时才单独建索引。

## Entity Sync

任何 Schema 变化都必须同时检查：
- 对应 Entity 字段。
- Entity 字段 Java 类型。
- Entity / 字段 JavaDoc。
- Mapper / Repository 查询。
- 枚举存储方式。

Flyway COMMENT 与 Entity 字段注释表达的业务语义必须一致。

## Boundary

```text
Flyway
  ↓ defines
Schema
  ↑ mirrors
Entity
  ↑ consumed by
Repository
  ↑
Business / Service
```

Flyway 定义数据库结构；Entity 镜像持久化结构；Repository 消费结构；Business / Service 拥有业务语义。
