# Data Sync Schema / Logical Table Contract

Status: Active — v1.2 Contract

Scope:

- Data Sync 产品级 Logical Table
- Logical Column / Primary Key / Schema Version
- Datasource Catalog → Logical Model 边界
- Logical Model → YakFlow Runtime Schema 边界
- 后续持久化、Target Planning、Auto Create Table 的稳定前置契约

## 1. Goal

v1.2 开始把“数据库里发现的一张物理表”与“Yak Ops 自己理解并保存的一张逻辑表”分开。

目标不是把 Source 的 CREATE TABLE SQL 保存下来，而是形成一个跨数据库、可以长期复用的产品级 Schema Model：

~~~text
Datasource Catalog
Physical Metadata
        ↓
LogicalTable
Product Metadata
        ↓
YakTableSchema
Runtime Contract
        ↓
Target Table Plan
Physical DDL
~~~

这层模型是后续 Auto Create Table、Schema Compatibility、Logical Modeling 与数仓建模的共同基础。

## 2. Ownership

### Datasource

Datasource 只负责发现真实数据库当前状态：

~~~text
database / schema / table
column name
native typeName
jdbcType
size / scale
nullable
ordinalPosition
primaryKey
remarks
~~~

Datasource Catalog 是实时物理元数据读取，不是 Yak Ops 的长期逻辑模型。

### Data Sync

Data Sync 拥有产品级 LogicalTable / LogicalColumn。

Logical Table 可以在后续 PR 中持久化为 Workspace-scoped 产品资源；它必须在没有实时数据库连接时仍能独立描述 Schema。

### YakFlow

YakFlow 继续只拥有数据平面运行契约：YakDataType、YakColumn、YakTableSchema。

YakFlow 不拥有 Logical Table 的产品 ID、Workspace、Comment、Schema Version、Catalog 来源关系或建模生命周期。

### JDBC Connector / Target Planner

数据库原生目标类型与 DDL 由后续 Target Planner / JDBC Dialect 根据 Logical Model 生成。

禁止把 Source 的 MySQL / PostgreSQL / Oracle 原生类型名称直接复制成跨数据库目标建表规则。

## 3. Logical Table Contract

当前内存契约：

~~~text
LogicalTable
├── name
├── comment
├── schemaVersion
├── columns[]
└── primaryKeys[]
~~~

LogicalTable 本身是不可变 Schema Definition，不携带 DAO Entity 字段。

后续持久化资源至少需要在产品层拥有：

~~~text
logicalTableId
workspaceId
name
comment
schemaVersion
columns
primaryKeys
created / updated audit fields
~~~

logicalTableId / workspaceId / audit fields 属于资源与持久化层，不进入 YakFlow Runtime Schema。

Logical Table 必须是自包含 Schema Snapshot。即使原 Datasource 或 Source Table 后续不可用，已经保存的 Logical Table 仍能描述当时确认的结构。

## 4. Logical Column Contract

当前字段契约：

~~~text
LogicalColumn
├── name
├── dataType: YakDataType
├── nullable
├── length
└── comment
~~~

规则：

- 字段名称保留来源标识符原始大小写，不在 Logical Model 中统一 lower-case。
- 类型唯一标准是 YakFlow YakDataType，不再维护第二套 Data Sync 类型枚举。
- STRING / BINARY 可以保存 length；未知容量使用 null。
- DECIMAL 的 precision / scale 由 YakDecimalType 自己承载，不塞进通用 length。
- comment 是产品元数据，不进入 Runtime Schema。
- Primary Key 在 Table 层使用有序字段名列表表达，从而保留复合主键顺序。
- Primary Key 必须引用当前 Logical Table 中真实存在的字段。

当前 Logical Type 家族继续复用：

~~~text
BOOLEAN
TINYINT
SMALLINT
INTEGER
BIGINT
FLOAT
DOUBLE
DECIMAL
STRING
BINARY
DATE
TIME
TIMESTAMP
TIMESTAMP_WITH_TIME_ZONE
~~~

本 PR 不引入 ARRAY / MAP / ROW 等复合类型。

## 5. What Does Not Belong in Logical Table

以下内容不是 Logical Schema 的 canonical identity：

- Source JDBC typeName。
- java.sql.Types 编码。
- MySQL / PostgreSQL / Oracle 专属 DDL 片段。
- Target database / schema / table physical path。
- Target dialect。
- Runtime connection / credential。
- Debezium offset / schema history。
- Execution / Attempt identity。

Source 的 typeName / jdbcType / size / scale 可以用于导入、兼容分析和诊断，但不能成为跨数据库逻辑模型的唯一类型表示。

当前 Datasource Catalog 没有稳定暴露 Column Default / Index / Foreign Key，因此 PR1 不把这些字段伪造成 Logical Table Contract。后续只有在来源、语义和跨数据库规则明确后才能扩展。

## 6. Schema Version Contract

schemaVersion 从 1 开始，并与以下版本完全独立：

~~~text
Product Version        1.2.0
Task definitionVersion
LogicalTable schemaVersion
~~~

它们不能互相驱动。

Logical Table 的结构发生变化时推进 schemaVersion：

- Column add / remove。
- Column order 改变。
- Column name 改变。
- Logical Type / precision / scale / length 改变。
- nullable 改变。
- Primary Key 集合或顺序改变。

仅修改表 / 字段 comment 等展示元数据不推进 schemaVersion。

后续如果提供 Catalog Refresh：

~~~text
Saved LogicalTable
        +
Fresh Datasource Catalog
        ↓
Schema Diff
        ↓
No structural change
→ keep schemaVersion

Structural change accepted by user/product rule
→ schemaVersion + 1
~~~

Catalog Refresh 不能静默覆盖一个已保存且被 Task / Model 引用的 Schema Snapshot。

## 7. Runtime Projection

产品 Logical Table 可以投影成 YakFlow Runtime Schema：

~~~text
LogicalTable
        ↓
YakTableSchema
~~~

必须保留 Column order、Column name、YakDataType、nullable、STRING / BINARY capacity 与 Primary Key order。

不会进入 Runtime：Logical Table name、comment、schemaVersion、product resource identity、workspace、audit fields。

当前代码通过 LogicalTable#toRuntimeSchema() 固定这条最小投影规则。

## 8. Source Import Boundary

PR2 已实现 Source Metadata Introspection + Logical Type Normalization：

~~~text
Datasource Catalog exact table metadata
        +
Datasource Catalog columns
        ↓
SourceTableIntrospector
        ↓
LogicalTableNormalizer
        ↓
JdbcSchemaMapper / YakDataType
        ↓
LogicalTable
~~~

Datasource Catalog 现在提供精确 `findTable(DataSourceTablePath)`，DataSourceService 内部提供 `queryCatalogTable(...)`，Data Sync 不再通过 table keyword 搜索结果猜测表备注或对象身份。

字段归一规则：

- 字段按 Catalog `ordinalPosition` 恢复稳定顺序。
- JDBC `typeName / jdbcType / size / scale` 只作为物理输入。
- Logical Type 唯一通过现有 `JdbcSchemaMapper` 归一，不在 Data Sync 再写 JDBC type switch。
- STRING / BINARY capacity 继续由 YakColumn length 表达。
- DECIMAL precision / scale 继续由 YakDecimalType 表达。
- table remarks / column remarks 作为初始 comment，空白备注归一为 null。
- Catalog 额外保留 JDBC `KEY_SEQ` 为 `primaryKeyPosition`，复合主键按 KEY_SEQ 顺序进入 LogicalTable，不按字段物理顺序猜测。

禁止：

- 按数据库产品名维护新的逻辑类型枚举。
- 用 Source native typeName 直接决定 Target DDL。
- Source introspection 读取或暴露 Runtime credential。
- Catalog import 阶段直接持久化或执行 Target DDL。

当前没有新增 Logical Table HTTP API。现有 Catalog Column 响应增加 `primaryKeyPosition`，用于保留复合主键顺序。

## 9. Target Planning Boundary

后续 Target Table Planner 接收的是 Logical Table，而不是 Source physical column list：

~~~text
LogicalTable
      +
Target Datasource Type
      +
Target Table Path
        ↓
TargetTablePlan
        ↓
Dialect DDL
~~~

Target Plan 才负责 Logical Type → Target Native Type、Identifier quote、physical path、Primary Key DDL、CREATE TABLE SQL 与兼容性诊断。

Logical Table 不保存最终 Target DDL，避免把产品元数据绑死到某个数据库方言。

## 10. Persistence Boundary

PR1 只建立 Contract，不新增 Flyway Migration。

后续持久化实现必须满足：

- Workspace-scoped。
- Logical Table 使用稳定产品 ID。
- Schema 内容是 Yak Ops Source of Truth，不依赖查询时重新连接 Source。
- 不建立到 Datasource 的数据库物理外键；Datasource 删除不能让历史 Logical Schema 失去可读性。
- Catalog 来源关系如果保存，只能作为 provenance / refresh hint，不作为 Logical Schema 唯一身份。
- 不把 connectionJson、password、JDBC URL、Token 等 Secret 写入 Logical Table。
- 已冻结到 Task definitionSnapshot 的 Schema 不能因为 Logical Table 后续修改而改变历史 Execution。

具体 Entity / Mapper / Migration 由后续实现 PR 决定，不能在 Contract 阶段提前锁死物理表设计。

## 11. Current v1.2 Boundary

PR1 完成后只有 Schema / Logical Table Contract。

~~~text
Logical Table Persistence = NOT IMPLEMENTED
Source Metadata Introspection = IMPLEMENTED
Logical Type Normalization = IMPLEMENTED
Catalog Refresh / Diff = NOT IMPLEMENTED
Target Table Planner = NOT IMPLEMENTED
Auto Create Table = NOT IMPLEMENTED
Schema Preview UI = NOT IMPLEMENTED
DDL Sync = NOT IMPLEMENTED
Automatic Schema Evolution = NOT IMPLEMENTED
Multi-table Task = NOT IMPLEMENTED
~~~

现有 OFFLINE / REALTIME Task 仍要求目标表预先存在，本 PR 不改变任何运行行为。

## 12. Verification

Contract test 至少验证：

- Logical Table 可以稳定投影为 YakTableSchema。
- Composite Primary Key 顺序保持。
- Primary Key 不能引用不存在字段。
- Logical Column 名称不能重复。
- capacity 只允许出现在 STRING / BINARY。

PR2 通过 LogicalTableNormalizer / SourceTableIntrospector Contract Test 验证 Catalog Import 的内存归一行为；跨库 Target Type Mapping、持久化与 Auto Create Table 验收分别属于后续 PR。
