# Realtime Sync Domain Design

> 目标：为 Yak Ops 实时同步建立稳定、可演进、可约束 AI 的领域内核。

这组文档描述的是 **Realtime Sync Domain**，不是 Flink CDC 使用手册，也不是前端页面说明。

任何实时同步需求进入代码前，都应先判断它属于：

- Domain：实时同步业务概念与规则；
- Application：用例编排、发布、启动、停止、重启、版本切换；
- Infrastructure：Flink、CDC Connector、SSH、REST、YAML 编译、数据库持久化；
- Interface/UI：Controller、DTO、Wizard、YAML Editor 等交互适配。

## 阶段路线

| 阶段 | 文档 | 目标 |
|---|---|---|
| 1 | [领域边界与统一语言](./01-domain-boundary-and-language.md) | 定义实时同步负责什么、绝不负责什么，以及统一术语 |
| 2 | [核心领域模型 v1](./02-core-domain-model.md) | 确定聚合根、Entity、Value Object 和核心对象关系 |
| 3 | [领域不变量与生命周期](./03-invariants-and-lifecycle.md) | 固定 Draft / Publish / Execution 不变量、状态机、并发和快照规则 |
| 4 | [现有代码到领域模型 Mapping](./04-current-code-mapping.md) | 迁移前/迁移中的代码 Mapping、Gap 与施工顺序 |
| 5 | [AI 领域开发宪法](./05-ai-domain-rules.md) | 把阶段 1～4 转换成 AI 强制执行规则 |
| 6 | [Stage 6 Migration Completion](./06-stage6-migration-completion.md) | 记录 Wave 0～6 完成后的当前实现事实、兼容边界和剩余 Gap |
| 7 | 待补充 | 自动化领域护栏扩展 |

模块级最高优先级硬规则入口：

```text
yak-ops-business/yak-ops-business-sync/
yak-ops-business-sync-realtime/DOMAIN.md
```

任何 AI / Codex / 开发者修改 realtime-sync 代码前，都必须先读 `DOMAIN.md`。

---

## 当前领域模型

Realtime Sync Core Domain 使用三个聚合根：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

`SyncDefinition` 是唯一配置事实模型：

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

核心生命周期：

```text
Task.currentDraft
   ↓ publish
DefinitionVersion (immutable)
   ↓ start
SyncExecution
```

核心原则：

- `Task ≠ Definition ≠ Version ≠ Execution`；
- Draft 可以在旧 Execution 运行时继续编辑/发布；
- Start 只读取不可变 Published DefinitionVersion；
- RestartExecution 固定旧 Execution 的 VersionRef；
- ApplyPublishedVersion 显式使用命令开始时捕获的 Published Ref；
- 每次 Start/Restart/Apply 都创建新的 SyncExecution；
- 单个 Execution 的 `STOPPED / FAILED` 是终态；
- `UNKNOWN / CONFLICT` 禁止自动创建第二个运行实例；
- Runtime Environment：Definition 存 Ref，Execution 存 Snapshot；
- Flink / YAML / SSH / JDBC credentials / adapter-private tuning 不进入 Core Domain；
- 新场景优先扩 Selector / Route / Target / Policy，不优先增加 sceneType/syncType。

---

## Stage 6 当前实现事实

Stage 6 已按既定顺序完成：

```text
Wave 0  Core VO + compatibility mapper                         ✅
Wave 1  Immutable DefinitionVersion                            ✅
Wave 2  Start by Published DefinitionVersion                   ✅
Wave 3  SyncExecution lifecycle ownership                      ✅
Wave 4  Active Execution 下继续编辑 / 发布                      ✅
Wave 5  RestartExecution / ApplyPublishedVersion               ✅
Wave 6  Legacy runtime projection / contract cleanup           ✅
```

当前可以稳定表达：

```text
Running E100(V3)
+
Draft r4
+
Published V4
```

并满足：

```text
Save Draft       != mutate E100
Publish V4       != mutate E100
RestartExecution -> E101(V3)
ApplyPublished   -> E101(V4)
Runtime state    -> SyncExecution only
Version identity -> immutable DefinitionVersionId only
```

### Task runtime legacy columns

物理表仍可能存在：

```text
desired_state
observed_state
last_error
```

但 Wave 6 后它们是 inert compatibility storage：

- Application 不写；
- Runtime command 不读；
- Read model 不 fallback；
- 无 Execution 的 Task 派生为 STOPPED / STOPPED / null。

### Legacy names still physically/API-visible

为了兼容，以下名字可以暂时存在：

```text
yak_realtime_job_definition
yak_realtime_job_deployment
definition_version
published_version
config_digest
status
latestDeployment
HTTP /restart alias
```

它们不再决定领域语义。

当前 Mapping 以 [Stage 6 Migration Completion](./06-stage6-migration-completion.md) 为准；阶段 4 文档中的“当前实现事实”是当时的**历史迁移快照**，不能覆盖 Stage 6 后的新事实。

---

## 仍然存在的独立 Gap

Stage 6 完成不代表所有技术债清零。以下问题必须单独评审：

```text
Audit-safe Archive/Tombstone delete
ExecutionPolicy checkpoint/restart runtime application
Flink FINISHED normal completion / snapshot-only
legacy failure-rate mapping
Read-model package hygiene
Compute Environment physical context/package cleanup
API v2 / physical schema naming cleanup
```

这些问题不能以“cleanup”名义偷偷进入普通功能 PR。

---

## AI 使用顺序

以后修改 realtime-sync：

```text
1. 读 DOMAIN.md
2. 做 Domain Impact Analysis
3. 查 06-stage6-migration-completion.md 当前事实
4. 需要设计依据时再查 01～05 文档
5. 对照测试与现有 Adapter
6. 实现前确认没有 Domain Gap
```

如果一个新需求无法映射到三个聚合、`SyncDefinition` 子模型、现有生命周期或明确的邻接上下文：

```text
Domain Gap = yes
```

先讨论模型，不允许直接增加新的 `syncType / sceneType / *Spec / *Task` 体系，也不能绕过幂等、快照、UNKNOWN/CONFLICT、runtime identity、credential zeroize 等安全机制。
