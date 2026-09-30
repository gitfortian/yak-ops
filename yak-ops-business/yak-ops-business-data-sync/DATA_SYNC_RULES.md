# Data Sync Rules

Status: Active

Scope: `yak-ops-business/yak-ops-business-data-sync/**` 及 Common / DAO 中对应的 datasync 契约和持久化实现。

## Applicable Rules

遵循 [Architecture](../../ARCHITECTURE.md)、[Java Rules](../../JAVA_RULES.md)、[Business Rules](../BUSINESS_RULES.md)。DTO / VO、Entity、Repository 与 Migration 分别遵循 Common / DAO 的就近规则，不在本文件复制。

产品行为只由 [Data Sync Capability](../../docs/capabilities/data-sync/README.md) 及其发布、执行重试、调度、实时恢复专题定义。本文件管实现职责，不再维护另一份阶段、状态或字段清单。

## Business Boundary

唯一稳定产品 Service 为 `DataSyncService / impl.DataSyncServiceImpl`。它编排 Task、发布、Schedule 与执行请求；YakFlow 只负责数据平面执行。

Datasource 校验、Catalog 和运行连接必须经过 DataSourceService。禁止跨到 Datasource DAO / Plugin Registry，禁止复制连接模型或在 Business 维护 JDBC type-family 转换。

## Execution Package Organization

`io.yak.ops.business.datasync.execution` 根包不放具体实现，按已有职责聚合：

| 包 | 职责 |
| --- | --- |
| executor | OFFLINE / REALTIME Runtime 提交、单次尝试、指标 flush 和终态处理 |
| planning | 冻结快照 + Catalog / Connection 到执行计划；共用 SchemaResolver |
| lifecycle | Execution / Attempt 状态迁移、进程内注册、取消和启动 LOST 处理 |
| realtime | CDC state identity、目录和 MySQL serverId 资源 |

executor 可以依赖 planning / lifecycle / realtime；lifecycle 和 realtime 不反向依赖 executor。不要复制 offline/planning 与 realtime/planning 层级、逐类建包或重建 Manager / Coordinator。

测试按对应职责组织；直接代码入口见 [execution 目录](src/main/java/io/yak/ops/business/datasync/execution)。

## Task and Mapping Implementation

- 通过 WorkspaceContext.requireWorkspaceId 获取产品请求范围；所有 Task / Schedule / Execution / Attempt 访问必须带 workspaceId，不能仅凭资源 ID 查询。
- Task 保存、发布、运行均按 [Task / Mapping Contract](../../docs/capabilities/data-sync/README.md#datasource-scope-and-mapping) 做服务端校验；前端值只在未绑定范围内参与选择。
- 复用 DataSyncCatalogColumns 处理同名字段 / 主键集合，复用 DataSyncSchemaResolver 与 JdbcSchemaMapper 投影，再交 JdbcSchemaCompatibility 判断；不在 Service 再写一套类型能力表。
- 版本比较集中在可执行定义的规范化比较，不每次 PUT 加一；包括 retryPolicy，具体语义见 [Version Contract](../../docs/capabilities/data-sync/task-lifecycle.md#definition-version-contract)。
- CRUD / 查询、发布与运行的副作用必须分开；不能在保存或发布方法里偷偷启动 YakFlow。

## Execution and Metrics Implementation

新 Execution 先保存脱敏快照，再提交 Runtime；有事务时在提交后派发，不让执行依赖尚未提交的产品记录。

OFFLINE / REALTIME 共用 DataSyncAttemptLifecycle。状态变更使用 Repository 的预期状态条件更新，竞争失败不能当作已成功转移；取消和重试不得复活终态根记录。

活动集合、backoff、root trigger 及指标唯一语义见 [Execution Contract](../../docs/capabilities/data-sync/execution-retry-attempt.md)。实现必须按 Runtime / Attempt 区分计数与 Execution 当前尝试镜像；不得对根记录使用跨 Attempt 的“只增不减”修补或累计总量。

本地注册表仅持有活动 Runtime 的取消引用；启动将旧进程活动记录标记 LOST，不能把数据库 RUNNING 当作仍有本地执行对象。连续 Source 意外完成不标记 SUCCEEDED。

## Scheduler and Recovery Implementation

- `scheduler` 下只定义框架无关 Contract；org.quartz.*、JobFactory 和最终启动装配归 Boot。
- onFire 重读数据库，不能直接信任 Quartz JobData 的授权或状态；Schedule / Runtime 边界见 [Scheduler](../../docs/capabilities/data-sync/scheduler.md)。
- Runtime 更新安排在提交后，但不能把它描述为与 DB 原子提交。不得用 Quartz Refire 实现产品 Retry。
- 启动恢复遍历跨 Workspace 任务时，显式绑定所属 Workspace，并在 finally 清理，不能泄漏上下文到下一任务。
- REALTIME 目录和 serverId 生命周期留在 realtime；offset / schema-history 内容留在连接器。恢复条件与限制见 [Realtime Contract](../../docs/capabilities/data-sync/realtime-desired-state.md)。

## Secret and Persistence Boundary

运行连接只在可信执行规划阶段解析。Task / Execution / Attempt / Retry Policy、HTTP 响应、日志和异常不得泄漏原始凭证。ExecutionPlan 为内存对象，不能持久化或序列化到响应；快照不得包含连接 JSON、密码、SSH 私钥、Token、offset 结构或运行时租约。

Data Sync Entity / Mapper / Repository 和全部 Schema 归 DAO；不重复定义 DAO 模型、不建立数据库物理外键。历史 Execution 自存 syncType 等身份，不通过 join 当前 Task 推断历史；Task 删除不级联删除历史记录。

迁移遵循 [Flyway Rules](../../yak-ops-dao/FLYWAY_RULES.md)。V1 是冻结基线，现有 Schedule / Attempt / Desired State 已由向前迁移定义，不能按旧阶段计划重复建表或回改已冻结 SQL。

## Verification

普通编译 / 格式 / verify 使用 [Java Rules](../../JAVA_RULES.md)。专项执行与路径过滤由 [Backend Acceptance](../../.github/workflows/backend-acceptance.yml) 定义，不在这里复制命令。

现有验证职责：业务状态测试检查身份、状态与命令；Quartz 测试检查 Cron / Time Zone / Misfire / next-fire；JDBC / CDC 真实数据库验收检查数据与恢复；[手工 E2E](../../docs/e2e/data-sync/README.md) 检查产品完整链路。

不得以跳过测试、仅 H2、业务替身或单次连接成功替代对应验收证据。验证结果只对实际提交与场景有效，记录在 PR / CI 或版本 Evidence，不追加阶段完成清单。
