# Task Plugin 核心模型设计

> 状态：Accepted（阶段 1）  
> 范围：统一 Yak Ops 数据开发、工作流与调度之间的任务语义；本阶段不实现具体任务插件与执行器。

## 1. 背景

Yak Ops 的数据开发页面已经具备 SQL、Shell 等开发节点的前端雏形，工作流也已经存在任务编排入口。后续如果分别在数据开发、工作流、调度中实现 SQL/Shell/Python 执行逻辑，会产生重复实现和行为不一致。

因此统一采用“任务资产 + 不可变发布版本 + Task Plugin + Task Runtime”的方向：

- 数据开发负责开发、保存草稿、调试和发布；
- Task Catalog 负责暴露可被编排的已发布任务资产；
- Workflow 只引用已发布任务版本，不复制任务内容；
- Schedule/Workflow/手动运行最终都进入统一 Task Runtime；
- Task Plugin 只负责某一种任务能力如何校验和执行。

该设计借鉴 DolphinScheduler 的 Task Plugin 职责拆分，以及 Chat2DB 的 SPI/Plugin 隔离方式，但保持 Yak Ops 当前 `business / spi / plugins` 的模块边界，不引入独立 Master/Worker、注册中心或动态前端插件系统。

## 2. 核心概念

### 2.1 DevelopmentNode

数据开发目录中的设计态节点，负责“它是谁、叫什么、放在哪里”。

典型字段：`id / name / type / projectId / directoryId`。

约束：

- `DevelopmentNode` 不是生产执行的事实来源；
- 节点名称、目录位置可以修改；
- SQL/Shell/Python 的具体内容不继续扩散为 `yak_dev_sql_task / yak_dev_shell_task / ...` 多套业务模型。

### 2.2 TaskDraft

任务的可变工作副本。

- 保存只更新 Draft；
- Draft 可以被反复修改；
- Draft 不进入 Task Catalog；
- Draft 不允许被 Workflow 新建引用；
- 发布时由 Draft 固化出新的不可变 TaskRevision。

### 2.3 TaskDefinition

插件无关的任务定义载荷，也是后续 Task Plugin 与 Task Runtime 之间的稳定输入模型：

```text
TaskDefinition
  taskType       SQL / SHELL / PYTHON / HTTP / ...
  schemaVersion  插件配置结构版本
  content        SQL、脚本等主内容，可为空
  configJson     插件私有配置 JSON
```

本阶段在 `yak-ops-spi` 中提供最小不可变模型 `TaskDefinition`，但不提供插件执行接口。

### 2.4 TaskRevision

一次“发布”产生的不可变快照。

```text
TaskRevision
  revisionId
  assetId
  revisionNo
  definition
  checksum
  createdBy
  createdTime
```

约束：

- `revisionNo` 对同一 TaskAsset 单调递增；
- 已发布 Revision 不原地修改；
- Workflow/调度运行必须最终解析到明确的 Revision；
- 新发布 v2 不得让已绑定 v1 的生产 Workflow 静默升级。

### 2.5 TaskAsset

平台统一“任务资产目录”中的稳定身份，解决“上线一个数据开发任务后自动出现在 Workflow 中”的问题。

```text
TaskAsset
  assetId
  name
  source
  sourceId
  taskType
  currentRevisionId
  status
```

`source` 第一阶段统一为：

- `DATA_DEVELOPMENT`
- `DATA_INTEGRATION`
- `DATA_QUALITY`

Workflow 左侧的“数据同步 / 数据开发 / 数据质量”只是 Task Catalog 的不同 source 视图，不直接查询各业务模块表。

资产状态：

- `ONLINE`：允许被新的 Workflow 引用；
- `OFFLINE`：不允许新增引用，但历史 Revision 与已有引用保留；
- `DISABLED`：运行层禁止执行，用于强制停用。

### 2.6 TaskExecution

一次实际运行记录。它必须引用明确的 TaskRevision，而不是读取“最新草稿”。

触发来源统一为：

- `MANUAL`：数据开发/任务详情手动运行；
- `WORKFLOW`：工作流节点运行；
- `SCHEDULE`：调度直接触发任务或后续统一调度入口。

基础状态语义统一为：`PENDING / RUNNING / SUCCESS / FAILED / CANCELLED / TIMEOUT`。

本阶段只固定语义，不落运行表、不实现执行器。

### 2.7 TaskPlugin

TaskPlugin 是“某种任务怎么校验、怎么执行”的能力插件，不是用户创建的任务本身。

未来计划：

```text
TaskPlugin
  type()
  descriptor()
  validate(TaskDefinition)
  createExecutor(TaskDefinition, TaskExecutionContext)
```

本阶段不创建 `TaskPlugin`/`TaskExecutor` 接口；它们属于阶段 2 的 `yak-ops-plugin-task-api` 骨架。

## 3. 生命周期

```text
DevelopmentNode
      |
      v
  TaskDraft  <------ 保存 / 编辑 / 调试
      |
      | publish
      v
TaskRevision v1  ---- immutable
      |
      v
 TaskAsset ONLINE
      |
      v
   Task Catalog
      |
      +---------- Workflow 选择/拖入
      |                 |
      |                 v
      |       assetId + revisionId(v1)
      |
      +---------- Manual / Schedule
                        |
                        v
                  TaskExecution
                        |
                        v
                   Task Runtime
                        |
                        v
                    TaskPlugin
```

再次修改并发布：

```text
Draft -> Revision v2

Workflow A -> v1（保持不变，可提示有新版本）
Workflow B -> v2（新建时使用当前发布版本）
```

## 4. Workflow 集成规则

1. Workflow 任务选择器查询 Task Catalog，不直接查询 `yak_dev_node`、同步任务表或质量任务表。
2. 拖入任务时保存 `taskAssetId + taskRevisionId`。
3. Workflow 发布后，引用的 Revision 必须保持稳定。
4. TaskAsset 发布新版本后，可提示 Workflow “有新版本可升级”，但不得自动替换。
5. `OFFLINE` 仅阻止新增引用；已有 Workflow 仍保留其历史 Revision。
6. `DISABLED` 在 Task Runtime 层拒绝新的执行。
7. Workflow 不直接依赖 SQL/Shell/Python 具体插件实现。

## 5. 模块与依赖方向

目标依赖方向：

```text
yak-ops-ui
   |
   +--> business-data-development
   +--> business-workflow
   +--> future task-catalog / task-runtime
                         |
                         v
                 task-plugin-api
                         |
          +--------------+--------------+
          v              v              v
       sql plugin     shell plugin    python plugin
          |
          v
  datasource plugin api
```

约束：

- `business-*` 不依赖具体任务插件；
- Workflow 不自建一套 SQL/Shell 执行器；
- SQL Task Plugin 复用现有 DataSource Plugin，不按数据库重复建设 SQL Task Plugin；
- 插件私有参数进入 `TaskDefinition.configJson`，平台只负责版本、资产、权限、审计与运行编排；
- TaskDraft/TaskAsset/TaskRevision/TaskExecution 属于业务/运行时聚合，不塞进具体插件模块。

## 6. 阶段 1 代码边界

本阶段只在 `yak-ops-spi.task.model` 放置后续跨模块会共同使用的最小词汇：

- `TaskDefinition`
- `TaskRevisionRef`
- `TaskAssetSource`
- `TaskAssetStatus`
- `TaskExecutionTrigger`
- `TaskExecutionStatus`

这些类型不是数据库实体，也不代表已经实现 Task Runtime。

本阶段明确不做：

- 不新增数据库表或 Flyway Migration；
- 不新增 `yak-ops-plugin-task-*` Maven 模块；
- 不实现 `TaskPluginRegistry` / `ServiceLoader`；
- 不实现 SQL/Shell/Python 执行；
- 不修改 Workflow 运行逻辑；
- 不修改数据开发现有 API 行为。

## 7. 后续阶段

1. 阶段 2：建立 `yak-ops-plugin-task-api / sql / all` 骨架和 `TaskPluginRegistry`。
2. 阶段 3：实现 Draft / Revision / Publish 后端与版本模型。
3. 阶段 4：实现 SQL Task Plugin，复用 DataSource Plugin，打通手动运行。
4. 阶段 5：建立 Task Catalog，发布任务自动进入 Workflow 任务库。
5. 阶段 6：Workflow 节点绑定 `TaskAsset + TaskRevision`。
6. 阶段 7：Manual/Workflow/Schedule 统一进入 Task Runtime。
7. 阶段 8：扩展 Shell/Python/HTTP 与可选 Worker/Container Execution Backend。
