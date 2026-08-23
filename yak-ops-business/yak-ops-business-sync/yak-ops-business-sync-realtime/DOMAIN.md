# Realtime Sync Domain

> 本文件只保留**实现必须遵守的硬规则**，不记录设计过程。历史演进看 Git / PR。
>
> `REQUIREMENTS.md` 定义“需要什么”；`DOMAIN.md` 定义“不能违反什么”；`ARCHITECTURE.md` 定义“代码边界如何收敛”；`REVIEW.md` 定义“怎么判卷”。

## 核心模型

```text
RealtimeSyncTask
      │ publish
      ▼
DefinitionVersion (immutable)
      │ start
      ▼
SyncExecution
```

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
│   ├── SourceSelector
│   ├── SinkTarget
│   └── ReplayKey
├── SyncPolicy
└── ExecutionPolicy
```

**Task ≠ DefinitionVersion ≠ Execution。**

## 12 条硬规则

1. **`SyncDefinition` 是唯一配置事实。** Wizard、Yak YAML、HTTP DTO、DB JSON、Flink YAML 都只是 Adapter / Projection。
2. **Published Version 不可变。** Execution 只能引用明确的 `DefinitionVersionId`，不能读取当前 Draft。
3. **Draft、Published、Running 可以同时存在。** 运行 V3 时可以继续编辑和发布 V4，V4 不会热更新 V3。
4. **每次运行都是新的 `SyncExecution`。** 单个 Execution 的 `STOPPED / FAILED` 是终态，不能复活。
5. **运行状态只属于 `SyncExecution`。** Task 表里的 `desired_state / observed_state / last_error` 只是遗留兼容字段，不能作为运行真相。
6. **同一 Task 只允许一个 Active / Uncertain Execution。** `STARTING / RUNNING / STOPPING / UNKNOWN / CONFLICT` 都阻止第二实例。
7. **`UNKNOWN` 不是失败，`CONFLICT` 不能猜。** 必须先 Reconcile，不能为了重试强行转 FAILED。
8. **Restart 和升级版本是两个命令。** `RestartExecution(E100/V3) -> E101/V3`；`ApplyPublishedVersion -> 新 Execution / 新 Published Version`。
9. **Restart / Apply 必须先校验目标版本，再停止健康任务。** Apply 的目标 Published Version 在命令开始时固定，不能执行中漂移。
10. **新场景优先扩 Selector / Route / Target / Policy。** 不因为“单表、多表、整库、分库分表”直接增加 `syncType / sceneType / *Task`。
11. **Core Domain 不包含 Flink / SSH / JDBC Credential / Adapter 私有参数。** Runtime Environment、DataSource 是邻接上下文；Execution 保存运行环境 Snapshot。
12. **无法映射现有模型就是 `Domain Gap`。** 先讨论模型，不用临时字段、boolean、enum、`*Spec / *Task / *Service` 绕过去。

## 关键不变量

- Source / Sink 必须有效，当前 v1 不允许同一 DataSourceRef。
- 至少一条 `SyncRoute`。
- 每条 Route 必须有非空、字段不重复的 `ReplayKey`。
- Route / ReplayKey 无业务意义的顺序变化，不应改变 `DefinitionDigest`。
- `DefinitionDigest`、`sourceConfigDigest`、`artifactDigest` 是三个不同概念。
- `ExecutionPolicy` 被接受后，运行引擎必须真正执行或明确拒绝，不能静默忽略。

## 命令语义

```text
Save Draft             -> RealtimeSyncTask.currentDraft
Publish                -> immutable DefinitionVersion
Start                  -> current Published Version -> new SyncExecution
RestartExecution       -> current Execution Version -> new same-version Execution
ApplyPublishedVersion  -> captured Published Version -> new Execution
Stop / Reconcile       -> SyncExecution lifecycle
```

## Runtime Truth

命令侧运行真相固定为：

```text
RealtimeSyncTask
    │ publish reference
    ▼
DefinitionVersion
    │ execution reference
    ▼
SyncExecution
```

```text
Task desired/observed/lastError = compatibility only
Deployment.status               = compatibility mirror only
Flink runtime state             = external evidence
SyncExecution                   = local runtime truth
```

任何结构重构都不能把运行真相重新搬回 Task、Controller、Engine Adapter 或查询投影。

## 遗留兼容字段

这些名字可以暂时存在，但**不是领域语义**：

```text
definition_version              = DraftRevision
published_version               = published DraftRevision marker
published_definition_version_id = immutable Published Version identity
latestDeployment                = latest SyncExecution projection
Deployment.status               = compatibility mirror
Task desired/observed/lastError = inert compatibility storage
HTTP /restart                   = restartExecution 的兼容 alias
```

禁止用 `definition_version / published_version` 判断真正的 Version identity。

Compatibility 如果必须继续存在，应停在拥有旧协议或旧持久化格式的边界；不得为了结构迁移把 compatibility mapper / facade 扩散回 Core Domain。

## 安全能力必须保留

```text
Idempotency-Key
DB command serialization / CAS
start reservation before external submit
same-key race recovery
prepared version re-check
stop-during-start
UNKNOWN / CONFLICT recovery
runtime identity persistence / recovery
RuntimeEnvironmentSnapshot
replacement-stop reservation
credential short lifetime + zeroize
secret-free persistence / log redaction
multi-instance reconcile lease
```

这些不是实现细节，可以在重构中换角色或换 package，但不能被删除、弱化或绕过。

## Architecture Boundary

领域模型与工程结构的关系以 `ARCHITECTURE.md` 为准。长期方向固定为业务子系统优先：

```text
Definition
Execution
Reconcile
Observability
Environment
Engine / Persistence boundaries
```

允许后续 PR 移动类、重命名角色、拆分过宽 Service，但必须满足：

- 不新增第二套 SyncDefinition / Execution 模型；
- 不改变 Published Version immutable contract；
- 不把 Query / Observability 变成 command truth owner；
- 不把 Flink / SSH / Credential / HTTP DTO / MyBatis PO 引入 Core Domain；
- 不为了和 offline-sync 目录一致提前抽 Shared Sync Kernel。

## 修改代码前后

修改前先确认 `REQUIREMENTS.md` 中已有对应能力，再写一个短块：

```text
Domain Impact Analysis
- Aggregate(s):
- Invariant/lifecycle impact:
- Layer / subsystem:
- Domain Gap: yes/no
```

涉及 package / role / dependency 调整时，再回答：

```text
Architecture Impact Analysis
- Target subsystem:
- Stable entry / gateway:
- Runtime truth owner:
- Dependency direction changed: yes/no
```

修改后写：

```text
Domain Compliance Report
- Rule changed/implemented:
- Safety/tests:
- Known gaps:
```

代码 Review 固定按 `REVIEW.md` 执行。

## 自动护栏

本地最快检查：

```bash
python3 tools/realtime_domain_guardrails.py
```

CI 强制执行：

```text
Static domain contract
Framework-free core domain smoke
```

完整 Maven/JUnit 深层回归依赖 private `yak-framework`；配置 `YAK_FRAMEWORK_TOKEN` 后自动启用。

**不要因为功能或结构调整被护栏拦住就删护栏。** 如果规则真的变化，同一个 PR 中同步修改当前 contract 文档和对应测试 / guardrail。

当前 `RealtimeArchitectureTest` 是迁移期间的基础安全网；完整 package dependency graph 与 explicit corridor guardrails 在目标 package 结构稳定后补齐，不应在结构尚未收敛时提前把临时依赖固化成永久白名单。

## 已知独立 Gap

```text
Archive / Tombstone delete
ExecutionPolicy checkpoint/restart runtime application
Flink FINISHED / snapshot-only
legacy failure-rate mapping
Compute Environment physical context cleanup
API v2 / physical schema naming cleanup
```

这些 Gap 需要单独做 Requirement / Domain 设计，不在纯架构重构中顺手解决。
