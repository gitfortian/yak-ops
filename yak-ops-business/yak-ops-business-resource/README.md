# Yak Ops Resource

文件管理模块负责资源目录、文件元数据、上传下载、在线文本编辑以及存储插件路由。

## 工程分层

```text
Controller -> DTO -> Service -> Domain
           -> Repository -> Adapter -> DAO
           -> BaseMapper -> PO -> MySQL

Domain -> ViewMapper -> VO -> Controller

Service / FileOperations -> Storage Plugin SPI
Domain -> ResourceFileSyncDispatcher -> ResourceFileSyncProvider SPI
```

约束：

- Controller 只通过 `ResourceService` 进入业务链路，不直接依赖 Repository、DAO、Mapper 或 Storage SPI。
- Service、`ResourceServiceSupport` 和 `ResourceFileOperations` 使用 `ResourceNode` 等 Domain，不直接操作 `ResourcePO`。
- `ResourceRepository` 只暴露 Domain；PO 只存在于 Repository Adapter / DAO / Mapper 持久化层。
- DAO 不接收 HTTP DTO，也不返回 HTTP VO；分页使用 DAO 自己的 `PageQuery`。
- 当前资源元数据只有一张表，查询均为单表 CRUD/条件查询，因此继续使用 MyBatis-Plus `BaseMapper` / LambdaWrapper；只有未来真正出现复杂关联 SQL 时才放入 `mapper/resource/*.xml`。
- `MultipartFile`、`InputStream`、Storage Operator 等文件 I/O 对象属于 Service/Storage SPI 边界，不进入 Repository。
- 文件上传/创建仍遵循“先写物理存储，元数据失败则补偿删除”的现有语义。
- 元数据删除成功后，物理对象删除与 ResourceFileSyncProvider 通知继续在事务提交后执行，避免数据库事务被外部存储/Git 同步拖住。

## 持久化

当前文件管理只维护一张业务表：

```text
yak_ops_resource
```

数据库元数据仍是资源树的事实来源；MinIO/HDFS/Local 等 Storage Plugin 只负责文件内容和目录对象。

资源模块复用 `yak.database` 对应的业务 DataSource、MyBatis SessionFactory 和事务管理器，同时继续拥有独立的 `yak_resource_schema_history` Flyway 历史边界。
