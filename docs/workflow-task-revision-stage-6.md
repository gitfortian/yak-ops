# Stage 6 — Workflow 固定 TaskAsset + immutable TaskRevision

## 目标

Stage 5 已经把数据开发发布任务提升为平台级 `TaskAsset`，Workflow 可以发现这些资产。

Stage 6 解决的是另一个问题：**Workflow 引用任务时必须固定到一个不可变版本，而不是每次运行都读取 TaskAsset 的 latest revision。**

目标语义：

```text
Data Development
  今天统计 v1
       ↓ publish
TaskAsset #12
  currentRevision = v1
       ↓ add to Workflow
Workflow Node
  taskAssetId = 12
  taskRevisionId = 101
  taskRevisionNo = 1
       ↓
Workflow v1 发布并固定 TaskRevision v1

随后数据开发发布 今天统计 v2：

TaskAsset #12
  currentRevision = v2

Workflow 草稿/已发布版本仍固定 v1
  ↓ 用户显式点击“升级到 v2”
Workflow draft -> v2
  ↓ 重新发布 Workflow
Workflow v2 -> TaskRevision v2
```

## 设计原则

### 1. TaskAsset 是逻辑身份，TaskRevision 是执行身份

`TaskAsset` 表示“今天统计”这个长期存在的任务资产；`TaskRevision` 表示它某次发布产生的不可变执行版本。

Workflow 节点正式保存：

```text
taskId          = task-asset:12
taskAssetId     = 12
taskRevisionId  = 101
taskRevisionNo  = 1
```

其中 `taskId` 是兼容现有 Workflow / runtime 结构的稳定逻辑 ID，真正保证可复现的是 `taskAssetId + taskRevisionId`。

### 2. 普通保存绝不静默升级

当 Workflow 已经固定 `v1`，即使 Task Catalog 当前已经是 `v2`：

```text
保存工作流
移动节点
修改重试次数
修改输入映射
重新打开编辑器
```

都不会把绑定从 v1 改成 v2。

只有显式升级动作才能推进 Revision。

### 3. 升级先改草稿，再重新发布

显式升级接口：

```http
POST /api/v1/workflows/definitions/{workflowId}/nodes/{nodeId}/upgrade-task-revision
```

该操作只修改 Workflow draft：

```text
v1 -> v2
```

不会修改当前 active WorkflowVersion。

因此线上工作流仍然使用旧版本，直到用户重新发布 Workflow。

### 4. 已发布 WorkflowVersion 自包含不可变执行快照

Workflow 发布时，从固定 Revision 解析：

```text
TaskDefinition
checksum
task type
configJson
revisionNo
```

并继续写入现有 `TaskVersionSnapshot`。

正式运行读取 WorkflowVersion 的 snapshot，而不是重新查询 TaskAsset.currentRevision。

因此：

```text
TaskAsset currentRevision: v1 -> v2 -> v3
```

不会改变已经发布的 Workflow v1。

## Source-neutral Revision Resolver

Task Catalog 新增扩展边界：

```text
TaskAssetRevisionProvider
        ↓
TaskAssetSource
        ↓
resolve(sourceRef, revisionId)
        ↓
TaskSourceRevision
```

当前接入：

```text
DATA_DEVELOPMENT
  -> DataDevelopmentTaskRevisionProvider
  -> DevelopmentTaskRevisionRepository.findById(...)
```

Task Catalog 本身不依赖 Data Development 的 Repository/表结构。

未来可以继续增加：

```text
DATA_INTEGRATION -> DataIntegrationTaskRevisionProvider
DATA_QUALITY     -> DataQualityTaskRevisionProvider
INTERNAL         -> InternalTaskRevisionProvider
```

而 Workflow 不需要感知具体来源。

## Task Catalog API

Stage 6 增加资产详情：

```http
GET /api/v1/task-catalog/assets/{assetId}
```

用于首次固定 Revision 和刷新最新版本信息。

TaskAsset / Revision 的 Long ID 对前端统一按字符串输出，避免 JavaScript Number 精度问题。

## Workflow API 模型

Workflow Node 返回：

```json
{
  "id": "task-node-1",
  "taskId": "task-asset:12",
  "taskAssetId": "12",
  "taskRevisionId": "101",
  "taskRevisionNo": 1,
  "taskAssetName": "今天统计",
  "taskType": "SQL",
  "taskAssetStatus": "ONLINE",
  "latestTaskRevisionId": "102",
  "latestTaskRevisionNo": 2,
  "taskRevisionUpdateAvailable": true
}
```

这里区分两组信息：

```text
固定状态：taskRevisionId / taskRevisionNo
发现状态：latestTaskRevisionId / latestTaskRevisionNo
```

发现新版本不会改变固定状态。

## Workflow UI

### 任务选择器

Stage 5 的数据开发资产由“只展示、不可选择”改为可添加。

列表继续显示：

```text
今天统计                         已发布 v2
```

新节点第一次保存时固定选择时对应的 published revision。

### 节点 Inspector

TaskAsset 节点新增“任务版本”区域：

```text
任务版本

当前固定    v1
资产最新    v2
资产状态    已上线

[升级到 v2]
```

没有新版本时：

```text
当前固定    v2
资产最新    v2
当前固定版本已是最新版本
```

升级成功后提示重新发布 Workflow 后才会影响 active version。

## Workflow JSON 持久化

Stage 6 不新增 Workflow DB migration。

现有 Workflow definition/version 已将 `WorkflowNodeSpec` 作为 JSON 持久化，因此新增：

```text
taskAssetId
taskRevisionId
taskRevisionNo
```

会自然进入 draft/version JSON。

Legacy Workflow 节点仍然保持：

```text
taskId = 原 TaskRegistry ID
```

并继续走原有兼容路径。

## 生命周期

### Data Development 发布新版本

只推进：

```text
TaskAsset.currentRevision
```

不扫描、不修改 Workflow。

### TaskAsset OFFLINE

新的 Workflow discovery 不再展示该资产。

已有 Workflow 的固定 Revision 信息仍然保留，因此历史 WorkflowVersion 可继续保持可解释、可审计。

Stage 6 禁止把 OFFLINE 资产显式升级到最新版本。

### TaskAsset 重命名

Workflow 的逻辑绑定不变：

```text
taskAssetId + taskRevisionId
```

界面展示名称可读取 Catalog 当前 metadata，不影响执行版本。

## 测试重点

新增测试覆盖以下不变量：

1. Workflow draft 固定 TaskAsset v1；
2. Workflow 发布 v1 后保存不可变 snapshot；
3. TaskAsset currentRevision 推进到 v2 时，Workflow 仍固定 v1；
4. API 返回 `updateAvailable=true`；
5. 用户显式升级后只改变 draft，不改变 active WorkflowVersion；
6. 重新发布后产生 Workflow v2，并固定 TaskRevision v2。

## Runtime 边界

Stage 6 完成的是 **Workflow binding / version reproducibility**，不是新的执行后端。

Stage 4 已经支持 Data Development 中 SQL 手动真实执行，但当前 Workflow `TaskExecutionGateway` 尚未注册 SQL `TaskExecutor`。

因此本阶段可以完成：

```text
SQL TaskAsset
  -> 添加 Workflow
  -> 固定 SQL TaskRevision
  -> 保存/发布 WorkflowVersion
  -> 检测/显式升级 Revision
```

但 SQL 节点进入 Workflow test-run / run 时，仍需要后续阶段把 SQL runtime 正式接入 `TaskExecutionGateway`。

本阶段不通过临时旁路执行 SQL，避免 Workflow 和 Data Development 出现两套执行语义。

## 后续建议

下一阶段可以聚焦统一执行层：

```text
TaskVersionSnapshot
        ↓
TaskExecutionGateway
        ↓
SQL TaskExecutor
        ↓
DataSource execution / session
        ↓
TaskExecution status + output
```

在这一层完成后，`TaskAsset -> immutable Revision -> WorkflowVersion -> Runtime` 就形成完整闭环。
