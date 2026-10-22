# YOIP-5: Data Integration Synchronization Model + Product Interaction

Status: Accepted

Discussion: https://github.com/weifuwan/yak-ops/pull/1636

Decision: [VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/1636#issuecomment-6160395888

Implementation: [Unified product entry and versioned solutions — PR #1637](https://github.com/weifuwan/yak-ops/pull/1637); [Explicit multi-table batch — PR #1639](https://github.com/weifuwan/yak-ops/pull/1639); [Database realtime and stage observation — PR #1641](https://github.com/weifuwan/yak-ops/pull/1641); [Initial schema preparation — PR #1643](https://github.com/weifuwan/yak-ops/pull/1643)

Release: N/A until released

Related FLIPs: N/A

## Motivation

Yak Ops Data Integration provides one "Data Sync" product entry. Users first choose source and target datasource types, then a supported synchronization solution and its steps. They subsequently configure actual connections, synchronization objects, target mappings, and execution settings. The list, editor, and execution details share one solution model.

**Offline and realtime are properties of synchronization solutions, not separate list, creation, editing, or detail pages.** A unified page does not erase the distinction between bounded batches and continuous execution, nor does it require identical forms for every scenario.

### Design References

DataWorks' [database-wide realtime configuration][dw-realtime] and [database-wide offline configuration][dw-offline] both begin from a synchronization task. They select a solution through source type, target type, specific synchronization type, and synchronization steps before configuring actual datasources. Its [supported solutions documentation][dw-solutions] distinguishes single-table and database-wide scenarios. Its [offline incremental configuration][dw-incremental] expresses batch ranges through filters and scheduling parameters. These references, together with screenshots supplied by the maintainer showing the creation dialog, batch table selection, and target table mappings, informed this proposal.

This proposal adopts that interaction structure; it does not claim to reproduce every DataWorks capability, API, or internal architecture. The fields, type identifiers, and delivery boundaries here belong to Yak Ops. Purchasing resource groups, CU quotas, Data Agents, and periodic Base/Log merges are out of scope.

### Existing Code and Issues to Consolidate

The following observations refer to `main` at `1da2d2555f4411e72550606b4fca45f5e2cdb788`. They are neither a complete capability inventory nor runtime test results.

| Code location | Observed behavior | Product impact |
| --- | --- | --- |
| [AppRouter][code-router] and `app/offline-sync`, `app/realtime-sync` | Register separate list, creation, edit, and detail routes; editing and details already share components | Page identity remains path-dependent and both lists maintain duplicate logic |
| [definition.ts][code-definition] | Task parameters contain single-table `source / sink`; `JDBC / MYSQL_CDC` implies offline or realtime | Specific solution identity and chosen steps are not represented independently |
| [MySqlCdcSourceFactory][code-cdc] | The current product path always uses a Hybrid Source | Existing realtime behavior means a full load followed by continuous incremental updates; arbitrary step combinations cannot be enabled by adding checkboxes |
| [JdbcSinkFactory][code-sink] | Existing write modes are `APPEND / UPSERT`, and changelog streams require `UPSERT` | Overwrite and schema migration must not appear as delivered options |
| [ExecutionConfigurationFactory][code-execution] | New Instances use isolated state directories and initially set `RESTORE_LATEST=false` | Submitting again after stopping does not resume the previous Instance's checkpoint |

Task identity, versions, submission, and execution follow [YOIP-2](YOIP-2-task-execution-lifecycle.md). Connector creation and datasource access follow [YOIP-3](YOIP-3-connector-creation-and-datasource-lookup.md). Scheduled submissions follow [YOIP-4](YOIP-4-schedule-lifecycle-quartz.md). Physical completion and checkpoint semantics follow [YOIP-1](YOIP-1-checkpoint-and-end-of-input.md). This proposal does not redefine those boundaries.

## Public Interfaces

The following are proposed public product contracts. The original proposal PR introduced only this document, not an executable API, parameter version, database migration, or runtime capability.

### Pages and Routes

Data Integration navigation retains "Datasources" and "Data Sync". Data Sync uses these unified routes:

| Route | Purpose |
| --- | --- |
| `/data-sync` | One list for all synchronization tasks |
| `/data-sync/new` | Configure a task from a creation draft |
| `/data-sync/:id/edit` | Edit a task from its saved solution |
| `/data-sync/:id/detail` | Task information and execution details, with `executionId` selecting a specific Instance |

Offline and realtime filters use one list and server-side pagination. The new page must not simply wrap the old pages in two tabs or fetch two independently paginated datasets and merge them in the browser. Operations Center retains its statistics role; update only necessary detail links, without moving its statistics or redesigning navigation.

### Query Available Synchronization Solutions

Proposed authenticated, Workspace-scoped read-only endpoint:

`GET /api/v1/data-sync/solutions?sourceType={datasourceType}&targetType={datasourceType}`

Here `sourceType / targetType` are product datasource types, not Connector identifiers such as `JDBC / MYSQL_CDC`. Retain existing datasource type IDs, including PostgreSQL's existing `POSTGRE_SQL`; this proposal does not rename them. Both parameters are required.

Return only enabled solutions for the directed source/target type pair. Each solution has a stable `solutionType`, display name, applicable step descriptions, explicitly allowed step combinations, and one default combination. Each allowed combination declares its applicable execution settings, including whether scheduled submission is supported. Required, optional, and disabled checkbox behavior derives from those combinations; the three checkboxes are not independent Boolean values.

An unknown type produces a parameter error. A recognized pair with no available solutions returns an empty collection. A failed query must not be interpreted as an empty collection or trigger an automatic offline fallback. Without an available solution, the creation flow blocks progression and shows a concise reason.

The type-level query neither loads actual connections nor accesses databases or credentials. After selecting concrete connections, saving, publishing, and execution admission must still validate Workspace permissions, actual datasource types, table schemas, steps, and write rules. Query results do not authorize later execution; the backend must reject a stale page submitting a no-longer-supported combination.

### Versioned Synchronization Parameters

Retain the generic Task Definition, Version, and Instance APIs with `taskType=DATA_SYNC`. New product parameters use the next format version, `parameterSchemaVersion=3`; this is the parameter *schema* version, not the incrementing Definition version.

| Parameter | Authoritative meaning and constraints |
| --- | --- |
| `solutionType` | User-selected specific solution identity; never inferred from the page URL or Connector name |
| `steps` | User-selected, server-validated steps with unique identifiers; persisted in schema, full, incremental display order |
| `source / sink` | Selected Workspace datasource IDs; the backend resolves and validates types, and task parameters never contain secrets |
| `tableSelection` | Selection intent; the first unified release accepts only `EXPLICIT`, not an unreleased rule mode |
| `tableMappings` | Explicit source objects, target objects, and per-mapping write modes; a single-table task is a collection of length one |
| Synchronization latency, scope summary, data strategy | Read-only projections of solution and steps, not a second set of conflicting writable options |
| Schedule, Instance, execution stages, offsets | Not editable static synchronization parameters; each follows its own lifecycle |

The proposed version 3 example below represents single-table realtime full-plus-incremental synchronization into an existing target table. It cannot be submitted directly to the current version 2 API.

```json
{
  "solutionType": "SINGLE_TABLE_REALTIME",
  "steps": ["FULL", "INCREMENTAL"],
  "source": { "datasourceId": "mysql-source" },
  "sink": { "datasourceId": "postgres-target" },
  "tableSelection": { "mode": "EXPLICIT" },
  "tableMappings": [
    {
      "source": { "database": "app", "table": "orders" },
      "target": { "database": "ods", "schema": "public", "table": "ods_orders" },
      "writeMode": "UPSERT"
    }
  ]
}
```

In `EXPLICIT` mode, the source objects in `tableMappings` are the persisted explicit selection. Do not persist a second, redundant source-table list; incomplete mappings exist only in the page draft. Source and target identifiers use structured database/schema/table values, rather than splitting dotted strings. Each mapping must belong to namespaces allowed by its selected connection.

For version 3, resolve Connector selection from the solution, selected datasources, and registered Factories; do not accept potentially contradictory `source.type` and `solutionType` fields. If user-configurable Connector parameters are added later, define and validate them in the configuration that owns their semantics, rather than introducing an unbounded arbitrary property bag. Preserve the existing empty-array meaning of `transform`; transformation is not enabled by this proposal.

### Execution Summaries and Stage Observation

Lists and details use the same Instance observation contract for `instanceId`, pinned Definition version, Instance status, and available stages. Each stage observation distinguishes at least the step identifier, stage status, nullable percentage, measurement basis, and sample time. Stage statuses include pending, running, completed, failed, canceled, and unknown.

A percentage belongs to a particular stage of a particular Instance, not to the Definition. Unknown progress is null. Do not store a display-only failure fallback of 0% in the actual measurement field. Historical Instances without stage data remain viewable; do not fabricate schema migration or full initialization steps for them.

The Data Sync business projection supplies these observations. Do not push table configuration into the generic Task lifecycle or add UI colors, labels, or Workspace dependencies to Core/Runtime. Implementations reuse existing Instance queries and batch summary endpoints instead of creating a second execution history API.

## Proposed Changes

### 1. 四个维度与实际控件的关系

| 维度 | 概念 | 回答的问题 | 页面表达 |
| --- | --- | --- | --- |
| 同步时效 | 离线、实时 | 按有限批次同步，还是持续同步变化？ | 包含在具体类型中 |
| 同步范围 | 单表、多表、整库、分库分表 | 同步哪些对象，这些对象如何组织？ | 具体类型提供入口，选表方式和映射表达对象范围 |
| 数据策略 | 全量、增量、全量加增量 | 同步存量、变化，还是两者衔接？ | 由全量和增量步骤组合表达 |
| 写入方式 | Append、Overwrite、Upsert 等 | 数据如何影响目标已有内容？ | 靠近目标表映射配置，按已支持能力提供 |

四个认知维度不是四个彼此独立的下拉框。选择“整库实时”后，不重复填写“同步时效：实时”；勾选全量和增量后，不再填写另一项“数据策略：全量加增量”。

同步时效不等于 Cron 周期；数据策略不等于启动位点；全量不等于覆盖写入；增量不等于 Upsert；结构迁移不属于第四种数据策略。

### 2. 具体类型与范围

采用以下稳定的方案身份，不将单表和实时作为同层级互斥选项：

| `solutionType` | 显示名称 | 范围语义 |
| --- | --- | --- |
| `SINGLE_TABLE_OFFLINE` | 单表离线 | 一条表映射，按有限批次执行 |
| `SINGLE_TABLE_REALTIME` | 单表实时 | 一条表映射，提供已实现的实时数据策略 |
| `DATABASE_OFFLINE` | 整库离线 | 一个来源连接下的批量表选择与目标映射 |
| `DATABASE_REALTIME` | 整库实时 | 一个来源连接下的批量表选择与持续同步方案 |

这些名称定义目标产品词汇，不表示所有类型在首次交付时均可选。多表由整库方案的明确选表集合承载，不新增一套 `MULTI_TABLE` 页面或插件。整库方案只匹配到一张表时仍保持方案身份，不自动改成单表。

分库分表是选表与多对一映射能力，不仅是表数量变化。开放前必须确定来源分片身份、目标主键冲突、Schema 一致性、事件顺序和删除语义。当前普通批量映射禁止多个来源写入同一目标表；未经该能力验收，不能通过把目标表名改成相同值绕过限制。

首次统一范围是一个来源连接到一个去向连接。跨数据源实例的分片聚合、运行中动态加表和热修改执行计划不随本提案自动开放；相关持久状态及准入变化需要补充设计评审。

### 3. 创建入口：先类型、后连接

统一弹窗标题为“新建同步任务”，字段顺序固定为：

```text
数据来源类型   [请选择]
数据去向类型   [请选择]
具体类型       [根据来源与去向加载]
同步步骤       [结构迁移] [全量同步] [增量同步]
名称           [自动建议，可修改]

                         取消   下一步
```

步骤仅展示该方案适用且已经实现的项。必选项勾选且不可取消；可选项的切换只能落在允许组合中。至少选择全量或增量之一，首次设计不提供仅结构迁移任务。当前 Hybrid 实时路径只支持全量加增量时，两者均为必选，不提供无效的取消操作。

来源与去向均由类型目录选择，实际连接在编辑器中选择。没有已保存连接不阻止查看类型级方案；进入编辑器后提供“新增数据源”并保留草稿。不给用户重复填写已经能够从具体连接确定的类型。

联动规则：

- 类型组合变化时取消旧请求或丢弃过期响应；加载期间禁用具体类型、步骤和下一步。旧结果不能覆盖新选择。
- 改变类型组合后重新验证具体类型和步骤；仍合法的选择可以保留，否则清除失效选择并要求确认，不静默降级成其他数据策略。
- 更换具体类型后使用该方案的合法默认步骤组合；若这会丢弃已经填写的对象、映射或阶段设置，必须先确认，再清除受影响字段。
- 自动名称只在用户未手动修改时随选择更新。用户自定义名称不被数据源或步骤联动覆盖。

“下一步”只创建当前页面会话的非敏感草稿，不写入 Task Definition，不上线、不启用调度、不启动执行。草稿按用户、Workspace 和标签页隔离；刷新时恢复有效草稿，无法恢复则重新打开方案入口。退出登录或切换 Workspace 不泄露上一作用域的草稿和异步结果。

首次“保存”仍要求形成有效任务配置，并创建已下线的 Definition；不为未选连接或未完成映射的草稿引入另一种持久化任务生命周期。

### 4. 统一编辑器：按能力展开配置

统一编辑器由任务方案驱动。新建读取草稿，编辑读取已保存配置，不能根据旧 URL 或导航筛选重新解释任务类型。共同区域为基本信息、数据源、数据来源、目标表映射、运行配置。

| 区域 | 用户操作 | 必须保持的行为 |
| --- | --- | --- |
| 基本信息 | 名称、备注、Debug，查看所选方案与步骤 | 不重复配置已确定的认知维度 |
| 数据源 | 选择实际来源与去向连接，测试连接 | 只提供类型匹配且有权限的连接；连接测试成功不等于 CDC 或目标写入条件均满足 |
| 数据来源 | 单表选择或批量选表 | 单表保持轻量；批量模式提供来源对象、已选对象及搜索过滤 |
| 目标表映射 | 目标名称、结构准备、写入方式和适用的映射配置 | 保存最终有效映射，明确区分预览结果与执行结果 |
| 运行配置 | 必要的增量范围、启动设置和周期调度 | 按实际步骤能力展开；普通用户不直接配置引擎内部参数 |

编辑器可组合单表选择、批量选择、映射和调度组件，但不建立通用动态表单平台。条件分支表达具体能力，不以一个 `realtime` 布尔值决定整页布局、路径、写入行为和进度。

#### 明确选表与规则选表

`EXPLICIT` 保存明确对象集合；搜索或正则过滤后手动勾选，仍然是明确选表。`RULES` 才表示持久化规则及明确的对象发现时机；它不能由一个前端过滤框冒充。

首次交付只接受 `EXPLICIT`。未来开放规则选表时，必须同时定义 include/exclude、命名空间、零匹配行为、预览与提交间的对象变化、发现时机及历史解析结果。周期批次应固定该次执行的对象集合，不能在执行中因重新查询元数据而悄悄增加表。持续任务的动态加表还需要新表初始化和增量衔接契约。

指定选表与规则选表互斥。切换时若存在已选对象或映射，确认后清除不适用结果，不能暗中合并两种模式。表数量和批量上限由 Yak Ops 实际能力决定，不直接照搬截图中的数值。

#### 目标映射与结构准备

批量映射复用同一数据模型，支持已实现的批量命名和批量编辑。每条映射展示来源、去向、写入方式及有意义的校验结果；目标冲突、缺失权限或不兼容 Schema 不能只放在全局 Toast 中。

未选择 `SCHEMA` 时使用已有目标对象，仍进行必要结构校验。选择 `SCHEMA` 表示真实准备目标对象，不把“校验通过”称为“结构迁移完成”。结构预览不执行 DDL；真实创建必须在任务准入、权限和目标占用检查之后。已有不兼容目标不得被隐式删除、清空或改造。

初始建表与持续 DDL 传播是不同能力。自动建表、字段映射、分片合并只有在端到端实现和校验完成后开放，单独勾选结构迁移不承诺传播未来所有 DDL 变更。

### 5. 步骤组合与执行语义

步骤标识为 `SCHEMA`、`FULL`、`INCREMENTAL`，显示名称为结构迁移、全量同步、增量同步。下表定义产品目标行为；是否可选由已验证的类型链路与合法组合决定。

| 方案行为 | 数据步骤 | 运行语义 |
| --- | --- | --- |
| 离线全量 | `FULL` | 有限批次；可以手动运行，支持周期提交时每次仍执行全量 |
| 离线增量 | `INCREMENTAL` | 按固定批次范围读取变化数据；批次完成后结束 |
| 离线全量加增量 | `FULL, INCREMENTAL` | 首次完成全量初始化，后续周期实例执行增量，不要求用户手动建立两个任务 |
| 实时纯增量 | `INCREMENTAL` | 从被明确支持且有效的起点持续消费变化 |
| 实时全量加增量 | `FULL, INCREMENTAL` | 完成初始化并正确衔接持续增量；全量阶段完成不结束整个实例 |

步骤数组不是客户端编排的 Job DAG。结构、全量、增量是产品观察和配置单位，不要求每个步骤创建独立 Task、独立 Instance 或独立 Job。全量与 CDC 的衔接由已有引擎和 Connector 负责；不能在全量结束后才随意取得日志起点，造成衔接窗口丢失。

如果以后在实时方案中开放仅全量组合，它必须对应实际可结束的物理计划和准确的调度能力。不能仅凭名称包含“实时”就强制无限运行，也不能允许取消增量后仍启动无限 CDC。该有限计划分支未实现前，不开放此组合。

#### 离线增量与周期初始化

[DataWorks 的离线增量示例][dw-incremental]以条件和调度参数形成数据范围。Yak Ops 区分按业务窗口读取与按持久游标续读；两者不能混用为一个不透明的“增量位点”。

开放窗口增量前，必须定义并固定每次 Instance 的时间或键范围、时区及上下界。失败重跑重用原范围；手动补跑历史窗口不自动推进正常周期状态。开放游标增量前，必须定义同值排序、并发更新和游标提交时机。读取结束或源端 Split 完成都不足以推进尚未成功写入的增量状态。

离线全量加增量需要持久化的初始化完成证据和兼容版本关系。初始化失败后的下一次运行仍执行所需初始化；结构、目标或选表发生不兼容变更时，不能复用旧完成标志。Quartz 仍只决定何时提交，不持有表级水位、不编排初始化和增量步骤。

字段条件增量不能自动感知物理删除；删除同步只在相应捕获和写入语义均支持时承诺。上述状态与边界未实现之前，不展示可用的离线增量选项。

#### 写入规则

写入配置位于目标映射。已有 `APPEND / UPSERT` 语义与主键校验保持不变；当前变更流不允许静默降级为追加。Upsert 的名称不代替 DELETE 能力说明，目标重放必须按实际 Connector 行为处理变更。

全量读取与覆盖写入是独立选择。未来的覆盖写入必须明确覆盖对象、清理时机及失败后果；恢复、重试和增量批次不能重复清空目标。

当初始化处理与后续写入不同，例如“初始化覆盖，后续按主键更新”时，在目标写入配置中分开表达。批量默认值最终形成明确的表级有效配置，不持久化互相冲突的全局和表级值。首次交付不开放尚未实现的 Overwrite，也不把旧 Append 改成覆盖。

### 6. 统一任务列表、操作与详情

列表默认展示全部数据同步任务。筛选包括名称或表名、具体类型、来源类型、去向类型和上线状态；高级筛选按需展开，不复制四个维度形成四套重复控件。

| 列 | 内容 |
| --- | --- |
| 任务名称 | 名称和当前版本 |
| 同步链路 | 来源数据源到目标数据源，保留类型图标，不展开表名 |
| 同步方案 | 具体类型与数据步骤摘要，例如“单表实时 · 全量加增量” |
| 调度 | 适用任务的 Cron 与真实调度状态；不适用时为 `—` |
| 状态 | Definition 的已上线或已下线，不混入 Instance 结果 |
| 执行概况 | 明确绑定最近一次 Instance 的阶段与进度，可跳转到该实例 |
| 更新信息 | 修改人和时间 |
| 操作 | 现有任务与实例能力决定的操作 |

服务端完成全列表筛选、排序与分页，并按可见任务批量查询实例和调度摘要。活动实例用于决定可执行操作；它与最近一次实例不同时，不能将两个实例的状态或进度拼成一个执行概况。没有执行记录显示 `—`，不显示虚构的 0%。

保存、上线、立即执行、停止当前执行、启停调度保持独立含义。统一页面不取消已有的发布条件、取消许可和资源占用规则。停止当前执行不等于停止未来调度；停用调度不取消已准入实例。已上线配置的编辑限制及任务下线、删除与调度的既有联动继续遵守 YOIP-2/4。

任务和 Schedule 的保存不是一个伪装成原子事务的前端请求。任务保存成功而调度失败时，保留已保存任务身份和调度草稿，明确显示未确认保存的部分；重试不得重复创建任务或报告全部成功。

详情保留左侧执行记录，右侧基本信息、执行状态和运行日志两个 Tab，底部展示表级执行结果。日志保持文本，不改成表格。当前 Definition 信息与选中 Instance 的固定配置明确区分；运行步骤、表映射和日志均来自该实例，不因当前任务编辑而改变历史事实。

### 7. 阶段进度与可信展示

创建入口选择步骤，编辑器配置这些步骤，执行页只展示该实例真实包含的步骤。离线与实时使用同一个进度组件，根据阶段事实渲染，不根据路由或方案后缀推测。

| 场景 | 展示语义 |
| --- | --- |
| 等待进入某步骤 | 等待，不宣称已经处理过该步骤 |
| 有可靠总量的全量阶段 | 显示计量百分比，例如 37% |
| 全量完成并满足目标写入及衔接完成条件 | 该阶段显示绿色 100% |
| 持续增量正在运行 | 使用既定 `○ >>> ○` 动画，不显示永远不可能完成的总百分比 |
| 有观测进度的失败 | 停止动画，以红色保留最后观测值，不归零 |
| 没有进度数据的失败 | 可沿用红色 0% 的显示兜底，但 Tooltip/可访问文本注明进度未取得；API 值仍为空 |
| 已停止、已取消或待确认 | 对应的停止或不确定语义，不继续显示健康运行动画 |

实时任务全量阶段达到 100% 时，Instance 仍可处于 RUNNING 并进入增量阶段。有限任务只有在既有生命周期确认成功后才显示任务完成。结构检查、Source EOF、Split 完成、Sink 提交和检查点完成分别拥有自己的语义，不互相替代。

`completedSplits / plannedSplits` 是 Split 进度，不是已提交行数或剩余时间。应携带计量口径，必要时标明估算；单 Split 任务可以没有平滑的中间百分比。多表任务不能简单平均表百分比，也不能给结构、全量、增量各分三分之一伪造总进度。无法可靠计量时采用不确定进度。

采样失败或过期不得变成新鲜的 0%、100% 或运行状态。未知阶段不被伪装为等待；恢复后进度不得通过累计重复读取记录假装持续增长。已有历史单一百分比只有在确定其归属时才投影为阶段指标。

### 8. 配置修改与重新执行

已保存任务的来源类型、去向类型和具体类型保持稳定；重大方案变化通过复制新建表达，避免把原任务的历史与恢复状态改成另一条链路。“复制新建”只复制配置，不复制调度启用状态、Instance、位点或目标占用，新任务仍处于已下线状态。

同一具体类型内修改合法步骤、连接、表映射、写入或增量条件，应经过影响确认并形成新参数版本；正在运行的实例不热切换新配置。受影响的 Schema 校验、映射预览和恢复兼容性必须重新验证。无法证明兼容时不能默认继续旧状态。

新提交与恢复是不同操作。当前独立 Instance 的重新提交不承诺沿用前一次检查点；界面文案必须与实际恢复能力一致。跨 Instance、跨版本或跨进程恢复，以及重置位点、仅重跑失败表，不因统一页面自动获得授权。没有相应公开契约和验收时，不展示这些可执行操作。

### 9. 所有权、实现边界与能力开放

DATA_SYNC 业务拥有产品类型、步骤规则、用户配置和可用组合查询；已有 Option/Factory 校验负责 Connector 约束。前端从这一权威业务结果驱动控件，不复制数据库类型到方案的规则表。无需新增方案插件族、通用表单引擎或多层能力注册中心。

继续采用 Task Definition → DATA_SYNC Plugin → JobPlanner → PipelineBuilder → YakFlow 的链路。Task 平台拥有准入、版本和生命周期；实际调度资格取决于解析后的步骤和物理执行能力，不仅是产品名称。Core/Runtime 保持数据库与产品边界，Connector 不依赖业务 Controller/DAO。

多表开放前，必须批量验证全部目标资源并原子获得所需占用；失败不能部分启动后留下无主占用。多表局部失败不代表任务成功，也不声明跨表事务。恢复和重试范围以实际执行契约为准，不能仅凭表格行状态自行重跑某张表。

| 交付边界 | 可开放的内容 | 开放条件 |
| --- | --- | --- |
| 统一入口与页面的首次交付 | 已验证 JDBC 链路的单表离线 `FULL`；已验证 MySQL CDC 链路的单表实时 `FULL, INCREMENTAL` | 新旧版本可读可执行、统一分页与调度回归、真实端到端验收；不开放结构迁移和任意步骤取消 |
| 批量对象能力 | 整库类型中的明确多表选择与一对一目标映射 | 多表计划、Schema、资源占用、结果与失败边界全部接通 |
| 扩展同步策略 | 结构迁移、离线增量、全增量衔接、实时纯增量或有限全量 | 每个链路与步骤组合独立满足配置、执行、持久状态和验收条件 |
| 进一步能力 | 规则发现、动态加表、分片合并、跨实例恢复等 | 补充相关公开状态和安全契约评审，不从本提案推断已经支持 |

目标词汇完整，不等于前端一次展示所有未来能力。尚未实现的选项不常驻展示为“规划中”；已实现但当前对象不满足条件的能力，才提供明确且简短的阻断原因。

页面使用 Yak UI、共享 Token 和现有交互规范。帮助文字保持最少；仅在数据丢失风险、关键前置条件或阻断原因无法由控件表达时按需提示。保留键盘、焦点、响应式和减少动画支持。不复制截图中的推广横幅、资源购买或重复性帮助文案。

## Compatibility, Deprecation, and Migration Plan

### 现有任务和参数

版本 2 任务使用服务端确定性投影进入统一产品界面：

| 已存配置 | 产品投影 | 必须保留 |
| --- | --- | --- |
| 已验证的单表 JDBC Source 与 JDBC Sink | `SINGLE_TABLE_OFFLINE`，`FULL` | 原连接、表、写入方式、任务状态和调度配置 |
| 已验证的单表 Hybrid MySQL CDC 与 JDBC Sink | `SINGLE_TABLE_REALTIME`，`FULL, INCREMENTAL` | 初始化及持续消费的既有执行行为 |
| 未识别或与上述形态不符的配置 | 明确提示不可识别，阻止误执行 | 原始数据，不猜测方案或静默转换 |

首次新建及修改执行参数写入版本 3，并形成新的不可变 Definition Version；仅修改名称、备注和 Debug 保持现有版本规则。保持仅针对版本 2 的明确读取与执行路径，使现有已发布版本无需重新发布即可继续使用。停止新写入版本 2，不在两套参数格式间双写。

已经固定的历史版本不原地重写，Instance 继续读取自己的参数版本。兼容读取的删除需要后续明确的历史保留与迁移决策，不能仅因默认写入已经切换就删除。未来按方案筛选必须同时覆盖版本 2 的确定性投影和版本 3，不能先分页再在浏览器过滤。

界面和路由迁移不改变任务 ID、Workspace、审计字段、发布状态、实例、日志、调度启用状态或恢复目录，不触发 DDL、目标清理、自动启动或重新初始化。已应用 Flyway 脚本保持不变。若实现需要索引或新持久字段，使用前向迁移并单独验证升级，不把 SQL 混入本提案 PR。

### 页面与旧链接

迁移两边有效能力后，删除 `app/offline-sync`、`app/realtime-sync` 下独立页面和包装入口，由 `app/data-sync` 拥有统一产品页面。不是将离线页面改名后继续保留一套实时分支页面。

旧地址只在路由边界转换，不保留旧业务页面：

- 旧列表地址进入 `/data-sync`，可保留对应的离线或实时筛选含义，仍使用同一列表。
- 旧 `/:id` 编辑地址和 `/:taskId/detail` 转向统一编辑、详情地址，保留任务与 `executionId` 等受支持参数。
- 旧 `/instances/:id` 先通过授权的 Instance 查询解析所属 Definition，再定位统一详情；不可访问时按现有权限或不存在语义处理。
- 旧 `/new` 地址进入统一创建流程，可预选合法的时效偏好，但不能绕过类型组合、步骤和连接校验。

同步更新菜单、返回列表、面包屑、登录回跳允许列表、运维中心详情链接和接受测试。只允许同源、安全的内部回跳；非法目标不因兼容地址转换获得访问能力。切换 Workspace 清理缓存与过期请求，历史 URL 不成为越权读取入口。

### 提案与实施关系

本文固定产品目标、参数语义和能力开放条件。接受之后，按可验收的垂直范围提交实现 PR；首次交付不以实现全部未来能力为前提，也不允许用未实现的控件替代实际能力。实现或 CI 通过不自动将本文标记为 Accepted；决定在提案 PR 中明确记录。

## Test Plan

以下是实现的验收要求，不是此文档 PR 已运行的测试结果。

| 范围 | 必须验证的场景 |
| --- | --- |
| 类型与步骤 | 不同有向类型组合返回不同方案；默认组合合法；当前 Hybrid 不允许取消必选数据步骤；无数据步骤、未知步骤、重复步骤和不支持组合被后端拒绝 |
| 联动与网络 | 快速切换来源、去向和具体类型；乱序响应、查询失败、无候选；旧结果不污染新选择，下一步不能使用过期组合 |
| 创建草稿 | 类型先于连接；新增连接后返回；刷新、返回和取消；用户名称不被覆盖；没有有效配置前不产生持久任务 |
| 单一产品入口 | 同一页混合展示所有同步方案；筛选、排序、总数和分页由服务端统一；无离线实时分页拼接、逐行实例或 Schedule 请求 |
| 参数版本 | 版本 2 的两类任务按原语义投影和执行；新参数版本 3 保存回显一致；非法混合格式被拒绝；不修改历史快照、不自动发布或运行 |
| 映射 | 单表恰好一条映射；批量零表、重复来源、重复目标、不兼容 Schema、来源或目标命名空间越界；整库只选一张表时方案身份不变 |
| 规则边界 | 初次统一拒绝规则输入；日后验证明确过滤与持续规则的区别、切换确认、预览失效和固定实例对象集合 |
| 结构与写入 | 校验和预览不执行 DDL；未启用结构迁移仍校验目标；覆盖范围与清理次数；不兼容目标不被悄悄删除；实时变更不降级为 Append |
| 运行与调度 | 已上线条件、实际调度资格；停止实例与停用调度互不混淆；任务保存成功但调度失败后重试不重复创建；现有取消和资源占用回归 |
| 阶段与进度 | 全量 37% 到阶段 100% 后进入增量动画；实例仍运行；未知、过期、失败、取消与待确认不伪造进度；Split 口径不冒充行数；不平均无限增量进度 |
| 历史与恢复 | 当前配置变更不改变历史阶段、映射和日志；新 Instance 不隐式恢复旧检查点；复制新建不复制位点或启用调度 |
| 多表准入 | 一个目标冲突时不部分启动；全部资源原子获得或释放；局部失败不会显示整体成功，不能通过 UI 绕过任务恢复边界 |
| Workspace 与访问 | 类型查询认证、实际连接授权、批量摘要、草稿、旧链接和登录回跳的隔离；没有明文凭据、跨空间缓存或迟到响应泄露 |
| 浏览器交互 | 真实列表、创建、编辑、详情和调度操作；键盘、焦点、窄屏、长名称、异常返回和 `prefers-reduced-motion` |

保留现有格式、类型、架构、构建和 CI 检查。新开放的 JDBC、CDC、步骤和目标写入组合必须提供真实数据库的产品端到端证据；引擎单测或前端构建通过不能代替这种验收。提案文档自身检查六章节、元数据、链接和单文件范围；文档范围 CI 的跳过项不能报告为运行验收通过。

## Rejected Alternatives

| 方案 | 不采用的原因 |
| --- | --- |
| 保留离线、实时两套页面，外面套两个 Tab | 只改导航外观，查询、操作与表单仍有两套身份 |
| 四个认知维度都做独立必填下拉框 | 用户重复表达时效与策略，容易保存冲突配置 |
| 先选实际连接，再决定是否存在支持方案 | 本次选择类型先行的方案发现入口；没有连接也能了解可用链路，连接权限检查留在具体配置阶段 |
| 根据映射数量或 Connector 名称猜测具体类型 | 规则恰好匹配一张表或更换 Connector 时，任务方案身份会错误改变 |
| 让三个 Checkbox 自由组合、执行时再报错 | 无法保证配置承诺与真实计划一致，可能跳过初始化或创建不终止的 Job |
| 为单表、多表、全量、增量分别新增 Task 插件和执行引擎 | 重复既有 Task、Factory、Planner 和 YakFlow 责任，不能解决产品表达问题 |
| 为动态候选建设通用表单平台和多层能力注册框架 | 当前只需具体的同步业务契约和已有组件，不需要没有独立调用方的抽象 |
| 为统一页面重写历史参数、自动覆盖目标或恢复旧检查点 | 页面合并不能改变执行事实、数据安全和已接受恢复边界 |
| 一次展示所有未来选项，再用说明文字标记尚未实现 | 用户可见选择应对应真实可运行能力，完整产品设计不等于已经交付 |

[dw-realtime]: https://help.aliyun.com/zh/dataworks/user-guide/configure-a-data-synchronization-solution-in-data-integration
[dw-offline]: https://help.aliyun.com/zh/dataworks/user-guide/configure-a-database-wide-offline-synchronization-task
[dw-solutions]: https://help.aliyun.com/zh/dataworks/user-guide/supported-data-source-types-and-read-and-write-operations/
[dw-incremental]: https://help.aliyun.com/zh/dataworks/user-guide/configure-a-batch-synchronization-node-to-synchronize-only-incremental-data
[code-router]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-ops-ui/apps/web/app/router/AppRouter.tsx
[code-definition]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-ops-ui/apps/web/service/data-sync/definition.ts
[code-cdc]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source/MySqlCdcSourceFactory.java
[code-sink]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-flow/yak-flow-connector-jdbc/src/main/java/io/yak/ops/connector/jdbc/sink/JdbcSinkFactory.java
[code-execution]: https://github.com/weifuwan/yak-ops/blob/1da2d2555f4411e72550606b4fca45f5e2cdb788/yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/main/java/io/yak/ops/plugin/task/datasync/config/ExecutionConfigurationFactory.java
