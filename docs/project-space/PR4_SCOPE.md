# Project Space PR4：数据生产链迁移

> 状态：Draft rollout baseline

PR4 将以下数据生产链统一接入 PR2 的可信 `CurrentProject`：

```text
Data Development
  -> Task Catalog
  -> Offline Sync
  -> Realtime Sync
  -> Workflow
```

## 目标

- 项目业务根对象直接保存 `project_id`；
- 异步执行、调度和部署记录直接保存 `project_id`；
- 从属版本、节点、事件通过父聚合继承项目；
- 创建归属只读取服务端可信项目上下文；
- 列表、详情、编辑、删除、发布、执行和运行记录查询均按项目隔离；
- Workflow、同步任务和开发任务不得引用其他项目的业务资源；
- 继续采用 `Expand -> Backfill -> Contract`，本阶段不直接收紧为 `NOT NULL`。

## 本 PR 数据边界

| 模块 | PROJECT_ROOT / PROJECT_PROJECTION | PROJECT_RUNTIME | INHERITED / GLOBAL |
|---|---|---|---|
| Data Development | directory、node | task execution、lineage outbox | draft、revision |
| Task Catalog | task asset（来源投影） | - | revision snapshot |
| Offline Sync | job definition | batch execution、job execution | execution event |
| Realtime Sync | job definition | deployment | definition version、job event；compute environment 与 runtime lease 保持 GLOBAL |
| Workflow | workflow definition | schedule、execution、schedule trigger、backfill | version、node execution、attempt |

## 兼容策略

- Controller 在 Expand 阶段使用 `PROJECT_OPTIONAL`；
- 有合法 Project Header 时，所有项目化查询和写入使用可信项目条件；
- 无 Header 时仅保留历史兼容读取，禁止继续创建无归属的新根对象；
- 前端只为已经完成后端隔离的 API 家族开启 Project Header；
- 项目空间菜单和右上角切换器继续隐藏；
- 完成历史数据回填与双项目验收后，再切换 `PROJECT_REQUIRED`。

## 验收门槛

1. Project A 无法通过修改 ID 读取、编辑、运行或删除 Project B 的任务；
2. Project A 的同步任务不能引用 Project B 的 DataSource；
3. Project A 的 Workflow 不能编排 Project B 的 TaskAsset；
4. Scheduler、重试、部署 reconcile 和后台 outbox 消费能从持久化记录恢复 `project_id`；
5. 历史记录全部回填默认空间，项目化根表和运行态表不存在空项目；
6. 同一名称可以在不同项目内创建，同一项目内仍保持唯一。
