# Lineage Requirements

本文描述 Lineage 模块需要长期保持的业务能力与边界，不记录阶段性实现方案。

## Responsibilities

Lineage 负责：

- 注册、更新并查询统一的 `LineageAsset`；
- 注册带类型、来源证据、置信度与观测时间的有向 `LineageRelation`；
- 支持批量写入并对同批输入做稳定去重；
- 以指定根资产查询 upstream / downstream / both 血缘图；
- 对自动生成血缘按 evidence scope 做安全替换和清理；
- 提供 source-neutral 的 SQL projection lineage 分析 contract，供数据开发、Dataset 等调用方复用；
- 为未来 Flink / Spark / Hadoop 等来源保留角色化接入边界。

## Stable Behavior

当前 HTTP、数据库与 Flyway contract 在纯结构重构中保持不变。

图查询继续保持：

- 根资产必须存在；
- `depth` 有明确上限，当前最大值为 10；
- 遍历过程中节点与关系去重；
- 上下游方向由关系的 source / target 决定，不在查询层反转领域语义。

关系写入继续保持：

- source / target 必须是有效资产；
- 不允许资产指向自身；
- `confidence` 必须在 `[0, 1]`；
- evidence 字段用于追踪生成来源，不把来源实现细节变成资产身份。

## Replacement Safety

自动生成血缘允许按 `sourceType + sourceId` 替换，但清理旧资产必须同时满足 ownership、无残留边、无子节点等安全条件。

旧 revision 在新 revision 已提交后不能覆盖新结果。并发发布的正确性属于 Lineage contract，不应下沉为 Controller 或 SQL 拼接约定。

## Analysis Boundary

`SqlProjectionLineageAnalyzer` 是 source-neutral contract。Lineage core 不绑定数据开发模块或某个具体 SQL parser；解析器实现应停留在调用方或明确的 adapter/analysis 实现边界。

未来新增 Flink / Spark / Hadoop 血缘时，应优先回答“它承担什么角色”，再决定 package 和依赖，而不是按技术栈复制一套完整业务结构。

## Non-goals

Lineage 不负责：

- 调度 Flink / Spark / Hadoop 作业；
- 成为数据开发、离线同步、实时同步的第二套任务状态中心；
- 在 Core Domain 中直接依赖 MyBatis、Spring MVC、Flink/Spark/Hadoop SDK；
- 为每个来源维护独立的 Asset / Relation 模型；
- 在结构重构 PR 中顺便修改 REST path、表结构或既有迁移脚本。

## Acceptance

Lineage 的结构改造至少需要满足：

- 业务行为测试继续通过；
- architecture / dependency guard 继续通过；
- HTTP DTO / VO 不进入 Repository/DAO contract；
- DAO/PO 不进入稳定 Service 与 Domain contract；
- 新依赖方向与 `ARCHITECTURE.md + DEPENDENCIES.md` 一致。
