# YOIP-9: Data Modeling — Dimension Modeling

Status: Accepted

Discussion: https://github.com/gitfortian/yak-ops/pull/9

Decision: [VOTE] Accepted — https://github.com/gitfortian/yak-ops/pull/9#issuecomment-6354864428

Implementation: In progress — #10–#14 (merged), #15 — Dimension Modeling

Release: N/A until released

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
5. 批量录入：粘贴 Doris/Paimon `CREATE TABLE` 文本，前端解析出字段（限标准字段定义子集，解析失败逐行降级为手填）。
6. 前端预留 `ReverseModelingProvider` 接口（只定义，不实现）。

### Non-goals

- 汇总表（DWS）、应用表（ADS）。二者依赖「度量 + 聚合方式」与数据集市归属，留到 P1；届时字段入口为「手动添加度量字段（SUM/COUNT/AVG/MAX/MIN）」+「从源表导入」，**不提供**「从指标导入」。
- 数据指标模块（原子/派生/复合指标）。仓库内不存在，不为一个下拉框引入整个指标中心。
- 物理发布与物化：本期只产出逻辑模型，不向引擎建表。发布按分层绑定解析库实例的链路留到后续 YOIP。
- 逆向建模实现（当前数据源只有 MySQL / Oracle / PostgreSQL 的 JDBC 接入，没有 MaxCompute / EMR Hive；Paimon 是湖格式，`DataSourceCatalogService` 的 JDBC Catalog 覆盖不到）。
- FML 代码模式（DataWorks 私有标准，不复刻）。
- 可配置的表名检查器（正则 / 命名词典）、模型版本、审批流、导入导出、模型移动。本期只做强制分层前缀，见 3 节。
- 概念维度跨域复用（一个维度同时挂在多个数据域 / 数据集市、或维度表跨域引用他人维度）。本期归属单一，见 Proposed Changes 第 2 节；多对多归属属于公共契约变更，P1 单独评审。

## Public Interfaces

### Product behavior

| 项目 | 契约 |
| --- | --- |
| 菜单 | 数据建模 → 维度建模，位于数仓规划、数据标准之后 |
| 路径 | `/data-modeling/dimension-modeling`，加入导航与登录回跳合法路径 |
| 布局 | 左侧约 250px 目录树（公共层 / 应用层两个分区），右侧模型列表或详情表单；窄屏上下排列 |
| 数仓规划 | 左树新增「数据集市」根级分区，与「业务分类」并列；集市下挂主题域 |
| 前置条件 | 只有 `enabled=1` 且已绑定数据源的分层可被选择（复用 YOIP 分层约束） |
| 列表检索 | 关键词（表名 / 中文名）、模型类型、数仓分层、负责人、数据域 / 数据集市；本期以列表筛选替代尚未建成的数据地图入口 |

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
│       ├── 维度
│       │   ├── 商品
│       │   │   └── dim_product（维度表）
│       │   └── 时间（暂无维度表）
│       └── 业务过程
│           └── 下单
│               └── dwd_trade_order_detail（明细表）
└── 应用层
    └── 运营集市 / 实时战报
        └── 维度
            └── 渠道
                └── dim_channel（维度表）
```

维度表**总是挂在概念维度下**，不单独平铺在数据域里，这样「先建维度、再建维度表」的顺序在树上直接可读；概念维度即使还没有表也显示为占位节点，避免用户以为维度没创建成功。在概念维度节点上直接「新建维度表」会预填并锁定归属关系；若用户从工具栏新建，表单顶部提示「维度表必须先关联概念维度」，`概念维度` 下拉为空时给出去创建的链接。

明细表挂在业务过程下，维度表不参与业务过程分支。

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
| GET | `/models` | 分页，按 `type`、`layerId`、`ownerId`、`domainId`、`datamartId`、`keyword` 过滤 |
| GET | `/models/{id}` | 详情含字段列表 |
| POST | `/models` | 建模型（字段随模型一次提交） |
| PUT | `/models/{id}` | 改基本信息与字段（整表替换 + revision） |
| DELETE | `/models/{id}` | 删模型 |
| GET | `/models/name-availability` | 完整表名（含分层前缀）在 Workspace 内可用性预检 |

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
| `yak_ops_modeling_model` | `UNIQUE(workspace_id, name)`；`type VARCHAR(16)`，取值由应用层枚举校验（本期 `DWD, DIM`）；`lifecycle_days BETWEEN 1 AND 36000`；`CHECK` 保证 DWD 必须有 `domain_id` 且无 `datamart_id` |
| `yak_ops_modeling_model_field` | `FK(model_id) → model ON DELETE CASCADE`；`UNIQUE(model_id, ordinal)`、`UNIQUE(model_id, en_code)`；`standard_id` 可空且 `ON DELETE RESTRICT` |

`yak_ops_modeling_code` 的 `node_kind` 取值从 `('CATEGORY','DOMAIN','PROCESS')` 扩到包含 `'DATASET','SUBJECT'`，继续保证编码跨 Workspace、跨层级全局唯一。

### Frontend internal contract

只定义、不实现，避免将来按引擎扩展时改调用方：

```ts
export interface ReverseModelingCandidate {
  datasourceId: string;
  schema?: string;
  table: string;
}

/** 与逻辑模型字段同构，不暴露引擎方言（类型映射由 provider 负责）。 */
export interface ModelField {
  enCode: string;
  cnName: string;
  dataType: "STRING" | "BIGINT" | "DECIMAL" | "DATE" | "TIMESTAMP";
  lengthPrecision?: string;
  isPrimaryKey: boolean;
  isNotNull: boolean;
  description?: string;
}

export interface ReverseModelingProvider {
  readonly engine: string;
  discover(input: {
    datasourceId: string;
    pattern: string;
    exact: boolean;
  }): Promise<ReverseModelingCandidate[]>;
  describe(input: {
    datasourceId: string;
    schema?: string;
    table: string;
  }): Promise<ModelField[]>;
}
```

`describe` 与 `discover` 成对定义：逆向建模拿到候选表之后必然要继续取字段元数据，否则调用方只能自己再拼一次「取列 + 类型映射」，将来接入 Paimon/Doris 时会同时改接口和调用方。`describe` 直接返回逻辑字段而非物理列，把 `STRUCT<>`、`ARRAY<>` 等方言差异挡在 provider 内部。

本期没有任何 `ReverseModelingProvider` 实现，左侧菜单不出现逆向建模入口。

## Proposed Changes

### 1. 数据集市与主题域

不塞进「业务分类」树：公共层与分析层是两个视角，混在一棵树里会让「数据域」和「数据集市」的父子关系变得不可解释。因此规划左树改为两个并列分区：业务分类树、数据集市树。

- 数据集市：中文名、英文名、英文缩写（全局唯一）、负责人、描述。
- 主题域：中文名、英文名、英文缩写、描述；挂在集市下，一个集市多个主题域。
- 删除保护：集市下有主题域，或已被应用层模型引用 → 409；主题域被应用层模型引用 → 409。两类检查在本期（P0）就会真实触发——维度表允许挂在应用层，`model.datamart_id` 已经是引用源，不能等到 ADS 落地才补。
- 编码：复用 `PlanningService#available` 的全局不占用语义（检查不加锁，创建时在事务内锁并写 `yak_ops_modeling_code`）。

### 2. 概念维度

维度是分析视角，不是物理表。归属二选一：

- 公共层：必须选一个数据域（`domain_id`），并记录业务分类。
- 应用层：必须选一个数据集市（`datamart_id`）。

编码唯一性限定在「所属数据域或集市」内，用生成列做唯一索引。被维度表引用后禁止删除。

**本期归属单一，不支持跨域共享**：一个维度只能属于一个数据域或一个数据集市，`owner_scope_id = IFNULL(domain_id, datamart_id)` 的生成列与 `(workspace_id, owner_scope_id, code)` 唯一索引都建立在这个前提上。同一缩写在不同域下允许并存（唯一性按域计算），因此两个域都需要「商品」时是各建一份、各自维护字段，而不是共享一条记录。

不做的原因不是省事：多对多需要额外的 `dimension_scope` 关联表，`code` 的唯一性语义会从「域内唯一」变成「Workspace 内唯一 + 共享」两种并存模式，而「被引用即禁删」的检查也要改为跨多个归属方校验。这属于公共契约变更，应按流程单独出 YOIP，不在本期夹带。

### 3. 明细表（DWD）与维度表（DIM）

| 字段 | DWD | DIM |
| --- | --- | --- |
| 数仓分层 | 必填（启用且已绑定数据源） | 必填（同上） |
| 业务分类 | 必填 | 必填 |
| 业务过程 | 必填 | — |
| 概念维度 | — | 公共层必填 / 应用层可选 |
| 数据域 / 数据集市 | 必须 `domain_id` | 二选一 |
| 存储策略 | 必填（`DAILY_INCREMENTAL` / `DAILY_FULL`） | 同 |
| 表名 | 分层前缀固定 + 用户后缀（见下） | 同 |
| 表中文名 | 必填 | 必填 |
| 生命周期 | 1–36000 天 | 同 |
| 负责人 | 默认当前用户，可选 Workspace 成员 | 同 |
| 描述 | 可选 | 可选 |

**表名前缀**：表名 = `lower(layer.en_code) + "_"` 前缀（只读、不可编辑）+ 用户填写的后缀。前缀取自所选分层而不是硬编码 `dwd_` / `dim_`，因为分层本身可自定义（如用户建了 `WIDE` 层就应落 `wide_` 前缀）。后缀校验 `^[a-z][a-z0-9_]{0,40}$`，全名 ≤ 64 字符。切换分层时重算前缀并清空已存在的重名报错。这是「表名检查器」的零配置子集：可配置正则与命名词典仍不做，规范前缀本期即可落地。

**模型 `type` 用 `VARCHAR(16)` + 应用层枚举，不加 `CHECK`**。`DWS/ADS` 是已排期的扩张，若用 `CHECK`，P1 必须 `DROP CHECK / ADD CONSTRAINT` 才能放开；私有化部署的库升级路径越长，这类只为一枚举值动的 DDL 越不划算。写路径由 `ModelType` 枚举校验，读路径对未知值以「未知类型」展示并禁止编辑（防止手工 SQL 写入的脏行被误改）。语义稳定的约束继续留在库里：`scope`、`storage_policy`、`lifecycle_days` 的 `CHECK` 不动。

### 4. 字段体系

- 手填：英文编码、中文名、逻辑类型（`STRING/BIGINT/DECIMAL/DATE/TIMESTAMP`，与标准一致）、长度或精度、主键、非空、描述。
- 关联数据标准：选中后**强制继承** `en_code`、`data_type`、`length_precision` 三项并在前端置灰，必须「解除绑定」才能改；后端保存时按 `standard_id` 重新覆盖这三项，不信任前端传入值。
- `is_not_null` **不强制继承**：选择标准时只作为预填默认值，用户可独立调整，后端不覆盖。理由：同一标准会被 ODS 同步表、DWD 明细、DIM 维度分别引用，可空性取决于本层数据的真实约束；若按标准强制收紧，源表可空的字段会在 DWD 层凭空变成非空，直接导致装载失败。主键同理，永远由用户显式勾选。
- 分区：字段级 `is_partition` 标记，单模型最多一个；分区字段类型必须是 `STRING`（业务日期 `ds`）。
- 字段顺序用 `ordinal` 显式保存，整表替换 + `revision` 乐观锁，避免逐条编辑产生半成品状态。

### 5. 从表/视图导入

前端三级选择：数据源 → 库/模式 → 表（支持模糊搜索）→ 字段勾选。

字段类型映射遵循「只自动映射无损或语义明确的类型，其余显式标红」的原则。逻辑类型只有五个（`DataType`：`STRING` / `BIGINT` / `DECIMAL` / `DATE` / `TIMESTAMP`，与数据标准同一套），因此任何物理类型都要么落到这五个之一，要么被判为待处理：

| 物理类型 | 逻辑类型 | 说明 |
| --- | --- | --- |
| `VARCHAR` / `CHAR` / `TEXT` / `STRING` / `CLOB` | `STRING` | 长度写入 `length_precision` |
| `TINYINT` / `SMALLINT` / `INT` / `INTEGER` / `BIGINT` | `BIGINT` | 整数族统一收敛 |
| `DECIMAL` / `NUMERIC` | `DECIMAL` | 保留 `(p,s)` |
| `DATE` | `DATE` | |
| `DATETIME` / `TIMESTAMP` | `TIMESTAMP` | |
| `JSON` / `JSONB` | `STRING` | 文本保真落库，不解析 JSON 语义，行内提示 |
| `BOOLEAN` / `BOOL` | **不自动映射** | 五个逻辑类型里没有布尔，默认建议 `STRING` 存 `true`/`false`，必须由用户确认 |
| `FLOAT` / `DOUBLE` / `REAL` | **不自动映射** | 收敛到 `DECIMAL` 需要用户指定精度，不替用户猜 |
| `BLOB` / `BINARY` / `VARBINARY` / `TINYBLOB` / `BYTEA` | **不支持** | 无二进制逻辑类型，必须移除该字段或改为受支持类型 |
| `TIME` / `YEAR` / `INTERVAL` / `ENUM` / `SET` / `GEOMETRY` / `ARRAY<...>` / `STRUCT<...>` / `MAP<...>` | **不支持** | 同上 |

未映射与不支持的行**保留在字段列表**里、标红、`dataType` 留空并给出原因，`length_precision` 不猜。导入动作本身不阻断（与批量录入一致），但**保存模型前必须为每一行选定类型或删除该行**，否则返回 400。

这里刻意不做静默降级：把 `BLOB` 塞成 `STRING`、把 `DOUBLE` 塞成 `DECIMAL(10,2)` 都能让导入「成功」，但会在物理发布和装载阶段变成真正的事故，而且用户在导入页看不出来。

导入时的可空性与主键不做推断式收紧：`is_not_null` 按源表可空性预填，物理主键自动勾选为主键，二者都允许用户修改（与字段标准继承的取舍一致）。

**数据源范围**：只有**当前 Workspace** 内的数据源可选。`DataSourceCatalogService` 的入参是 `datasourceId`，因此必须由服务端按 Workspace 重新校验归属，跨 Workspace 的 id 返回 403 而不是 404/500，避免越权探测。

**环境不作过滤条件，只作提示**。「只允许生产环境数据源」在私有化部署下会直接废掉导入功能：`yak_ops_data_source.environment` 是 `NOT NULL DEFAULT 0`，即所有未显式改过的数据源都是「开发」；单实例部署的团队根本没有生产/预览之分。改为：

- 下拉列表按 `PROD > TEST > DEVELOP` 排序，每项显示环境徽标；
- 选中非 `PROD` 时，插入前弹一次确认「即将从『开发』环境读取字段，请确认连接的是目标库」，用户确认即继续；
- 不在服务端拦截，也不隐藏非生产数据源。

### 6. 批量录入

字段编辑区提供「批量录入」：粘贴 Doris/Paimon 的 `CREATE TABLE` 文本，解析出字段后追加到列表。该能力完全在前端，不新增后端解析接口。

**明确只兼容标准字段定义子集**，不做通用 SQL 解析：

| 支持 | 忽略 / 不支持 |
| --- | --- |
| `CREATE TABLE [IF NOT EXISTS] name (...) 的字段定义区` | `ENGINE=`、`PARTITION BY`、`DISTRIBUTED BY`、`PROPERTIES`、表级 `COMMENT` |
| 字段名（反引号 / 双引号 / 裸名）+ 类型 + `COMMENT '...'` | `INDEX` / `KEY` / `UNIQUE KEY` / `FOREIGN KEY` |
| `NOT NULL`、行内 `PRIMARY KEY`、表级 `PRIMARY KEY (a, b)` | 生成列、默认值表达式、`CHECK` 约束 |
| 嵌套类型 `DECIMAL(10,2)`、`STRUCT<a:int>`、`ARRAY<...>` | FML 语法、多语句脚本 |

实现要点：**不用 `split(',')` 切分字段**，改用一次「括号深度 + 引号状态」感知的扫描——`DECIMAL(10,2)`、`STRUCT<a:int, b:string>`、`COMMENT '含,逗号'` 都会把朴素正则在第一个逗号处切错。扫描器只在深度 0 且不在引号内时按逗号断行，随后逐行用正则提取字段名、类型与注释。

**解析失败不阻断整批**：成功行正常追加；失败行保留原文并在下方列出「第 N 行：无法识别的原因」，用户可手工补该行或忽略。整段无法定位字段定义区（如只有 `CREATE TABLE` 头）时给出整段提示而非逐行刷屏。

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

- **迁移**：并入 `V2__v1_4_0.sql`（追加在既有表之后），只做前向变更（建 5 张表 + 扩 `yak_ops_modeling_code` 的 `CHECK`），不改动 V1，不新增 `V3`。1.4.0 尚未发布，且分发版本需要贴近上游，因此不为本功能单独起一个 minor 版本；实施顺序上等 #8 合并后再追加，避免两个 PR 同时改同一文件。
- **兼容性**：`tree` 响应新增字段，`categories` 结构与既有语义不变；前端旧版本忽略新字段即可。`codes/availability` 的 `kind` 增加取值，既有调用不受影响。`model.type` 为 `VARCHAR` 且无 `CHECK`，P1 增加 `DWS/ADS` 时只改应用层枚举，不产生 DDL 变更，私有化部署无需为新类型跑迁移。
- **数据迁移**：无。不写业务行，不做回填，不使用 `flyway repair`。
- **降级**：数据集市、维度、模型均可在未被引用时删除；被引用时 409，不级联删除。

## Test Plan

- 单元测试（`DimensionModelingServiceTest`、`PlanningServiceTest` 扩展）：编码唯一性、维度归属互斥、DWD 必须挂业务过程、DIM 归属二选一、生命周期边界、分区字段唯一且为 `STRING`、revision 冲突。
- 维度归属（单测 + 真库）：同一缩写可在两个数据域并存；同一数据域内重复被拒；不存在跨域共享入口，多对多关联表不在本期迁移里（有测试断言库里只有 `domain_id` / `datamart_id` 两列）。
- 类型映射（单测 + 前端表驱动）：五种自动映射；`JSON` → `STRING` 并带提示；`BOOLEAN` / `FLOAT` 留空待确认；`BLOB` / `STRUCT<...>` 标记不支持；含未决行的模型保存返回 400，而导入动作本身不报错、也不丢弃这些行。
- 标准继承（单测）：绑定标准后 `en_code` / `data_type` / `length_precision` 被后端按 `standard_id` 覆盖（即使前端传了不同值）；`is_not_null` **保留**前端传值，不被覆盖；解绑后三项恢复可编辑。
- 表名前缀（单测 + 前端）：前缀恒为 `lower(layer.en_code) + "_"`，切换分层时重算；后缀非法字符被拒；全名超 64 字符被拒；两个不同分层下的同名后缀不冲突。
- 数据类型（单测）：`type` 未知值在列表展示为「未知类型」且不可编辑；写入非枚举值被拒（应用层校验，非 DB `CHECK`）。
- 真库集成测试（`DimensionModelingDatabaseIT`）：五张表仍在意的 `CHECK`（`scope`、`storage_policy`、`lifecycle_days`、DWD/DIM 归属互斥）与唯一索引、`ON DELETE RESTRICT` 阻断、级联删除字段、跨 Workspace 隔离、`code` 表新 `node_kind` 生效；确认 `type` 列**不**存在 `CHECK`（用 `information_schema.check_constraints` 断言）。
- e2e（`DimensionModelingIT`）：建集市 → 建主题域 → 建维度 → 建 DWD 表并导入字段 → 关联标准 → 列表可见；删除被模型引用的集市返回 409（P0 即覆盖，用应用层维度表构造引用）；跨 Workspace 的 `datasourceId` 调 catalog 返回 403。
- 前端：`tsc --noEmit`、oxlint、架构检查；批量录入解析用表驱动用例覆盖：`DECIMAL(10,2)` 逗号、`STRUCT<a:int, b:string>` 嵌套、`COMMENT '含,逗号'`、表级 `PRIMARY KEY`、`ENGINE=OLAP` 被忽略、畸形行只失败该行、整段无字段区时的整段提示。
- 发布脚本：`check-release-migration.sh 1.5.0`、`check-release-metadata.sh`。

## Rejected Alternatives

1. **把数据集市塞进业务分类树**：`CATEGORY → DOMAIN → DATASET` 或 `CATEGORY → DATASET` 会让「数据域」与「数据集市」的同级语义被父子关系掩盖，且已有数据域下的业务过程会与新节点争夺同一层级。选择并列根分区。
2. **本期一并做汇总表 / 应用表**：二者依赖度量与聚合方式，且规格原本的「从指标导入」没有指标模块支撑。先落地 DWD/DIM 主线，避免用一个不存在的指标中心冒充字段来源。
3. **为「从指标导入」先建指标模块**：成本远超本模块，且没有真实使用场景验证指标口径。降级为 P1 的「手动度量 + 聚合方式」。
4. **复用 `associatedModels` 返回空列表**：会让「没有模型」和「功能未开放」不可区分，掩盖真实状态。本期直接实现真实查询。
5. **物理发布一并实现**：发布涉及引擎方言、库实例解析与幂等，属于独立风险面，单独 YOIP 评审。
6. **`model.type` 用数据库 `CHECK` 枚举**：P1 扩 `DWS/ADS` 必须 `DROP CHECK / ADD CONSTRAINT`。改为 `VARCHAR` + 应用层枚举，代价是手工 SQL 可以写入未知 `type`，用读路径的「未知类型」兜底覆盖。
7. **「从表/视图导入」按 `PROD` 环境硬过滤**：`environment` 是 `NOT NULL DEFAULT 0`，硬过滤等价于关掉该功能，见 Proposed Changes 第 5 节。
8. **概念维度多对多归属（本期即支持跨域共享）**：需要 `dimension_scope` 关联表，且编码唯一性要同时支持「域内唯一」和「共享维度」两种语义，引用检查也要跨多个归属方。属于公共契约变更，按流程另开 YOIP，不夹带进本期。
9. **把 `BOOLEAN` / `BLOB` 等类型静默降级为 `STRING`**：能让导入「成功」，但把类型事故推迟到物理发布与装载阶段，用户在导入页看不出来。改为标红待确认、保存前强制解决。
