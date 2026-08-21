# 实时同步 MVP

## 运行边界

控制面位于独立模块 `yak-ops-business-sync-realtime`，不复用离线同步任务实体、执行表或状态机。执行面固定使用 [yak-flink-cdc-connectors](https://github.com/weifuwan/yak-flink-cdc-connectors) 中的 `yak-cdc-runtime`：Java 11、Flink 1.20.5、Flink CDC 3.6.0，以及 `yak-jdbc` MySQL/PostgreSQL Sink。

Yak Ops 仅调用 Gateway，不嵌入 Flink/Flink CDC 依赖，浏览器也不直接访问 Flink REST。当前 Gateway 契约只有：

| Method | Runtime path | Yak Ops 用途 |
|---|---|---|
| GET | `/health` | 健康检查 |
| GET | `/capabilities` | 版本、Source/Sink 与交付语义 |
| POST | `/validate` | 校验临时 Pipeline YAML |
| POST | `/deploy` | 提交唯一活动任务 |
| GET | `/status` | 对账 `NONE/RUNNING/TERMINATED` |
| POST | `/stop` | 停止当前任务；请求 body 为空 |
| GET | `/logs` | 临时读取脱敏日志 |

当前 Runtime 没有 Checkpoint、Metrics、认证和每任务凭据下发接口。Yak Ops 不伪造这些能力。

Flink CDC 3.6 的 Pipeline YAML 只接受 PipelineOptions；Checkpoint 与 Flink restart-strategy 属于 Runtime/Flink 配置。当前 Gateway 没有每任务覆盖接口，因此编译器不会生成无效的 `checkpoint-interval` 或 `restart-strategy` YAML。逻辑 Spec 预留这两项，界面明确标为 Runtime 固定配置；要让它们真正按任务生效，必须先扩展 Runtime 契约和 capability manifest。

## Spec 与凭据

`CdcPipelineSpec` 只保存数据源 ID、表匹配/映射、主键声明、启动模式、Schema Evolution 和运行参数。host、port、JDBC URL、用户名与密码均不属于任务定义。

发布、校验和启动前，`RealtimeDataSourceResolver` 会重新读取最新数据源定义，并按角色执行独立校验：

- Source 仅允许 MySQL，对应 Runtime `mysql` Source；
- Sink 仅允许 MySQL/PostgreSQL，对应 `yak-jdbc:mysql` 或 `yak-jdbc:postgres`；
- 能力必须以 Runtime `/capabilities` 返回值为准；
- 一期强制 At-least-once、主键声明和 strict Replay Safety。

编译出的 Pipeline YAML 仅在调用栈中短暂存在，密码字段为 `${ENV:SOURCE_PASSWORD}` 和 `${ENV:SINK_PASSWORD}`（名称可配置），不会落库或返回前端。部署环境必须把与本次数据源一致的密码预置到 Runtime 环境。由于当前 Gateway 不支持安全的动态凭据下发，Yak Ops 无法把数据源密码自动注入 Runtime；扩展 Gateway 前不得用明文 YAML 或日志传递密码。

因此启动被安全阻止：当前 Gateway 只检查 `${ENV:...}` 形式，但 Flink CDC 3.6 的 Pipeline 解析器不会自动展开该值。Draft PR 合并前必须先由 Runtime 增加按部署 Secret 引用/绑定及内存内解析能力，并在 capability manifest 中明确声明；不能依赖明文临时文件或假定上游会展开占位符。

## 状态与恢复

任务使用三个正交状态轴：

- `releaseState`：`DRAFT/PUBLISHED`；
- `desiredState`：`RUNNING/STOPPED`；
- `observedState`：`STOPPED/STARTING/RUNNING/STOPPING/FAILED/UNKNOWN/CONFLICT`。

显式状态机拒绝非法迁移。启动先创建无密码部署快照和本地幂等键，再调用 Runtime；提交超时会保留 `UNKNOWN`，同一幂等键不会再次提交。Reconciler 周期查询 `/status`，可在 Yak Ops 重启后恢复 RUNNING/STOPPED/FAILED/CONFLICT。Runtime 丢失任务时不会自动重新部署，以避免 At-least-once 场景重复回放。

## 数据库

| 表 | 内容 |
|---|---|
| `yak_realtime_job_definition` | 无密码逻辑 Spec、三个状态轴、当前/发布版本、摘要 |
| `yak_realtime_job_deployment` | definitionVersion、无密码快照、Spec 摘要、SHA-256、幂等键、engineJobId、runtimeRevision、结果不确定标记 |
| `yak_realtime_job_event` | 低频状态迁移与审计事件 |

V2 迁移会清空旧实现曾保存的 `pipeline_yaml`，后续部署始终写入 `NULL`。高频指标和完整日志不进入业务库。

## Yak Ops API

| Method | Path | 用途 |
|---|---|---|
| POST | `/api/v1/realtime-sync/draft` | 新建草稿 |
| PUT | `/api/v1/realtime-sync/{id}` | 保存新定义版本 |
| GET | `/api/v1/realtime-sync/{id}` | 详情与最近部署 |
| GET | `/api/v1/realtime-sync` | 服务端分页/搜索 |
| DELETE | `/api/v1/realtime-sync/{id}` | 删除已停止任务 |
| POST | `/{id}/validate` | 按最新数据源与 Runtime 能力校验 |
| POST | `/{id}/publish` | 校验并发布当前版本 |
| POST | `/{id}/start` | 使用 `Idempotency-Key` 启动 |
| POST | `/{id}/stop` | 停止 |
| POST | `/{id}/restart` | 等待停止后重新部署 |
| GET | `/{id}/events` | 状态与审计事件 |
| GET | `/{id}/logs` | 临时 Runtime 日志 |
| GET | `/runtime/capabilities` | Runtime 能力代理 |

接口分别受 `task:realtime:read/create/update/delete/execute` 权限保护。

## 页面

“数据集成 → 实时同步”包括任务分页、Source/Sink/表规则/运行参数向导、发布与启停、部署摘要、Runtime 版本、状态事件和临时日志。Checkpoint/Metrics 页签依据后端能力明确显示不可用。
