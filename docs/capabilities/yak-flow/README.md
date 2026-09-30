# YakFlow Capability

Status: Active

Scope: 批流共用的数据平面、本地执行、JDBC 与 MySQL CDC 连接器行为。

## Goal

当前跨库验收覆盖 MySQL 批量读取及 MySQL CDC → MySQL / PostgreSQL / Oracle。能力不拥有产品 Task、发布、Cron、Retry Policy、Execution / Attempt 表或 HTTP API；这些属于 Data Sync。

## Core Model

```text
Source → YakRow + RowKind → bounded Channel → Sink
```

同一个 Source / Sink 协议同时用于 BOUNDED 和 CONTINUOUS_UNBOUNDED。RowKind 为 INSERT、UPDATE_BEFORE、UPDATE_AFTER、DELETE，不新增第二套 Batch / CDC 行协议。

YakTypeKind 表示逻辑类型族，YakDataType 表示完整类型，YakBasicType 不承载 DECIMAL 参数，YakDecimalType 拥有 precision / scale。YakColumn 保存列名、nullable 与当前 String / Binary capacity 等元信息。未知 DECIMAL 元信息保留未知，不编造精度；当前不支持 ARRAY / MAP / ROW 等复合类型。

## Local Execution Engine

有界 Source 使用一个串行 Enumerator 向 1～16 个 Reader 分配 split；各 Reader 拥有自己的读取连接，汇入一个有限容量 Channel，由单个 SinkWriter 串行消费。split 数量与 Reader 并行度是不同概念。

连续 Source 使用单 Reader。有限数据自然完成，连续任务持续到取消或失败；短暂无数据不是成功结束。取消 / 失败应中断阻塞工作，Reader.poll 必须周期返回，以观察 checkpoint 和取消请求。

Runtime readRows 在 Source batch 成功进入 Channel 后增加；writeRows 在 SinkWriter.write 返回后增加。计数在同一 LocalExecution 内单调，但不证明数据库事务已经提交。产品跨 Attempt 的展示语义由 Data Sync 定义。

## JDBC Schema Compatibility

Catalog 元信息先由 JdbcSchemaMapper 转换为 YakColumn，JdbcSchemaCompatibility 只接收逻辑类型；不支持映射的 JDBC 类型不能静默判为兼容。

兼容边界：同类型按已知容量判断；允许整数扩宽、容量足够的整数 → DECIMAL，以及 FLOAT → DOUBLE；拒绝整数缩窄和未经声明的跨族转换。

String / Binary 在双方长度已知时要求 Target 不小于 Source。DECIMAL 检查小数位和整数位容量，不只比较总 precision；元信息未知时不能伪造已知容量保证。具体算法见 [JdbcSchemaCompatibility](../../../yak-flow/yak-flow-connector-jdbc/src/main/java/io/yak/ops/flow/connector/jdbc/JdbcSchemaCompatibility.java)，产品层不复制该算法。

## JDBC Batch Connector

复用 Datasource 的 DataSourceConnection / DataSourceTablePath、Driver 隔离和 JDBC endpoint runtime。读取使用 forward-only cursor 和有限批次，只读取声明列并保持顺序，不把整表放进内存。

支持显式单整数主键范围 split，以及 splitSize 动态规划。动态规划读取 MIN / MAX / COUNT，以 ceil(rowCount / splitSize) 请求范围数量，再生成不重叠的闭区间。无合格单整数主键或记录数不超过 splitSize 时使用整表 split；超过 10,000 个动态 split 拒绝并要求增大 splitSize。

splitSize 是目标行数，不保证均匀分布。每个 split 是独立读取事务，不提供所有 split 共用的一致性快照；没有倾斜采样或分布分析。

保存方式与行写入方式分离：

| Save / Write | 语义 |
| --- | --- |
| APPEND + INSERT | 保留既有目标数据，分批插入 |
| OVERWRITE + INSERT | Writer 打开时先执行并提交 TRUNCATE，再加载；后续失败不恢复旧数据 |
| APPEND + UPSERT | 按 Target 主键使用数据库原生 UPSERT，更新非主键列 |
| APPEND + CHANGELOG | 按变更事件执行主键写入 / 删除 |

拒绝 OVERWRITE + UPSERT / CHANGELOG。目标表必须存在，TRUNCATE 失败不静默退化为 DELETE；写入 / flush 失败回滚尚未提交的数据，不能撤销已提交批次。MySQL 使用 ON DUPLICATE KEY UPDATE、PostgreSQL 使用 ON CONFLICT、Oracle 使用 MERGE；只有主键的表可以采用匹配时无操作路径。

标识符必须由方言引用，不接受任意用户 SQL。不提供自动建表、DDL 传播或 Schema 演进。

## MySQL CDC Connector

Debezium Engine 为连接器私有实现，依赖版本由 BOM 维护。snapshot.mode=initial 先执行初始快照，再消费 binlog。

Source 必须启用 binary log、ROW format、FULL row image，账号具备快照及复制所需权限：SELECT、RELOAD、SHOW DATABASES、REPLICATION SLAVE、REPLICATION CLIENT；特定部署可能还需快照锁权限。Source 要有主键，Data Sync 的严格 Source / Target 主键对应由其产品契约约束。

JDBC CHANGELOG 将 INSERT / UPDATE_AFTER 按主键写入，UPDATE_BEFORE / DELETE 按主键删除。这里的主键重放容忍不等于端到端 exactly-once，也不保证多语句更新在外部观察中原子可见。

offsets / schema history 位于调用方提供的状态目录，内容由连接器管理；新 Source 可使用同一目录和稳定 engine identity 续传，不能复活旧 LocalExecution。

## Checkpoint Boundary

单 Reader 路径先捕获 Source state，再入队 barrier；Sink 消费 barrier 时 flush 之前的全部数据，之后才完成 checkpoint。MySQL Reader 仅在 notifyCheckpointComplete 后确认 Debezium 记录，不能在记录刚进入 Channel 时提前确认。

LocalExecution 只保留当前执行内最新 checkpoint，不提供通用 CheckpointState 的跨进程序列化 / 恢复。多 Reader 的有界执行拒绝显式 checkpoint，不能假装多份 Reader 状态已经一致聚合。

连续 Source 自动触发 checkpoint，默认间隔 10 秒，可由调用方配置。连接器的文件状态是独立持久化边界；Sink 提交后、offset 持久化前崩溃仍可能重放，所以当前语义为 at-least-once。

## Verification

[Backend Acceptance](../../../.github/workflows/backend-acceptance.yml) 是 JDBC / CDC 专项执行入口。OfflineSyncJdbcAcceptanceIT 覆盖真实 MySQL Source 到三类 Target 及最终指标；MySqlCdcIntegrationIT 覆盖初始快照、INSERT / UPDATE / DELETE、checkpoint、offset 文件、取消和同目录重启续传。

续传用例在完成 checkpoint 后再写一条 Source 记录，检查第二次运行对应事件计数，防止把重新全量读取当成续传。它验证指定测试场景，不扩大为 exactly-once 保证；实际运行证据不保存在当前能力正文。

## Explicit Non-Goals

不提供 Transform、任意 SQL、Flink 集成、分布式执行 / Worker / fencing、产品调度与持久化、通用 checkpoint 跨进程恢复或 exactly-once 协调。

## Dependency Boundary

API 只依赖 JDK；Runtime 依赖 API；Connector 依赖 API 并复用允许的 Datasource 连接边界。第三方协议类型不越过 Connector，产品状态不进入 Runtime。实现与包组织遵循 [YakFlow Rules](../../../yak-flow/YAK_FLOW_RULES.md)。
