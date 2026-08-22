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
| 3 | 待补充 | 领域不变量与生命周期 |
| 4 | 待补充 | 现有代码到领域模型 Mapping |
| 5 | 待补充 | AI 领域开发宪法 |
| 6 | 待补充 | 最小领域重构 |
| 7 | 待补充 | 自动化领域护栏 |

## 当前已接受的模型方向

阶段 2 完成后，Realtime Sync Core Domain 使用三个聚合根：

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

核心原则：

- Task、Version、Execution 必须分离；
- Published Version 可以被独立稳定引用；
- Execution 永远来自 Published Version，不读取当前 Draft；
- Route / Selector / Policy 优先组合表达单表、多表、规则匹配和未来整库场景；
- Runtime Environment 不进入 `SyncDefinition`，Definition 保存 Ref，Execution 保存 Snapshot；
- Flink、YAML、SSH、JDBC 私有调优不进入 Core Domain。

现有 `CdcPipelineSpec`、`DefinitionRow`、`DeploymentRow` 暂不因为文档命名立即重构。阶段 4 会完成详细 Mapping，阶段 6 再执行最小迁移。

如果一个新需求无法映射到当前三个聚合或 `SyncDefinition` 的 Endpoint / Route / Policy 子模型，应先记录为 **Domain Gap**，不要直接增加新的 `syncType / sceneType / *Spec / *Task` 体系。
