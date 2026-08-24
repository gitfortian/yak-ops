# Dataset Requirements

本文件定义 Dataset 当前必须保持的行为 contract。领域不变量看 `DOMAIN.md`，代码职责看 `ARCHITECTURE.md`，依赖方向看 `DEPENDENCIES.md`。

## 1. Dataset Identity

- `Dataset` 是稳定业务身份；
- 上游来源变化不能通过覆盖旧 `DatasetVersion` 表达；
- `currentVersionId` 只负责选择当前版本；
- DevelopmentNode 重复保存必须继续绑定同一个 Dataset identity。

## 2. Immutable Version Publication

发布 QUERY_REVISION Dataset 时必须：

1. 来源必须是 Data Development 的 ONLINE SQL TaskAsset；
2. 固定当前 exact TaskRevision id / revisionNo；
3. 发现或接收字段 schema；
4. append 新的 immutable `DatasetVersion`；
5. 更新 `currentVersionId`；
6. 事务提交后请求派生血缘刷新。

同一个 current TaskRevision 的 release publish 保持幂等，不重复追加版本。

## 3. SQL_QUERY Version

Data Development 的 standalone Dataset 必须把：

```text
dataSourceId + SQL + field contract
```

冻结在 DatasetVersion 中。

再次保存相同 datasource / SQL / schema 时只允许更新 Dataset 可变 metadata；来源或 schema 变化时追加新版本。

## 4. Schema Contract

- preview schema 不拥有持久化 fieldId；
- 版本保存时才分配稳定 fieldId；
- compatible physical field 跨版本保持稳定 fieldId；
- 输出字段名必须继续满足当前安全 identifier 约束；
- 重复字段、非法字段、不可识别 result set 必须显式失败；
- schema discovery 只能执行只读 SQL。

## 5. Dataset Lifecycle

- Dataset 支持 ONLINE / OFFLINE；
- status 改变只修改 Dataset identity 状态，不重写任何 DatasetVersion；
- status 改变后请求派生血缘刷新；
- OFFLINE Dataset 不能通过 Query Runtime 查询。

## 6. Query Runtime

Dataset 查询必须绑定 exact DatasetVersion：

- 未指定 versionNo -> 使用当前 `currentVersionId`；
- 指定 versionNo -> 使用该 exact immutable version；
- 不能因为 currentVersion 后续变化而漂移；
- sourceType 必须有显式 `DatasetSourceQueryAdapter`；
- QUERY_REVISION 必须解析版本中固定的 TaskRevision；
- SQL_QUERY 必须使用版本中固定的 dataSourceId + SQL；
- SQL 保持单条、只读、安全字段/filter/limit 规则；
- Query Performance 记录失败或缺失不能改变查询业务真相。

## 7. Analysis Binding

Analysis 绑定 Dataset 时必须：

- Dataset 为 ONLINE；
- Dataset 存在 current version；
- 所有 fieldId 都属于当前 schema；
- 空/未知 fieldId 显式拒绝。

现有 `DatasetService.validateAnalysisBinding(...)` 跨模块 contract 保持兼容。

## 8. Data Development Boundary

`DevelopmentDatasetFacade` 是 Data Development 的稳定入口。

必须保持现有：

- find by developmentNodeId；
- standalone SQL preview / previewQuery / save；
- legacy TaskAsset preview / save；
- public nested record shape。

Data Development 不直接调用 Dataset Repository / DAO / internal Manager。

## 9. Derived Lineage

Dataset lineage 是派生投影，不是 Dataset transaction truth。

必须保持：

```text
Dataset transaction commit
 -> AFTER_COMMIT refresh
 -> REQUIRES_NEW lineage transaction
```

- QUERY_REVISION 解析 exact source revision；
- SQL_QUERY 不依赖 TaskCatalog；
- analyzer 不可用 -> SKIPPED；
- analyzer 异常 -> FAILED evidence；
- parse failure 仍保留 Dataset / DatasetField structural assets；
- Datasource Catalog 仅用于提高 schema/ordinal 解析精度；
- lineage refresh 失败不能回滚已经提交的 Dataset。

## 10. Observability

Query Performance 只保存进程内诊断窗口：

- newest first；
- bounded window；
- 支持 datasetId / queryId 过滤；
- 不成为 Dataset、Version 或 QueryResult 的业务状态。

## 11. Compatibility

纯架构治理不得顺手改变：

- `/api/v1/datasets/**` route；
- HTTP DTO / VO JSON shape；
- `DatasetService` public methods / compatibility records；
- `DatasetQueryService` public API；
- `DevelopmentDatasetFacade` public API；
- Dataset database schema / Flyway；
- DAO / Repository contract；
- persisted sourceType/status values；
- SQL runtime behavior；
- Lineage shared graph API。

如需改变以上 contract，应独立更新 Requirement / Domain / migration，而不是混入治理 PR。