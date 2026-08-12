# Workflow 与 Task Plugin 集成设计

> 本文已按统一 Task Plugin 核心模型修订。Workflow 不再拥有一套独立的任务插件执行模型，而是通过 Task Catalog 引用已发布任务，并在运行时统一进入 Task Runtime。

## 设计目标

Yak Ops 借鉴 DolphinScheduler 的任务插件职责拆分，但保持当前架构轻量，不在 Workflow 模块中复制 SQL/Shell/Python 执行逻辑，也不在当前阶段引入 Master、Worker 或注册中心。

统一关系：

```text
数据开发 / 数据同步 / 数据质量
          |
          | publish
          v
      Task Catalog
          |
          | assetId + revisionId
          v
       Workflow
          |
          v
     Task Runtime
          |
          v
  TaskPluginRegistry
```

核心模型与生命周期以 [`task-plugin-core-model.md`](./task-plugin-core-model.md) 为准。

## Workflow 的职责

Workflow 只负责：

- 编排任务依赖关系；
- 保存任务资产与发布版本引用；
- 传递工作流上下文、参数和触发信息；
- 展示运行实例、节点状态和日志入口；
- 将节点执行请求交给统一 Task Runtime。

Workflow 不负责：

- 解析 SQL 数据源参数；
- 启动 Shell/Python 进程；
- 根据数据库类型选择 JDBC 实现；
- 直接发现或加载具体 Task Plugin；
- 保存一份独立于 TaskRevision 的任务正文副本。

## 任务选择器

工作流设计器左侧的“数据同步 / 数据开发 / 数据质量”统一查询 Task Catalog：

```text
DATA_INTEGRATION
DATA_DEVELOPMENT
DATA_QUALITY
```

只有 `ONLINE` 资产允许新增引用。

拖入画布时，Workflow Node 保存：

```text
taskAssetId
taskRevisionId
```

而不是只保存业务模块自己的 `sourceId`，也不复制 SQL/Shell 内容。

## 版本规则

假设数据开发任务“今天统计”已经发布 v1：

```text
今天统计
  v1 <- Workflow A
```

之后开发人员修改并发布 v2：

```text
今天统计
  v1 <- Workflow A（保持不变）
  v2 <- current
```

Workflow A 可以提示存在 v2，但必须由用户显式升级引用。生产 Workflow 不允许因为上游任务再次发布而静默改变行为。

## 运行时规则

未来统一由 Task Runtime 处理：

```text
Manual Run ----+
Workflow Run --+--> Task Runtime --> TaskPlugin
Schedule Run --+
```

Workflow 只提供触发上下文，例如：

```text
workflowDefinitionId
workflowInstanceId
workflowNodeKey
taskAssetId
taskRevisionId
parameters
```

Task Runtime 再构造插件执行上下文、运行记录、取消信号、超时和日志能力。

## 与旧方案的差异

旧设计曾考虑由 Workflow 直接维护 `WorkflowTaskPluginFactory / WorkflowTaskExecutorRegistry / WorkflowTaskExecutor`。该方式会让 Workflow 成为任务插件的唯一宿主，后续数据开发手动运行和独立调度还需要再实现一套入口。

现在统一调整为：

```text
Workflow -> Task Runtime -> Task Plugin
```

因此后续不再新增 Workflow 专属 Task Plugin SPI。插件能力属于平台级 Task Plugin，供数据开发、Workflow、Schedule 共同复用。

## 实施顺序

1. 固定 Task 核心模型与版本语义；
2. 建立平台级 `yak-ops-plugin-task` 插件骨架；
3. 完成数据开发 Draft/Revision/Publish；
4. SQL 插件打通手动运行；
5. Task Catalog 接入 Workflow 任务选择器；
6. Workflow 固定绑定 TaskRevision；
7. 三种触发方式统一进入 Task Runtime。
