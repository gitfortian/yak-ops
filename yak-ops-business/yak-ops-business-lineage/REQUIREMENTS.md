# Lineage Requirements

本文描述 Lineage 模块需要长期保持的业务能力与边界，不记录阶段性实现方案。

## Responsibilities

Lineage 负责：

- 注册、更新并查询统一的 `LineageAsset`；
- 注册带类型、来源证据、置信度与观测时间的有向 `LineageRelation`；
- 支持批量写入并对同批输入做稳定去重；
- 以指定根资产查询 upstream、downstream 或 both 血缘图；
- 对自动生成血缘按 evidence scope 做安全替换和清理；
- 提供 source-neutral 的 SQL projection lineage 分析 contract；
- 为真实平台来源提供统一 Domain 与稳定写入边界。

## Stable Behavior

当前 HTTP、数据库与 Flyway contract 在纯架构调整中保持不变。

图查询继续保持：

- 根资产必须存在；
- `depth` 有明确上限，当前最大值为 10；
- 遍历过程中节点与关系去重；
- 上下游方向由关系的 source/target 决定，不在查询层反转领域语义。

关系写入继续保持：

- source/target 必须是有效资产；
- 不允许资产指向自身；
- `confidence` 必须在 `[0, 1]`；
- evidence 用于追踪生成来源，不把实现细节变成资产身份。

## Replacement Safety

自动生成血缘允许按 `sourceType + sourceId` 替换，但清理旧资产必须同时满足 ownership、无残留边、无子节点等安全条件。

旧 revision 在新 revision 已提交后不能覆盖新结果。并发发布正确性属于 Lineage contract，不下沉为 Controller 或 SQL 拼接约定。

## Analysis Boundary

`SqlProjectionLineageAnalyzer` 是 source-neutral contract。Lineage 不绑定 Data Development 或某个具体 SQL parser；实现停留在拥有 parser 的邻接模块。

## Public Contract

邻接业务模块只允许使用：

- `analysis.sql.SqlProjectionLineageAnalyzer`；
- 公开的 Asset/Relation/Graph 只读领域类型及枚举；
- `LineageQueryService`；
- `LineageWriteService`；
- `LineageMaintenanceService`；
- 上述入口所属的公开嵌套命令、结果和 scope。

以下内容不是跨模块 contract：

- Controller DTO/VO；
- Repository、RepositoryAdapter；
- DAO、Mapper、PO；
- Persistence Config；
- `LineageAssetDraft`、`LineageRelationDraft`。

调用方应通过自身 Gateway/Adapter 隔离 Lineage 类型，避免将邻接上下文 contract 扩散进自身核心 Domain。

## Platform Extension

当前没有活动的 Collector package，也不为 Flink、Spark、Hadoop 创建占位接口。

出现真实平台事件或 SDK 时，需要先明确：

- evidence owner；
- 事件重放与重复处理；
- replacement scope；
- SDK/event 到统一 Domain 的转换；
- 失败与恢复；
- 是否真的需要新增公共 contract 或 Maven artifact。

只有这些条件明确并存在真实调用链，才新增角色 package 和实现。

## Non-goals

Lineage 不负责：

- 调度 Flink、Spark、Hadoop 作业；
- 成为数据开发、离线同步、实时同步的第二套任务状态中心；
- 在 Domain 中直接依赖 MyBatis、Spring MVC 或平台 SDK；
- 为每个来源维护独立的 Asset/Relation 模型；
- 用空 package、空接口或未来白名单表达尚未实现的架构；
- 向邻接模块公开 Repository、DAO、Config、Controller 或 Draft；
- 在结构调整中顺便修改 REST path、表结构或既有迁移脚本。

## Acceptance

Lineage 变更至少满足：

- 业务行为测试继续通过；
- 活动 package 集合与依赖图精确匹配；
- 声明图和实际 import 图无环；
- 公共 API 仅包含已声明类型根；
- HTTP DTO/VO 不进入 Repository/DAO contract；
- DAO/PO 不进入稳定 Service 与 Domain contract；
- Maven runtime capability 由正确模块提供；
- Java source、文档和架构 guard 同步更新；
- 新依赖方向与 `ARCHITECTURE.md + DEPENDENCIES.md` 一致。
