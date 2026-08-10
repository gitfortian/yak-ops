# Yak Ops Datasource

数据源模块负责数据源注册、连接测试、Catalog 元数据访问和数据源插件配置。

## 工程分层

```text
Controller -> DTO -> Service -> Domain
           -> Repository -> Adapter -> DAO
           -> BaseMapper / Mapper XML -> PO -> MySQL

Domain -> View Mapper -> VO -> Controller

Service -> Datasource Plugin SPI
Catalog Service -> Domain Repository -> Datasource Plugin SPI
```

约束：

- Controller 只通过 Service 进入业务链路，不直接依赖 Repository、DAO、Mapper 或插件实现。
- Service 与 Catalog Service 使用 `DataSourceDefinition` 等 Domain，不直接操作 MyBatis PO。
- Repository 接口只暴露 Domain；PO 仅存在于 Adapter / DAO / Mapper 持久化层。
- 分页遵循 `DAO IPage<PO> -> Adapter PageData<Domain> -> Service PagingData<VO>`；不再创建数据源模块私有 `DataSourcePage`。
- HTTP 分页继续保持 `bizData + pagination`，第一阶段不要求前端迁移。
- DAO 不接收 HTTP DTO，也不返回 HTTP VO；DAO 查询使用自己的 Query/Row 投影。
- 普通单表 CRUD 使用 MyBatis-Plus；统计等自定义 SQL 放在 `mapper/datasource/*.xml`。
- `connectionParams`、`originalJson` 可以存在于内部 Domain，用于插件执行和密钥复用，但不得直接作为 HTTP 响应输出。
- HTTP 详情必须通过 `DataSourceViewMapper` 和 `DataSourceSecretCodec` 做连接地址与连接 JSON 脱敏。
- Catalog 读取使用 Repository 加载 Domain，再交给 Datasource Plugin SPI；Catalog 不直接访问 DAO/PO。

## 持久化

当前数据源管理只维护一张业务表：

```text
yak_ops_data_source
```

业务模块共享 `yak.database` 对应的 `yakBusinessDataSource`、MyBatis SessionFactory 和事务管理器；数据源模块继续拥有自己的 Flyway migration location/history 边界。
