# Data Sync Capability

Status: Active

Scope: Workspace 内的离线 / 实时同步定义、发布、调度与执行观察。Data Sync 管产品语义，YakFlow 管执行机制。

## Goal

```text
Task Definition → Published Task → Execution（DataSyncInstance）
                                      └─ Attempt → YakFlow → Source / Sink
```

本文件定义共同的任务与映射边界，并导航到专项契约；不按实现批次维护功能清单。

## Contract Map

| 关注点 | 权威正文 |
| --- | --- |
| 创建、编辑、上线、下线和 definitionVersion | [Task Publication Lifecycle](task-lifecycle.md) |
| Execution / Attempt 身份、状态、取消与指标 | [Execution Retry / Attempt](execution-retry-attempt.md) |
| 离线 Cron、启停、触发校验与恢复 | [Scheduler](scheduler.md) |
| 实时期望状态、进程重启与 CDC state | [Realtime Desired State](realtime-desired-state.md) |
| 类型、split、写入与 checkpoint 机制 | [YakFlow](../yak-flow/README.md) |

## Task Definition

Task 由 Workspace 拥有，名称在 Workspace 内唯一。数据源按 ID 引用，不复制凭证。一个 Task 连接一张 Source 表与一张已存在的 Target 表；`syncType` 创建后不变。

`runtime_config` 按类型解释：OFFLINE 使用 `DataSyncRuntimeConfig`，REALTIME 使用 `DataSyncRealtimeConfig`。Retry Policy 独立保存，创建 Execution 时冻结。`writeMode` 是任务语义，不放进 runtime tuning。

任务不是不完整草稿：保存必须通过后端 Datasource / Catalog / 映射校验；发布和运行还会重新校验外部资源。字段、默认值与请求校验从 DTO / VO 读取，不在这里复制全部参数。

## Datasource Scope and Mapping

Datasource 已绑定的 database / schema 是权威范围；Task 不能覆盖已绑定层级，只有未绑定 schema 可由任务选择。映射预览、保存、发布和执行不得依赖前端校验结果。

Source 字段按不区分大小写的同名规则映射到 Target。所有 Source 字段都要有兼容目标；不支持字段改名、表达式、自定义 SQL 或 Transform。Target 多余字段能否使用默认值等数据库约束，仍由实际写入校验，不能把预览通过当成写入必然成功。

Catalog 字段先投影为 YakColumn，再复用 [JDBC 逻辑兼容规则](../yak-flow/README.md#jdbc-schema-compatibility)。Data Sync 不再定义另一套 `java.sql.Types` 分类或转换规则。

## Offline Execution

当前跨库验收范围为 MySQL → MySQL / PostgreSQL / Oracle。离线运行由 OfflineSyncExecutionPlanner 构造 JDBC Source / Sink，交给本地执行引擎。

| Task writeMode | 执行映射 | 产品前置条件 |
| --- | --- | --- |
| APPEND | APPEND + INSERT | 保留原目标数据；重复运行可能重复写入 |
| OVERWRITE | OVERWRITE + INSERT | 需要 TRUNCATE 权限；清空已提交后失败不能恢复旧数据 |
| UPSERT | APPEND + UPSERT | Target 必须有主键，Source 映射包含全部目标主键字段 |

写入事务、split 与重放限制见 YakFlow；离线无跨进程断点续跑保证。Cron 和 Retry 不改变所选写入语义。

## Realtime Execution

Source 仅支持 MySQL CDC，Target 支持 MySQL / PostgreSQL / Oracle。Source 必须有主键；Target 主键字段集合必须与 Source 在不区分大小写的同名映射下完全一致，顺序可不同，缺失、额外或不同主键均拒绝。

Task 层 `writeMode` 固定 APPEND，运行时使用 JDBC CHANGELOG，并非普通追加 INSERT。读写指标表示变更事件，不等于业务表行数；一次 UPDATE 可以产生 UPDATE_BEFORE 与 UPDATE_AFTER 两个事件。

状态目录、稳定 engine identity、凭证变更风险与续传前提统一见 [Realtime Desired State](realtime-desired-state.md)。不提供可信的最后 checkpoint 时间字段，不推测或伪造该时间。

## MySQL CDC Source Requirements

源端 Binlog、快照与复制权限等前置条件统一见 [MySQL CDC Connector](../yak-flow/README.md#mysql-cdc-connector)。本节保留手工 E2E 使用的入口；Source / Target 主键对应仍按上述产品校验。

## Product Responsibilities

数据集成负责 Task 定义、发布，以及围绕当前 Task 的只读运行详情：任务详情可以查看该 Task 的 Execution 历史、选中的 Execution 状态、指标、Attempt 历史和冻结快照。Task Editor 仍只负责定义，不承载运行态；数据集成详情不提供 Run / Start / Stop。

运维中心负责执行命令、OFFLINE Schedule 启停、REALTIME 运行意图与跨 Task 的运行观察。后端权限与状态校验不能由前端按钮可用性替代；Task 详情和运维中心复用同一 Execution / Attempt 后端事实，不建立第二套运行模型。

历史 Execution 持有自身 syncType、任务版本与脱敏快照。查询历史不依赖当前 Task 发布状态；删除 Task 不删除已有运行历史，但当前 Task 详情需要 Task 本身仍存在。运维可执行任务查询限定已发布任务。

## Current Capability Boundary

当前为单节点、单表同步。未提供自动建表、逻辑建模、DDL 传播、Schema 演进、Transform、多表任务、分布式 Worker / HA / fencing 或 exactly-once。

发布、Retry、Schedule 和启动自动恢复是已有能力，不再列为“后续阶段”。通用 YakFlow checkpoint 跨进程恢复和常驻恢复 watchdog 仍不具备。

## Code and Verification

入口为 [DataSyncServiceImpl](../../../yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/impl/DataSyncServiceImpl.java)；实现职责见 [Data Sync Rules](../../../yak-ops-business/yak-ops-business-data-sync/DATA_SYNC_RULES.md)。

普通后端检查遵循 [Java Rules](../../../JAVA_RULES.md)，专项执行入口为 [Backend Acceptance](../../../.github/workflows/backend-acceptance.yml)。状态机 / Quartz 验证不代替真实 JDBC / CDC 验收，连接器验收也不代替 [产品手工 E2E](../../e2e/data-sync/README.md)。一次执行结果留在相应 PR / CI 或版本证据。
