# Data Service Project Governance

Data Service 的 Project Space 边界只作用于 **Yak Ops Management Plane**。对外 Runtime Invocation 保持稳定全局 URL，不要求调用方理解 Yak Project Header。

## Two planes

```text
Yak Ops User
   │
   ├── Project membership
   ├── RBAC permission
   ▼
Management Plane
/api/v1/data-service/**
   │
   ▼
Project-owned DataServiceDefinition

External Client
   │
   ├── global runtime path
   └── NONE / X-API-Key
   ▼
Invocation Plane
/api/v1/data-service/runtime/{servicePath}
```

Management Plane 是 `PROJECT_REQUIRED`。Invocation Plane 明确不是 Project API；前端 Project request rule 也通过最长前缀把 `/api/v1/data-service/runtime/**` 保持为 `LEGACY_GLOBAL`，不发送 `X-YAK-SECURITY-PROJECT-ID`。

## Project-owned truth

`DataServiceDefinition.projectId` 是稳定服务身份的一部分。管理查询、更新、删除、发布来源解析、Runtime Policy、API Key 管理、文档和观测都必须先通过当前 Project 找到父 Data Service。

调用日志持久化 `project_id` 快照：

```text
Invocation
  -> global path resolves DataServiceDefinition(projectId)
  -> audit record snapshots projectId
  -> later management log/overview reads filter projectId
```

这样即使 Data Service 后续被删除，历史调用记录仍然有明确 Project 归属。

API Key 和 Documentation 不重复保存 `project_id`；它们通过父 `api_id` 的 Project ownership 受控。Key update/rotate/delete 必须先确认父 API 属于 CurrentProject，不能只凭 keyId/apiId 修改。

## Global runtime path

外部 Invocation URL 没有 Project namespace，因此 `DataServiceSettings.path` 继续保持**全局唯一**：

```text
Project A /orders
Project B /orders   <- rejected
```

Repository 只有一个明确的全局读 corridor：

```text
DataServiceReader.requireByPath
  -> DataServiceRepository.findByRuntimePath
```

它只服务外部 Invocation。其他 `findById / findBySource / findAll / save / delete` 都必须受 CurrentProject 约束。

## Permissions

| Permission | Capability |
| --- | --- |
| `data-service:read` | API 集市、详情、契约文档、OpenAPI |
| `data-service:publish` | 来源查询、首次发布、重新发布、发布状态 |
| `data-service:manage` | 服务侧设置、启停、可编辑文档 |
| `data-service:delete` | 删除非 source-managed 服务 |
| `data-service:access` | Auth Mode 与 API Key 生命周期 |
| `data-service:runtime` | Runtime 状态/策略与 Console Test |
| `data-service:observe` | Overview 与调用日志 |

权限和 Project membership 是两个独立 gate，两者都必须满足。

`runtime` 是动作能力，Data Service Debug UI 还需要服务目录/文档，因此页面角色建议组合 `READ + RUNTIME`。观测角色通常组合 `READ + OBSERVE`，但 Overview/Logs 后端动作只要求 `OBSERVE`。

Source-managed Data Service 仍保留 owner-context 边界：Data Development 来源的定义发布/上线/下线必须由 Data Development authoring context 发起；Data Service 通用管理 API 不能绕过这个约束。

## Legacy cutover

Flyway V12 只扩展 nullable `project_id` 列；Project ID 来自 Yak Security，不能在静态 migration 中硬编码。

ApplicationReady backfill 顺序：

1. 确保 compatibility default Project；
2. `DATA_DEVELOPMENT_DATA_SERVICE` 来源优先从 `yak_dev_node.project_id` 推断 API ownership；
3. 发现已有 API ownership 与 owning Data Development node 冲突则启动失败；
4. 其余 legacy API 归 compatibility default Project；
5. Call Log 优先继承现存 API Project；
6. 已删除 API 的孤立历史日志归 compatibility default Project；
7. 断言 API / Call Log 不再存在 NULL project。

这保证 Data Development Stage 2 已建立的 authoring Project 与 Data Service Runtime projection 不被二次切割。

## Guardrails

- Management Controller 必须 `PROJECT_REQUIRED`。
- Public `DataServiceInvocationController` 禁止 `@ProjectScope` / Yak `@RequiresPermission`。
- Project header 不能作为外部 Invocation 的授权选择器。
- `findByRuntimePath` 不能被管理页面复用。
- Management Repository query/update/delete 必须包含 CurrentProject predicate。
- Overview / logs 只能聚合当前 Project 的 invocation evidence。
- Runtime path 继续全局唯一，直到未来显式设计 Project-aware public namespace。
