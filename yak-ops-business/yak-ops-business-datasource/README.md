# Yak Ops Datasource

数据源模块负责数据源注册、连接测试、Catalog 元数据访问和数据源插件编排，并为数据开发、同步和任务等上层模块提供稳定的数据源引用。

## 开发前必读

```text
REQUIREMENTS.md  -> 模块需要什么
DOMAIN.md        -> 实现不能违反什么
REVIEW.md        -> 按什么标准 Review
```

三份文档只维护当前有效规则，历史设计过程看 Issue / PR / Git。

## 核心领域模型

```text
DataSourceDefinition  (Aggregate Root，当前保留历史命名)
├── identity / name / dbType / environment
├── ConnectionProfile
│   ├── jdbcUrl
│   ├── normalizedJson
│   └── originalJson
├── connectionStatus
├── remark
└── timestamps
```

关键业务规则：

- 数据源类型创建后不可修改。
- 新建数据源连接状态为 `UNKNOWN`。
- 修改连接配置后，旧连接测试结果失效并回到 `UNKNOWN`。
- 已保存数据源连接测试成功进入 `CONNECTED`，失败进入 `DISCONNECTED`。
- 未保存配置的连接测试不产生持久化状态。
- Secret、完整连接 JSON 和可能携带凭据的 JDBC URL 不得通过 `toString()` 或普通日志输出。

详细规则见 `DOMAIN.md`。

## 当前工程分层

```text
Controller
    ↓
Application Service
    ↓
Domain
    ↓
Repository Port
    ↓
Repository Adapter
    ↓
DAO / Mapper / PO / MySQL
```

当前 Phase 1 为保持兼容，Application Service 仍直接编排 Datasource Plugin SPI：

```text
Application Service -> Datasource Plugin SPI -> Plugin Implementation
```

Plugin Gateway / Adapter 隔离属于后续阶段，不在 Phase 1 中以 Big-Bang 方式同时修改 Business、REST、DB 和 Plugin SPI。

## 分层约束

- Controller 只通过 Service 进入业务链路，不直接依赖 Repository、DAO、Mapper 或插件实现。
- Service 使用 `DataSourceDefinition` 等 Domain，不直接操作 MyBatis PO。
- Repository 接口只暴露 Domain；PO 仅存在于 Adapter / DAO / Mapper 持久化层。
- Core Domain 不依赖 Spring、MyBatis、HTTP DTO / VO / PO 或 Datasource Plugin SPI。
- 分页遵循 `DAO IPage<PO> -> Adapter PageData<Domain> -> Service PagingData<VO>`。
- DAO 不接收 HTTP DTO，也不返回 HTTP VO；DAO 查询使用自己的 Query / Row 投影。
- 普通单表 CRUD 使用 MyBatis-Plus；统计等自定义 SQL 放在 `mapper/datasource/*.xml`。
- HTTP 详情必须通过 `DataSourceViewMapper` 和 `DataSourceSecretCodec` 做连接地址与连接 JSON 脱敏。
- Catalog 读取使用 Repository 加载 Domain，再交给 Datasource Plugin SPI；Catalog 不直接访问 DAO / PO。

## 持久化

当前数据源管理继续维护现有业务表：

```text
yak_ops_data_source
```

Phase 1 不修改现有表结构和 Flyway 历史。`jdbc_url / connection_params / original_json / conn_status` 是持久化投影，不是新的领域边界。

业务模块共享 `yak.database` 对应的 `yakBusinessDataSource`、MyBatis SessionFactory 和事务管理器；数据源模块继续拥有自己的 Flyway migration location/history 边界。

## 后续领域 Gap

当前明确留给后续阶段处理：

```text
Datasource Plugin Gateway / Adapter
Catalog Domain Model
SQL Execution Domain Model
Plugin Capability / Descriptor
Plugin API VO dependency
Map-based Catalog protocol typing
Aggregate physical naming cleanup
```
