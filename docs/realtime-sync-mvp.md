# 实时同步 MVP

## 架构

实时同步是 `yak-ops-business-sync-realtime` 独立模块，不引用离线同步实体、状态机或表。控制面持久化无密码 `CdcPipelineSpec`，发布后由编译器生成 Runtime Pipeline YAML；浏览器仅访问 Yak Ops。`RealtimeEngineGateway` 隔离 Runtime 契约，HTTP 实现使用 `text/yaml` 调用 validate/deploy，并代理 health、capabilities、status、stop 与有限 tail 日志。

任务使用正交的 `releaseState`（DRAFT/PUBLISHED）、`desiredState`（RUNNING/STOPPED）和 `observedState`。启动事务先执行全局单活动任务检查，再以本地幂等键创建部署快照；超时标为 UNKNOWN 并由定时 Reconciler 查询 `/status` 恢复。Yak Ops 重启后数据库中的 RUNNING 期望状态会继续被对账。At-least-once、Connector 和版本由 `/capabilities` 展示，不落库完整日志或指标。

## 表结构

* `yak_realtime_job_definition`：可编辑无密码 Spec、发布/期望/观测状态、版本与摘要。
* `yak_realtime_job_deployment`：每次启动（包括重启）的版本化脱敏快照、YAML、SHA-256、本地幂等键、Gateway jobId/runtimeVersion 和不确定结果标记。
* `yak_realtime_job_event`：低频生命周期事件；不保存 Runtime 完整日志。

## API

| Method | Path | 用途 |
|---|---|---|
| POST | `/api/v1/realtime-sync/draft` | 新建草稿 |
| PUT/GET/DELETE | `/api/v1/realtime-sync/{id}` | 保存、详情、删除 |
| GET | `/api/v1/realtime-sync` | 分页列表 |
| POST | `/{id}/publish`, `/{id}/validate` | 发布、Runtime 校验 |
| POST | `/{id}/start`, `/{id}/stop`, `/{id}/restart` | 串行生命周期控制 |
| GET | `/{id}/events` | 事件 |
| GET | `/runtime/capabilities`, `/runtime/logs` | 能力与临时日志代理 |

## 页面

“数据集成 → 实时同步”提供任务状态与发布/启停/重启操作、Runtime 能力、事件和临时日志抽屉。由于当前 Runtime 无 checkpoints/metrics API，相关页签明确显示“不支持”，不伪造数据。

## 安全边界

定义只含 `sourceDataSourceRef`/`sinkDataSourceRef` 和连接逻辑字段；模型没有密码字段。Pipeline 密码固定编译为 `${ENV:SOURCE_PASSWORD}` 与 `${ENV:SINK_PASSWORD}`。Controller 不返回部署 YAML，日志不持久化。当前 MVP 的 Runtime 环境必须由部署管理员注入与数据源引用对应的两个密码。
