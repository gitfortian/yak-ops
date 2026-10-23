# YOIP-9: Data Modeling — Dimension Modeling

Status: Proposed

Discussion: Pending proposal PR

Decision: Pending

Implementation: N/A until accepted

Release: 1.5.0 (proposed)

Related FLIPs: N/A

## Motivation

「数据建模」目前只有两个能力：数仓规划（业务分类 → 数据域 → 业务过程，加数仓分层）和数据标准（标准目录 + 字段标准）。没有任何逻辑模型。`PlanningService#associatedModels` 对业务过程直接返回 `NOT_ENABLED`，前端 `process-assets.tsx` 只能渲染占位状态。

结果是链路断在最后一公里：用户规划好了业务过程、定好了字段标准，却没有地方记录「这个业务过程有哪些表、表里有哪些字段、字段对应哪条标准」。

本 YOIP 补齐这条链路，并解决三个必须先处理的结构性缺口：

| 规格要求 | 当前代码事实 |
| --- | --- |
| 应用层模型挂「数据集市 / 主题域」 | 规划只有 `NodeKind.CATEGORY / DOMAIN / PROCESS`，没有集市概念 |
| 维度表关联「概念维度」 | 无 Dimension 实体 |
| 明细表 / 维度表 + 字段体系 | 无逻辑模型、无字段实体、无标准引用 |
| 字段从表/视图导入 | 缺口已在别处补好：`DataSourceCatalogService` 已提供 `listDatabases / listSchemas / listTables / listColumns / getTableSchema` |
| 从指标导入 | 仓库无指标模块，见 Non-goals |

### Scope（本期 = P0 + 概念维度）

1. 数仓规划新增根级「数据集市」，其下「主题域」，与「业务分类」并列。
2. 概念维度（Dimension）：公共层挂数据域，应用层挂数据集市。
3. 逻辑模型：明细表（DWD）、维度表（DIM）。
4. 字段体系：手填、从表/视图导入、关联数据标准并继承、主键/非空/分区。
5. 批量录入：粘贴 Doris/Paimon `CREATE TABLE` 文本，前端解析出字段。
6. 前端预留 `ReverseModelingProvider` 接口（只定义，不实现）。

### Non-goals

- 汇总表（DWS）、应用表（ADS）。二者依赖「度量 + 聚合方式」与数据集市归属，留到 P1；届时字段入口为「手动添加度量字段（SUM/COUNT/AVG/MAX/MIN）」+「从源表导入」，**不提供**「从指标导入」。
- 数据指标模块（原子/派生/复合指标）。仓库内不存在，不为一个下拉框引入整个指标中心。
- 物理发布与物化：本期只产出逻辑模型，不向引擎建表。发布按分层绑定解析库实例的链路留到后续 YOIP。
- 逆向建模实现（当前数据源只有 MySQL / Oracle / PostgreSQL 的 JDBC 接入，没有 MaxCompute / EMR Hive；Paimon 是湖格式，`DataSourceCatalogService` 的 JDBC Catalog 覆盖不到）。
- FML 代码模式（DataWorks 私有标准，不复刻）。
- 表名检查器、模型版本、审批流、导入导出、模型移动。

## Public Interfaces

### Product behavior

| 项目 | 契约 |
| --- | --- |
| 菜单 | 数据建模 → 维度建模，位于数仓规划、数据标准之后 |
| 路径 | `/data-modeling/dimension-modeling`，加入导航与登录回跳合法路径 |
| 布局 | 左侧约 250px 目录树（公共层 / 应用层两个分区），右侧模型列表或详情表单；窄屏上下排列 |
| 数仓规划 | 左树新增「数据集市」根级分区，与「业务分类」并列；集市下挂主题域 |
| 前置条件 | 只有 `enabled=1` 且已绑定数据源的分层可被选择（复用 YOIP 分层约束） |

规划树形态：

```text
数仓规划
├── 数仓分层
├── 业务分类（公共层）
│   └── 电商业务
│       └── 交易域
│           ├── 下单
│           └── 支付
└── 数据集市（应用层）
    ├── 运营集市
    │   └── 实时战报（主题域）
    └── 财务集市
```

维度建模树形态：

```text
维度建模
├── 公共层
│   └── 电商业务 / 交易域
│       ├── 维度：商品、时间
│       └── 表：dwd_trade_order_detail、dim_product
└── 应用层
    └── 运营集市 / 实时战报
        └── 表：dim_channel
```

### REST

数仓规划边界（复用现有授权、编码注册、修订号语义）：

| 方法 | 路径 | 语义 |
| --- | --- | --- |
| GET | `/api/v1/data-modeling/warehouse-planning/tree` | 扩展返回 `datamarts` 顶层集合，`categories` 结构不变 |
| POST | `/api/v1/data-modeling/warehouse-planning/datamarts` | 建数据集市 |
| PUT | `/api/v1/data-modeling/warehouse-planning/datamarts/{id}` | 改数据集市（含 revision 校验） |
| DELETE | `/api/v1/data-modeling/warehouse-planning/datamarts/{id}` | 删除集市；有主题域或应用表引用时 409 |
| POST | `/api/v1/data-modeling/warehouse-planning/datamarts/{id}/subjects` | 建主题域 |
| PUT | `/api/v1/data-modeling/warehouse-planning/subjects/{id}` | 改主题域 |
| DELETE | `/api/v1/data-modeling/warehouse-planning/subjects/{id}` | 删除主题域；被应用表引用时 409 |
| GET | `/api/v1/data-modeling/warehouse-planning/codes/availability` | 扩 `kind` 取值：`DATASET`、`SUBJECT` |

维度建模边界（新 Controller，新错误码段）：

| 方法 | 路径 | 语义 |
| --- | --- | --- |
| GET | `/api/v1/data-modeling/dimension-modeling/dimensions` | 按 `scope`、`domainId`、`datamartId` 过滤 |
| POST | `/dimensions` | 建概念维度 |
| PUT | `/dimensions/{id}` | 改概念维度 |
| DELETE | `/dimensions/{id}` | 删概念维度；被维度表引用时 409 |
| GET | `/models` | 分页，按 `type`、`layerId`、`ownerId`、`keyword` 过滤 |
| GET | `/models/{id}` | 详情含字段列表 |
| POST | `/models` | 建模型（字段随模型一次提交） |
| PUT | `/models/{id}` | 改基本信息与字段（整表替换 + revision） |
| DELETE | `/models/{id}` | 删模型 |
| GET | `/models/name-availability` | 表名在 Workspace 内可用性预检 |

字段导入复用既有 Catalog 端点，不新增代理：

```text
GET  /api/v1/data-source/{id}/catalog/databases
GET  /api/v1/data-source/{id}/catalog/schemas?database=
POST /api/v1/data-source/{id}/catalog/tables      // 支持模糊搜索
POST /api/v1/data-source/{id}/catalog/columns     // 取字段与主键
```

### Persisted formats

新增五张表（名字沿用 `yak_ops_modeling_*` 前缀）：

| 表 | 关键约束 |
| --- | --- |
| `yak_ops_modeling_datamart` | `UNIQUE(workspace_id, code)`；`code` 为 `ascii_bin`，`CHECK '^[a-z][a-z0-9_]*$'`；含 `en_name`、`owner_id` |
| `yak_ops_modeling_subject` | `FK(workspace_id, datamart_id) → datamart ON DELETE RESTRICT`；`UNIQUE(workspace_id, datamart_id, code)` |
| `yak_ops_modeling_dimension` | `scope ∈ {PUBLIC, APPLICATION}`；`CHECK` 保证 PUBLIC 只填 `domain_id`、APPLICATION 只填 `datamart_id`；生成列 `owner_scope_id = IFNULL(domain_id, datamart_id)` 上建 `UNIQUE(workspace_id, owner_scope_id, code)` |
| `yak_ops_modeling_model` | `UNIQUE(workspace_id, name)`；`type ∈ {DWD, DIM}`；`lifecycle_days BETWEEN 1 AND 36000`；`CHECK` 保证 DWD 必须有 `domain_id` 且无 `datamart_id` |
| `yak_ops_modeling_model_field` | `FK(model_id) → model ON DELETE CASCADE`；`UNIQUE(model_id, ordinal)`、`UNIQUE(model_id, en_code)`；`standard_id` 可空且 `ON DELETE RESTRICT` |

`yak_ops_modeling_code` 的 `node_kind` 取值从 `('CATEGORY','DOMAIN','PROCESS')` 扩到包含 `'DATASET','SUBJECT'`，继续保证编码跨 Workspace、跨层级全局唯一。

### Frontend internal contract

只定义、不实现，避免将来按引擎扩展时改调用方：

```ts
export interface ReverseModelingCandidate {
  datasourceId: string;
  table: string;
  schema?: string;
}

export interface ReverseModelingProvider {
  readonly engine: string;
  discover(input: { datasourceId: string; pattern: string; exact: boolean }):
    Promise<ReverseModelingCandidate[]>;
}
```

本期没有任何 `ReverseModelingProvider` 实现，左侧菜单不出现逆向建模入口。

## Proposed Changes

### 1. 数据集市与主题域

不塞进「业务分类」树：公共层与分析层是两个视角，混在一棵树里会让「数据域」和「数据集市」的父子关系变得不可解释。因此规划左树改为两个并列分区：业务分类树、数据集市树。

- 数据集市：中文名、英文名、英文缩写（全局唯一）、负责人、描述。
- 主题域：中文名、英文名、英文缩写、描述；挂在集市下，一个集市多个主题域。
- 删除保护：集市下有主题域，或已被应用层模型引用 → 409；主题域被应用表引用 → 409。
- 编码：复用 `PlanningService#available` 的全局不占用语义（检查不加锁，创建时在事务内锁并写 `yak_ops_modeling_code`）。

### 2. 概念维度

维度是分析视角，不是物理表。归属二选一：

- 公共层：必须选一个数据域（`domain_id`），并记录业务分类。
- 应用层：必须选一个数据集市（`datamart_id`）。

编码唯一性限定在「所属数据域或集市」内，用生成列做唯一索引。被维度表引用后禁止删除。

### 3. 明细表（DWD）与维度表（DIM）

| 字段 | DWD | DIM |
| --- | --- | --- |
| 数仓分层 | 必填（启用且已绑定数据源） | 必填（同上） |
| 业务分类 | 必填 | 必填 |
| 业务过程 | 必填 | — |
| 概念维度 | — | 公共层必填 / 应用层可选 |
| 数据域 / 数据集市 | 必须 `domain_id` | 二选一 |
| 存储策略 | 必填（`DAILY_INCREMENTAL` / `DAILY_FULL`） | 同 |
| 表名 / 中文名 | 必填 | 必填 |
| 生命周期 | 1–36000 天 | 同 |
| 负责人 | 默认当前用户，可选 Workspace 成员 | 同 |
| 描述 | 可选 | 可选 |

模型 `type` 的 `CHECK` 本期只放 `DWD, DIM`，P1 增加 `DWS, ADS` 时再走迁移，避免留下没有调用方的枚举值。

### 4. 字段体系

- 手填：英文编码、中文名、逻辑类型（`STRING/BIGINT/DECIMAL/DATE/TIMESTAMP`，与标准一致）、长度或精度、主键、非空、描述。
- 关联数据标准：选中后继承 `en_code`、`data_type`、`length_precision`、`is_not_null` 四个属性并在前端置灰；必须「解除绑定」才能改。后端在保存时按 `standard_id` 重新覆盖这四个属性，不信任前端传入值。
- 分区：字段级 `is_partition` 标记，单模型最多一个；分区字段类型必须是 `STRING`（业务日期 `ds`）。
- 字段顺序用 `ordinal` 显式保存，整表替换 + `revision` 乐观锁，避免逐条编辑产生半成品状态。

### 5. 从表/视图导入

前端三级选择：数据源 → 库/模式 → 表（支持模糊搜索）→ 字段勾选。字段按标准逻辑类型映射：`VARCHAR/CHAR/TEXT → STRING`、整数族 `→ BIGINT`、`DECIMAL/NUMERIC → DECIMAL`、`DATE → DATE`、`DATETIME/TIMESTAMP → TIMESTAMP`。物理主键自动勾选为主键。

只允许从「生产环境」数据源导入（复用 `DataSourceEnvironment` 判定），预览环境不参与。

### 6. 批量录入

字段编辑区提供「批量录入」：粘贴 Doris/Paimon 的 `CREATE TABLE` 文本，前端正则解析字段名、类型、注释，映射为逻辑类型后追加到列表。解析失败逐行报错，不静默丢弃。该能力完全在前端，不新增后端解析接口。

### 7. 校验、错误码与并发

新增 `DimensionModelingErrorCode`（`195xx` 段），HTTP 映射沿用现有 `GlobalExceptionHandler` 风格：

| 场景 | HTTP |
| --- | --- |
| 编码/表名重复 | 409 |
| revision 冲突 | 409 |
| 被引用不可删 | 409 |
| 分层未启用或未绑定数据源 | 422 |
| 字段/模型校验失败 | 400 |

所有写操作按 Workspace 隔离，`revision` 冲突返回 409 而不是覆盖。

## Compatibility, Deprecation, and Migration Plan

- **迁移**：新增 `V3__v1_5_0.sql`，只做前向变更（建 5 张表 + 扩 `yak_ops_modeling_code` 的 `CHECK`），不改动 V1 / V2。选择 1.5.0 而不是继续往 `V2__v1_4_0.sql` 追加，原因有二：V2 已随数据标准进入 master，且当前 PR #8 正在改它，继续追加会让两个 PR 争抢同一文件；本功能体量也足以独立成版。若维护者希望 1.4.0 一并发布，替代方案是把 DDL 并入 V2 并把版本回落 1.4.0——但需等 #8 合并后再改，避免同文件冲突。
- **兼容性**：`tree` 响应新增字段，`categories` 结构与既有语义不变；前端旧版本忽略新字段即可。`codes/availability` 的 `kind` 增加取值，既有调用不受影响。
- **数据迁移**：无。不写业务行，不做回填，不使用 `flyway repair`。
- **降级**：数据集市、维度、模型均可在未被引用时删除；被引用时 409，不级联删除。

## Test Plan

- 单元测试（`DimensionModelingServiceTest`、`PlanningServiceTest` 扩展）：编码唯一性、维度归属互斥、DWD 必须挂业务过程、DIM 归属二选一、生命周期边界、分区字段唯一且为 `STRING`、标准继承覆盖前端传值、revision 冲突。
- 真库集成测试（`DimensionModelingDatabaseIT`）：五张表的 `CHECK` 与唯一索引、`ON DELETE RESTRICT` 阻断、级联删除字段、跨 Workspace 隔离、`code` 表新 `node_kind` 生效。
- e2e（`DimensionModelingIT`）：建集市 → 建主题域 → 建维度 → 建 DWD 表并导入字段 → 关联标准 → 列表可见；删除保护返回 409。
- 前端：`tsc --noEmit`、oxlint、架构检查；批量录入解析用表驱动用例覆盖 Doris/Paimon 常见类型与畸形输入。
- 发布脚本：`check-release-migration.sh 1.5.0`、`check-release-metadata.sh`。

## Rejected Alternatives

1. **把数据集市塞进业务分类树**：`CATEGORY → DOMAIN → DATASET` 或 `CATEGORY → DATASET` 会让「数据域」与「数据集市」的同级语义被父子关系掩盖，且已有数据域下的业务过程会与新节点争夺同一层级。选择并列根分区。
2. **本期一并做汇总表 / 应用表**：二者依赖度量与聚合方式，且规格原本的「从指标导入」没有指标模块支撑。先落地 DWD/DIM 主线，避免用一个不存在的指标中心冒充字段来源。
3. **为「从指标导入」先建指标模块**：成本远超本模块，且没有真实使用场景验证指标口径。降级为 P1 的「手动度量 + 聚合方式」。
4. **复用 `associatedModels` 返回空列表**：会让「没有模型」和「功能未开放」不可区分，掩盖真实状态。本期直接实现真实查询。
5. **物理发布一并实现**：发布涉及引擎方言、库实例解析与幂等，属于独立风险面，单独 YOIP 评审。
