# Realtime Sync Stage 6 Migration Completion

> 状态：Stage 6 / Wave 0～6 已完成核心迁移。
>
> 本文描述 **当前实现事实**。阶段 4 的 `04-current-code-mapping.md` 是迁移前/迁移中的历史快照；如果两者描述现状不同，以 `DOMAIN.md` 和本文为当前事实。

## 1. 最终领域坐标

Realtime Sync 当前继续以三个聚合为唯一坐标：

```text
RealtimeSyncTask
      │ publish
      ▼
DefinitionVersion (immutable)
      │ start / restart / apply
      ▼
SyncExecution
```

核心配置仍然只有一个事实模型：

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

Wizard、Yak YAML、REST DTO、数据库 JSON、Flink CDC YAML 都只是编辑/序列化/编译适配，不是第二套领域配置。

---

## 2. Wave 0～6 完成状态

```text
Wave 0  Core VO + legacy compatibility mapper                         ✅
Wave 1  Immutable DefinitionVersion persistence                        ✅
Wave 2  Start by immutable Published DefinitionVersion                 ✅
Wave 3  SyncExecution owns desired/observed lifecycle                  ✅
Wave 4  Active Execution no longer blocks Draft Save / Publish         ✅
Wave 5  RestartExecution / ApplyPublishedVersion explicit split         ✅
Wave 6  Contract cleanup / legacy runtime projection isolation          ✅
```

Stage 6 的完成含义不是“所有技术债清零”，而是：

> **Task / Version / Execution 的领域事实、生命周期和应用命令已经从 legacy 表结构中解耦，后续代码不得再以旧字段/旧命名反推领域模型。**

---

## 3. Wave 6 最终 contract cleanup

### 3.1 Task runtime projection 不再参与运行真相

物理表 `yak_realtime_job_definition` 目前仍保留：

```text
desired_state
observed_state
last_error
```

但 Wave 6 后它们是 **inert compatibility columns**：

```text
Application command truth  -> SyncExecution
Reconcile truth            -> SyncExecution
Read model runtime state   -> latest SyncExecution
```

代码不再：

```text
Execution lifecycle -> dual-write Task runtime columns
Task runtime columns -> fallback API runtime state
```

没有任何 Execution 的 Task 在 read model 中派生为：

```text
desired = STOPPED
observed = STOPPED
lastError = null
```

而不是读取旧 Task projection。

### 3.2 删除旧 Task-runtime 查询旁路

以下 contract 已从 DAO / Store 边界清除：

```text
desiredJobs
hasOtherDesiredRunning
lockOtherDesiredRunning
markStarting (Task projection only)
```

Reconcile 候选只从 latest SyncExecution 查询产生。

### 3.3 Digest 语义显式区分

物理列暂时仍可能叫 `config_digest`，但代码语义固定为：

```text
Task/Draft compatibility digest
  -> sourceConfigDigest

SyncExecution compiled artifact digest
  -> artifactDigest / ExecutionArtifactDigest

Core semantic digest
  -> DefinitionDigest
```

新代码不得因为物理列同名，再把这三种摘要当成一个概念。

### 3.4 Frontend runtime type 使用 SyncExecution 语义

前端新增/使用：

```text
RealtimeExecution
```

v1 JSON 为兼容仍保留：

```text
latestDeployment
```

但 UI / 新代码必须将其理解为一个 `SyncExecution` read projection，而不是另一个 Deployment 领域模型。

### 3.5 generic restart 从内部应用 contract 清除

内部 Application 只存在：

```text
restartExecution
applyPublishedVersion
```

前端不再允许 `restart` 泛化 action。

HTTP `/restart` 可以暂时保留为外部兼容 alias，但语义只能委托 `RestartExecution`，不能重新引入 restart-to-latest。

---

## 4. Contract-but-not-drop

Wave 6 **没有**执行 Big-Bang schema rename/drop。

以下物理兼容结构允许暂时存在：

```text
yak_realtime_job_definition
yak_realtime_job_deployment
Task desired_state / observed_state / last_error
Deployment status
Definition / Deployment config_digest
legacy definition_version / published_version
```

存在这些列不代表领域仍然依赖它们。

约束是：

```text
MUST NOT add new domain/application dependency on inert Task runtime columns
MUST NOT use deployment.status as lifecycle truth
MUST NOT compare definition_version / published_version as DefinitionVersion identity
MUST NOT treat both config_digest columns as the same digest semantic
```

物理列真正删除应单独走：

```text
expand -> verify no reader/writer -> contract migration
```

并考虑既有环境的 Flyway/回滚兼容，不在本 Wave 强删。

---

## 5. 当前命令语义

### Save / Publish

```text
Draft Save
  -> RealtimeSyncTask.currentDraft only

Publish
  -> immutable DefinitionVersion
  -> advance Task.publishedDefinitionRef
```

两者都不会修改 active SyncExecution。

### Start

```text
Start
  -> command-time PublishedDefinitionRef
  -> exact immutable DefinitionVersion
  -> new SyncExecution
```

### RestartExecution

```text
E100(V3)
  -> preflight exact V3
  -> reserve Stop under DB lock
  -> E100 STOPPED
  -> E101(V3)
```

### ApplyPublishedVersion

```text
E100(V3)
PublishedRef = V4
  -> capture exact V4 at command start
  -> preflight V4
  -> reserve Stop under DB lock
  -> E100 STOPPED
  -> E101(V4)
```

### Stop / Reconcile

只读取和修改 SyncExecution lifecycle。

---

## 6. 仍然存在、但不是 Wave 6 cleanup 的 Gap

Stage 6 完成后以下问题仍然需要独立领域/架构决策，不应偷偷塞进 cleanup：

### GAP-A：Audit-safe delete

当前删除链仍会物理删除 Execution/Event 历史，与“历史事实默认不可变”目标不一致。

未来需要：

```text
Archive / Tombstone / Retention policy
```

而不是在一次 rename cleanup 中直接改掉。

### GAP-B：ExecutionPolicy runtime application

当前 checkpoint/restart 等部分配置已经进入定义，但 Flink 编译/运行链未完整应用。

规则仍然是：

```text
apply correctly OR reject explicitly
never silently ignore
```

### GAP-C：FINISHED normal completion / snapshot-only

Flink `FINISHED` 尚未成为独立正常终态语义，因此 `SNAPSHOT_ONLY` 仍不能安全开放。

### GAP-D：legacy failure-rate mapping

旧 `failure-rate` 配置语义信息不足，仍应保持 `LEGACY_UNMAPPED`，不能伪造 Core RestartPolicy。

### GAP-E：Read-model / Compute Context physical package cleanup

`RealtimeJobView` 等 read model、ComputeEnvironment 等对象仍可能物理位于 realtime module/domain package。

这属于后续 package/context hygiene，不应为了“看起来 DDD”重写工作正常的运行机制。

### GAP-F：API v2 / physical schema naming

`latestDeployment`、job/definition/deployment 表名等外部/物理 legacy 命名仍可保留 v1 compatibility。

真正 API/schema v2 应单独设计和迁移，不与 Stage 6 混做。

---

## 7. Stage 6 之后 AI 的默认判断

看到下面这些 legacy 名字：

```text
definition_version
published_version
config_digest
status
latestDeployment
RealtimeJobDefinitionPO
RealtimeJobDeploymentPO
```

AI 必须先查 `DOMAIN.md` / 本文 Mapping，而不是从名字反推领域。

当前语义：

```text
definition_version          = DraftRevision compatibility field
published_version           = published DraftRevision compatibility marker
published_definition_version_id = immutable Published DefinitionVersion identity
execution.definition_version_id  = immutable Execution Version identity
Task runtime columns        = inert compatibility storage
Deployment.status           = physical compatibility mirror
latestDeployment JSON       = latest SyncExecution read projection
```

如果新需求需要重新依赖已 inert 的字段，必须先标记：

```text
Domain Gap / Migration Regression
```

不能为了实现方便重新把 Task、Version、Execution 混回去。

---

## 8. Stage 6 验收结论

经过 Wave 0～6，Realtime Sync 已经能够稳定表达：

```text
Running E100(V3)
+
Draft r4
+
Published V4
```

并且：

```text
Save Draft       != mutate E100
Publish V4       != mutate E100
RestartExecution -> new same-version Execution
ApplyPublished   -> explicit new-version Execution
Runtime state    -> SyncExecution only
Version identity -> immutable DefinitionVersionId only
```

后续扩展实时同步能力时，**优先扩展现有领域模型和 Port/Adapter，不得重新创建第二套 Task/Spec/Runtime state truth。**
