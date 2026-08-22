# Realtime Sync Domain Design

> 目标：为 Yak Ops 实时同步建立稳定、可演进、可约束 AI 的领域内核。

这组文档描述的是 **Realtime Sync Domain**，不是 Flink CDC 使用手册，也不是前端页面说明。

任何实时同步需求在进入代码实现前，都应先判断它属于：

- Domain：实时同步本身的业务概念与规则；
- Application：用例编排、发布、启动、停止等应用流程；
- Infrastructure：Flink、CDC Connector、SSH、REST、YAML 编译、数据库持久化；
- Interface/UI：Controller、DTO、Wizard、YAML Editor 等交互适配。

## 阶段路线

| 阶段 | 文档 | 目标 |
|---|---|---|
| 1 | [领域边界与统一语言](./01-domain-boundary-and-language.md) | 定义实时同步负责什么、绝不负责什么，以及统一术语 |
| 2 | [核心领域模型 v1](./02-core-domain-model.md) | 确定聚合根、Entity、Value Object 和核心对象关系 |
| 3 | [领域不变量与生命周期](./03-invariants-and-lifecycle.md) | 固定 Draft / Publish / Execution 不变量、状态机、并发和快照规则 |
| 4 | 待补充 | 现有代码到领域模型 Mapping |
| 5 | 待补充 | AI 领域开发宪法 |
| 6 | 待补充 | 最小领域重构 |
| 7 | 待补充 | 自动化领域护栏 |

## 当前已接受的模型方向

Realtime Sync Core Domain 使用三个聚合根：

```text
RealtimeSyncTask
DefinitionVersion
SyncExecution
```

`SyncDefinition` 是不可变 Value Object：

```text
SyncDefinition
├── SourceEndpoint
├── SinkEndpoint
├── SyncRoute[]
├── SyncPolicy
└── ExecutionPolicy
```

阶段 3 进一步固定生命周期规则：

```text
Task Draft
   ↓ publish
DefinitionVersion vN (immutable)
   ↓ start
SyncExecution EN
```

核心原则：

- Task、Version、Execution 生命周期分离；
- Draft 可以在旧 Execution 运行时继续编辑和发布，新版本不会自动影响旧 Execution；
- Start 只读取 `PublishedDefinitionRef`，不读取当前 Draft；Draft 有未发布修改不使旧 Published Version 自动失效；
- 同一个 Task v1 最多一个 Active / Uncertain Execution；
- 每次 Start / Restart 都创建新的 SyncExecution，单个 Execution 的 `STOPPED / FAILED` 是终态；
- `UNKNOWN` 表示外部事实尚不可确认，`CONFLICT` 表示存在歧义，二者都禁止自动创建第二个运行实例；
- Restart 同版本与 Apply Published Version 必须是两种不同语义，禁止重启时偷偷升级；
- Published Version、Execution Definition Snapshot、Runtime Environment Snapshot 都是不可变运行证据；
- Route / Selector / Policy 优先组合表达单表、多表、规则匹配和未来整库场景；
- ReplayKey 是 v1 强制领域语义，不存在 `strictReplaySafety=false` 的 Core Model；
- Runtime Environment 不进入 `SyncDefinition`，Definition 保存 Ref，Execution 保存 Snapshot；
- Flink、YAML、SSH、JDBC 私有调优不进入 Core Domain。

现有 `CdcPipelineSpec`、`DefinitionRow`、`DeploymentRow`、`RealtimeStateMachine` 暂不因为文档命名立即重构。阶段 4 会完成详细 Mapping，阶段 6 再执行最小迁移。

如果一个新需求无法映射到当前三个聚合、`SyncDefinition` 子模型或阶段 3 生命周期，应先记录为 **Domain Gap**，不要直接增加新的 `syncType / sceneType / *Spec / *Task` 体系或绕过状态机。
