# Realtime Sync Decisions

> 这里只记录少量**长期有效的关键取舍**。过程性讨论、迁移步骤和历史状态看 Git / PR。

## 1. Task / Version / Execution 分开

决定：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

三个生命周期独立。

原因：任务可以继续编辑，已发布版本必须不可变，运行实例必须保留自己的历史证据。

## 2. SyncDefinition 只有一份

决定：Wizard、Yak YAML、HTTP、DB JSON 都映射到同一个 `SyncDefinition`。

原因：避免不同编辑模式演进成两套执行逻辑和两套事实模型。

## 3. Published Version 不可变

决定：Publish 产生/复用 immutable `DefinitionVersion`；Start 只读取 Published Version。

原因：`Published V3 + Draft V4 + Running V3` 必须能同时存在，运行行为不能被后续编辑改写。

## 4. Runtime state 属于 SyncExecution

决定：`DesiredState / ObservedState` 的真实 owner 是 `SyncExecution`，不是 Task。

原因：Task 是长期定义，Execution 是一次运行。把两者混在一起会导致编辑、发布、启动、停止互相锁死。

## 5. Terminal Execution 不复活

决定：`STOPPED / FAILED` 是单个 Execution 的终态；再次运行创建新 Execution。

原因：保留完整运行历史，避免一个 Execution ID 表示多次运行。

## 6. UNKNOWN / CONFLICT 不等于失败

决定：状态未确认时禁止自动创建第二实例，先 Reconcile。

原因：外部 Flink Job 可能已经成功运行，贸然重试会造成双跑。

## 7. Restart 和升级版本分开

决定：

```text
RestartExecution(E100/V3) -> E101/V3
ApplyPublishedVersion     -> new Execution / captured Published Version
```

原因：用户的“重启”和“升级”是两个不同意图，不能用一个模糊 restart 隐式切版本。

## 8. 目标版本先 Preflight 再 Stop

决定：Restart / Apply 必须先校验目标版本，成功后才能停止当前健康 Execution。

原因：新版本不可运行时，不能先把线上正常任务停掉。

## 9. 场景优先组合，不优先加类型

决定：单表、多表、Pattern、未来整库优先用 `Selector + Route + Target + Policy` 表达。

原因：产品场景会不断增加，核心领域不应该跟着 UI 标签膨胀成大量 `syncType / sceneType / *Task`。

## 10. Flink / SSH / DataSource 不进入 Core

决定：Core 只保存同步语义和稳定引用；运行环境保存 Ref/Snapshot；Flink、SSH、Credential、Connector 私有参数留在边界外。

原因：Realtime Sync 领域描述“数据如何持续同步”，不是描述某个具体引擎如何启动。

## 11. 兼容名可以留，领域语义不能倒退

决定：`definition_version / published_version / latestDeployment / config_digest / status` 等 v1 名字可以渐进保留。

原因：兼容外部 API/DB 比一次性改名重要；但新代码必须使用明确的 Domain 语义。

## 12. 文档保持小，历史交给 Git

决定：长期只保留：

```text
DOMAIN.md
README.md
DECISIONS.md
```

新规则优先修改现有文档，不继续新增 `08-xxx / 09-xxx` 阶段文档。

原因：规范只有能被真正读完、Review 和维护，才有约束力。
