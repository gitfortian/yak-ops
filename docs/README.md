# Yak Ops Docs

这是迁移期间保留的历史文档索引，不再定义日常后端开发的文档同步义务。新的开发与 FLIP 入口见 [CONTRIBUTING](../.github/CONTRIBUTING.md) 和 [DEVELOPMENT](../DEVELOPMENT.md)。已知目标时直接读取实现和相关测试。

## Migration Note

旧的 [Engineering Context Model](engineering-context-model.md) 已退役。任务入口见 [AGENTS.md](../AGENTS.md)；相关历史文档尚待独立清理，不要求随普通实现 PR 同步。

## Current Knowledge Map

### 产品与执行能力

| 修改内容 | 文档入口 |
| --- | --- |
| 数据源资源与连接能力 | [Datasource](capabilities/datasource/README.md) |
| Task 插件类型注册与配置校验 | [Task Plugin Contract](capabilities/task-plugin.md) |
| 通用任务定义、不可变版本及重置 | [Task Definition Contract](capabilities/task-definition.md) |
| 数据同步 Task Definition | [Task Plugin Contract](capabilities/task-plugin.md)；其执行、调度和运维功能已从当前后端移除 |
| 执行引擎与连接器 | [YakFlow](capabilities/yak-flow/README.md)；[Core / Runtime 执行契约（提案）](capabilities/yak-flow/core-runtime-contract.md) |
| 工作空间与成员 | [Workspace](capabilities/workspace/README.md) |
| 用户收藏与使用偏好 | [User Preference](capabilities/user-preference/README.md) |
| 身份与支撑平台边界 | [Platform Rules](../yak-ops-platform/PLATFORM_RULES.md)、[Security Rules](../yak-ops-platform/SECURITY_RULES.md) |

### 前端与共享组件

[Frontend Architecture](../yak-ops-ui/ARCHITECTURE.md) 定位代码职责；[Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md) 定义通用实现约束。

共享组件从 [Yak UI Rules](../yak-ops-ui/packages/yak-ui/UI_RULES.md) 进入 [组件文档目录](../yak-ops-ui/packages/yak-ui/docs/)，只加载目标组件契约。API 和工具链变更分别读取 [Service Rules](../yak-ops-ui/SERVICE_RULES.md) 与 [Frontend Tooling](../yak-ops-ui/docs/tooling.md)。

### 验收与发布

[Version Management](release/README.md) 定位对应版本的范围、Readiness、Notes 和 Evidence。

发布状态只在版本材料中维护，本索引不复制版本号、冻结状态或 PASS 结果。普通开发不默认读取这些材料。

## Context Loading

按本次修改范围定位代码、调用方和测试。旧文档用于查找历史背景，不是现行实现的自动权威；存在冲突时核对代码、测试及已接受的 FLIP。

## Engineering Rules

后端开发入口为 [AGENTS](../AGENTS.md) 和 [DEVELOPMENT](../DEVELOPMENT.md)。现有 Architecture / Rules 文件在清理完成前可以按需参考，但普通代码变更不要求同步描述 Markdown。前端规则在本轮保持不变。
