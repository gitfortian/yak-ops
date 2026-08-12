# 数据开发任务草稿与发布版本（阶段 3）

> 状态：Implemented（阶段 3）  
> 范围：打通数据开发 `TaskDraft -> Publish -> TaskRevision`，不实现真实任务执行与 Workflow Task Catalog。

## 1. 目标

阶段 1 已固定 `DevelopmentNode / TaskDraft / TaskRevision / TaskAsset / TaskExecution` 的统一语义，阶段 2 已建立平台级 `TaskPlugin -> ServiceLoader -> TaskPluginRegistry` 骨架。

阶段 3 让数据开发工作台首次拥有服务端任务内容生命周期：

```text
DevelopmentNode
      |
      v
  TaskDraft       可反复保存
      |
      | publish
      v
TaskRevision v1   不可变
      |
      | edit + save + publish
      v
TaskRevision v2   不可变
```

本阶段仍不创建 TaskAsset，也不会让发布任务直接进入 Workflow；Task Catalog 属于后续阶段。

## 2. 数据边界

开发节点继续只承担树元数据：

```text
yak_dev_node
  id / name / type / project_id / directory_id / configured
```

任务正文不回填到 `yak_dev_node`，也不恢复历史的 `yak_dev_sql_task` 专属模型。

新增两张通用表：

```text
yak_dev_task_draft
  node_id                 PK
  task_type
  schema_version
  content
  config_json
  draft_revision
  create_time
  update_time


yak_dev_task_revision
  id                      PK
  node_id
  revision_no             node 内单调递增
  source_draft_revision
  task_type
  schema_version
  content
  config_json
  checksum                SHA-256
  create_time
```

插件私有参数继续放在 `TaskDefinition.configJson` 中。平台只理解统一信封，不增加 `SQL/Shell/Python` 专属任务表。

## 3. Draft 语义

草稿是可变工作副本。

保存接口：

```http
PUT /api/v1/data-development/nodes/{nodeId}/draft
```

请求携带：

```json
{
  "taskType": "SQL",
  "schemaVersion": 1,
  "content": "select 1",
  "configJson": "{\"dataSourceId\":\"123\"}",
  "baseRevision": 3
}
```

`baseRevision` 是乐观锁基线：

```text
客户端 A 读取 Draft #3
客户端 B 读取 Draft #3

A 保存 -> Draft #4
B 仍以 #3 保存 -> HTTP 409
```

因此多浏览器 / 多 Tab 不会静默覆盖更晚的服务端草稿。

未保存过的节点通过读取草稿接口得到一个临时 `draftRevision=0` 的空 Definition；第一次保存成功后进入 `draftRevision=1`。

## 4. Publish 语义

发布接口：

```http
POST /api/v1/data-development/nodes/{nodeId}/publish
```

请求必须携带希望发布的 `draftRevision`。

发布事务执行：

1. 锁定当前 Draft；
2. 检查请求的 Draft Revision 是否仍是当前版本；
3. 根据 DevelopmentNode 校验 taskType 不可漂移；
4. 规范化 `configJson`；
5. 从 `TaskPluginRegistry` 找到对应 Task Plugin；
6. 调用 `TaskPlugin.validate(TaskDefinition)`；
7. 计算 Definition SHA-256；
8. 写入新的不可变 `TaskRevision`。

SQL 阶段 2 插件当前会检查：

- `taskType=SQL`；
- `schemaVersion=1`；
- SQL content 非空。

因此空 SQL 可以先保存为草稿，但不能发布。

## 5. Revision 不可变规则

已发布 Revision 不提供 update/delete API。

例如：

```text
今天统计
  Draft #5 -> Publish -> v1
  Draft #6 -> Publish -> v2
```

后续修改只会形成新的 Draft Revision，再发布出新的 Task Revision。

同一个未变化的 Draft 重复点击发布时，如果最新 Revision 已来自该 Draft 且 checksum 相同，则直接返回现有 Revision，避免因为重复点击产生无意义版本。

## 6. API

```http
GET  /api/v1/data-development/nodes/{nodeId}/draft
PUT  /api/v1/data-development/nodes/{nodeId}/draft
POST /api/v1/data-development/nodes/{nodeId}/publish
GET  /api/v1/data-development/nodes/{nodeId}/revisions
GET  /api/v1/data-development/nodes/{nodeId}/revisions/{revisionNo}
```

版本列表只返回轻量元数据；版本详情才返回完整不可变 `TaskDefinition`。

## 7. 前端工作台

数据开发编辑器由本地 Session 升级为“本地编辑态 + 服务端 Draft”两层：

```text
Monaco / Editor Session
        |
        | 保存草稿
        v
Server TaskDraft
        |
        | 发布版本
        v
Server TaskRevision
```

具体行为：

- 打开节点时读取服务端 Draft；
- 本地已有未保存修改时，不用服务端响应覆盖本地 dirty Session；
- 保存按钮真正调用 Draft API，不再把 localStorage 当成服务端保存；
- SQL 数据源持久化只保存稳定 `dataSourceId`，Database / Schema 仍由数据源管理绑定配置解析；
- SQL 发布前如果当前内容未保存，会先保存 Draft，再发布明确 Draft Revision；
- 关闭 dirty Tab 时，“保存”会真正保存到服务端；
- “不保存”回退到最近一次已保存 Draft 基线；
- 右侧“版本”面板展示发布版本列表，并可查看某个 Revision 的 SQL 内容与 checksum。

当前只有 SQL Task Plugin 可发布。Shell / HTTP / Python 可以保存通用 Draft，但发布入口暂不开放，直到对应 Task Plugin 正式接入。

## 8. 与后续阶段的边界

本阶段明确不做：

- 不执行 SQL；
- 不创建 TaskExecution；
- 不创建 TaskAsset / Task Catalog；
- 发布后暂不自动出现在 Workflow；
- Workflow 暂不引用 Revision；
- 不做 Schedule；
- 不做 Shell/Python Worker。

后续阶段 4 将让 SQL Task Plugin 复用现有 DataSource Plugin，打通数据开发手动运行；阶段 5 再把已发布 Revision 注册为 Task Catalog 资产，使 Workflow 的“数据开发”任务库自动可见。
