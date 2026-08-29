# Stage 8.1 — Workflow Project Space 完成度审计

> 状态：完成  
> 基线：Stage 7.2 已合并后的 `main`  
> 工作分支：`feat/stage-8-1-workflow-project-space-20260829`

## 1. 阶段目标

Stage 8.1 将 Workflow 从全局共享业务数据收敛为 Project Space 内的复合业务域，并落实以下硬约束：

- Workflow API 必须在可信 Project 上下文中运行；
- Project 归属只能来自服务端 `CurrentProject`，不能信任 URL、DTO 或调用方传入字段；
- Definition、Version、Execution、Schedule、Trigger Ledger、Backfill 的读取和写入必须 fail closed；
- Workflow 节点引用的 TaskAsset 必须与 Workflow 属于同一 Project；
- 后台发现可以跨 Project，但进入普通业务读写前必须恢复持久化的 ProjectContext；
- 进程内 Engine、Metadata 和事件缓存只能用于加速，不能成为授权依据；
- 首发数据库基线直接表达最终 Project 归属，不保留开发期 nullable/Backfill 迁移包袱。

## 2. 数据归属矩阵

| 对象 | 归属类型 | Stage 8.1 约束 |
|---|---|---|
| `yak_workflow_definition` | `PROJECT_ROOT` | 直接保存 `project_id NOT NULL`；CRUD 按当前 Project 过滤 |
| `yak_workflow_version` | `INHERITED` / Runtime Snapshot | 发布版本从 Definition 继承；无父 Workflow 的 Runtime Version 也直接保存 Project |
| `yak_workflow_execution` | `PROJECT_RUNTIME` | 直接保存 Project，支持独立查询、控制和恢复 |
| `yak_workflow_schedule` | `PROJECT_RUNTIME` | Scheduler 独立扫描，必须持久化 Project |
| `yak_workflow_schedule_trigger` | `PROJECT_RUNTIME` | Trigger Ledger 独立恢复和去重，必须持久化 Project |
| `yak_workflow_backfill` | `PROJECT_RUNTIME` | 跨进程恢复，必须持久化 Project |
| `yak_workflow_node_execution` | `INHERITED` | 通过 WorkflowExecution 访问，不重复保存 Project |
| `yak_workflow_node_attempt` | `INHERITED` | 通过 WorkflowExecution 访问，不重复保存 Project |

## 3. HTTP 与前端请求契约

所有 `/api/v1/workflows/**` Controller 使用默认 `@ProjectScope`，其默认模式为 `PROJECT_REQUIRED`。

前端请求规则同步将 `/api/v1/workflows` 标记为 `PROJECT_REQUIRED`。已选择 Project 时统一附加：

```text
X-YAK-SECURITY-PROJECT-ID: <current-project-id>
```

未选择 Project 时不制造默认值，也不使用 `0` fallback，最终由后端统一返回 `PROJECT_REQUIRED`。

## 4. 普通业务访问边界

Workflow DAO / Repository 的普通入口遵循：

```text
CurrentProject
    ↓
Project-qualified root lookup
    ↓
INHERITED child access
    ↓
Project-owned mutation
```

跨 Project 的 ID 访问统一按不可见资源处理，不向调用方泄露目标对象实际属于哪个 Project。

Definition、Version、Execution、Schedule、Trigger 和 Backfill 的新增、更新、删除、详情、列表、运行、重试、补跑与调度操作均通过当前 Project 重新校验。

## 5. Workflow × TaskAsset Source Truth

Workflow 不接收调用方声明的 Task Project。节点绑定以 Task Catalog 中持久化的 Project 投影为 Source Truth，并与当前 Workflow Project 比较。

```text
Current Workflow Project
          ↓
TaskAsset.project_id / revision source truth
          ↓
same-Project validation
          ↓
compile / publish / run
```

`PROJECT_REQUIRED`、`PROJECT_NOT_FOUND` 等 Project 上下文异常不得被降级为普通 `UNKNOWN` 绑定；只有非授权类目录故障和历史失效引用才保留可诊断的 unresolved 展示。

## 6. Durable Recovery

跨 Project 的后台发现仅返回最小调度引用：

```text
(project_id, execution_id)
(project_id, schedule_id)
(project_id, backfill_id)
```

随后按持久化 Project 恢复上下文：

```text
System discovery
    ↓
ProjectContextScope.run(new ProjectContext(projectId, null), ...)
    ↓
Project-scoped DAO / Repository / Runtime
```

该模式覆盖：

- WorkflowExecution 启动恢复；
- Schedule 与 Misfire 对账；
- Trigger Ledger 中间态恢复；
- Backfill 批次恢复。

## 7. Runtime 缓存与异步线程

Workflow Runtime 同时持有 Engine 状态、Metadata、Dispatch、Task Control 等进程内结构。Stage 8.1 明确：这些结构不能证明访问者拥有某个 Execution。

普通 ID 操作在命中缓存前必须满足下列任一可信条件：

1. Execution 在当前请求中创建并绑定到当前 Project；
2. 启动恢复已从持久化 `(project_id, execution_id)` 重新建立绑定；
3. Project-qualified durable metadata 查询证明该 Execution 对当前 Project 可见。

虚拟线程和定时线程不会继承 HTTP ThreadLocal。Runtime 因此为每个活动 Execution 记录已验证的 ProjectContext，并在以下异步边界显式恢复：

- Node Task start；
- Task status polling；
- recovered attempt reconciliation；
- remote cancel；
- workflow timeout scan。

终态清理会同时移除 Execution 的进程内 Project 绑定；后续读取必须再次经过持久化 Project 证明。

## 8. 数据库首发基线

当前项目仍处于 v1 正式发布前。Workflow 的 Project 字段直接合并进 `V1__baseline_workflow.sql`：

- Project Root / Runtime 表为 `project_id BIGINT NOT NULL`；
- Node Execution / Attempt 保持 inherited，不重复保存 Project；
- Project 维度查询索引直接进入首发 Schema；
- 删除开发期 `V2__expand_project_scope.sql`。

该处理服务于 v1 clean install，不将当前开发数据库视为正式跨版本升级契约。

## 9. 验收清单

### 自动化覆盖

- Controller / 前端 Workflow 路由为 `PROJECT_REQUIRED`；
- Definition / Version / Execution 的 Project 绑定和跨 Project 拒绝；
- Workflow × TaskAsset same-Project 校验；
- Runtime 热缓存与冷缓存均不能绕过 Project；
- 缺少 Project 时在读取 Task Snapshot 前 fail closed；
- Task start、polling、timeout 和 cancel 的异步 ProjectContext 传播；
- Schedule payload 持久化 Project 并在 Handler 中恢复；
- Execution / Schedule / Trigger / Backfill 启动恢复按 Project 分组执行；
- 首发 Schema 的 Project ownership contract。

### 建议合并前执行

```bash
./mvnw -pl yak-ops-business/yak-ops-business-workflow -am \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd yak-ops-ui
yarn test projectContext.test.ts --runInBand
```

还应使用真实 MySQL 做一次 clean Flyway 安装，并以 Project A / Project B 验证：列表、详情、控制、SSE、调度、Backfill、重启恢复和跨 Project TaskAsset 引用。

## 10. 不属于 Stage 8.1 的范围

- Project 级 RBAC 覆盖；
- 跨 Project Workflow 编排；
- 多 Master Workflow Runtime 选主；
- v1 正式发布后的数据库跨版本升级契约；
- Quality、Analysis、Dashboard 等后续阶段的 Project 改造。
