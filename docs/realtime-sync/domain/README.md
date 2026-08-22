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
| 2 | 待补充 | 核心领域模型 v1：聚合根、Entity、Value Object |
| 3 | 待补充 | 领域不变量与生命周期 |
| 4 | 待补充 | 现有代码到领域模型 Mapping |
| 5 | 待补充 | AI 领域开发宪法 |
| 6 | 待补充 | 最小领域重构 |
| 7 | 待补充 | 自动化领域护栏 |

## 当前原则

在阶段 2 完成前，不因为文档命名而大规模重命名现有 Java 类。

例如当前 `CdcPipelineSpec`、`TableRoute`、`DeploymentRow` 仍然可以继续存在；阶段 1 先建立统一语言和边界，后续再决定如何逐步映射与迁移。

如果一个新需求无法用阶段 1 的统一语言描述，应先记录为 **Domain Gap**，不要直接增加新的 `syncType / sceneType / *Spec / *Task` 体系。
