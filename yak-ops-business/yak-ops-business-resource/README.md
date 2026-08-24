# Yak Ops Resource

文件资源模块负责 Resource Namespace、文件内容、存储插件路由、运行时资源解析，以及资源变更后的外部同步传播。

当前代码按业务子系统组织，不再以通用 `service/impl` 作为业务入口：

```text
Controller
   -> Namespace Manager / Reader
   -> Content Manager / Reader
   -> Storage Reader

Namespace / Content
   -> Resource Domain
   -> ResourceRepository -> DAO -> MyBatis
   -> ResourceStorageGateway -> StorageOperator SPI
   -> ResourceChangeDispatcher -> ResourceFileSyncProvider SPI

ResourceResolver SPI
   -> Resolution Adapter
   -> Namespace / Content Read Side
```

## 运行真相

数据库中的 `yak_ops_resource` 是 Resource identity / namespace / current revision metadata 的事实来源；Local / MinIO / HDFS 等 Storage Plugin 只拥有物理文件字节。

`version` 表示当前 Resource Revision，用于变更 fencing 与运行时版本校验。当前模块不是历史版本存储系统，因此 `version = 5` 不表示仍可读取 1~4 版本内容。

## 一致性语义

当前重构保持既有行为：

- 创建目录、上传和在线创建：先写物理存储，再写元数据；元数据失败时补偿删除刚创建的物理对象。
- Move：先移动物理对象，再批量更新当前资源及后代路径；元数据更新失败时尝试把 Storage 路径回滚。
- 当前不支持跨 Storage Move。
- Delete：先提交元数据删除；物理对象删除与外部 Resource Sync 在事务提交后执行，不让外部 I/O 延长数据库事务。
- Resource Sync 是提交后的 best-effort propagation，不是 Resource Namespace 的第二状态来源。
- Resource Resolver 会把资源物化到临时目录并校验 SHA-256 checksum；指定 version 时只允许读取当前匹配 revision。

## 持久化

当前只维护一张业务表：

```text
yak_ops_resource
```

资源模块复用 `yak.database` 对应的业务 DataSource、MyBatis SessionFactory 和事务管理器，并继续拥有独立的 `yak_resource_schema_history` Flyway 历史边界。
