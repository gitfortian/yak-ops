# Lineage Architecture

本文件定义 Lineage 的**长期架构 contract**：package 表达职责，稳定 Service 只作为应用入口，专业能力以角色命名，外部技术停在边界。

需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，依赖矩阵看 `DEPENDENCIES.md`，仓库风格看根目录 `CODE_STYLE.md`。

## Design Principles

1. **Domain first.** Asset / Relation / Evidence / Graph 不被 HTTP、MyBatis、Flink/Spark/Hadoop 类型污染。
2. **Stable entry, specialized roles.** Controller 只进入稳定应用 Service；内部用 Analyzer / Collector / Resolver / Converter / Repository 等角色表达职责。
3. **One lineage model.** 不为 Flink、Spark、Hadoop、Dataset、SQL Task 各建一套 Asset/Relation。
4. **External technology stops at boundary.** Parser、SDK、CLI、HTTP client、MyBatis PO 不能穿透到 Core Domain。
5. **Dependency ownership is explicit.** Driver、vendor runtime、UI runtime 与 parser plugin 由实际装配者持有，不挂在 Lineage POM 里“顺便带上”。
6. **Read does not mutate.** Graph/query side 不因读取失败反向改写领域事实。
7. **Structure change is not behavior change.** package、POM、class split 与 REST / DB / Flyway / Domain semantic change 分开评审。
8. **Architecture is executable.** 文档 contract 必须由 architecture/dependency tests 持续保护。

## Package Map

```text
io.yak.ops.business.lineage
├── controller          # HTTP inbound + DTO/VO/converter
├── service             # stable application facades
├── domain              # framework-free core domain / value objects
├── analysis
│   └── sql             # source-neutral SQL projection contract
├── collector           # platform/source ingestion roles when real collectors exist
├── repository          # persistence contracts + adapters
│   └── support         # persistence-only codecs/mapping helpers
├── dao                 # MyBatis primitives / mapper / PO
└── config              # Lineage wiring + narrow external infrastructure corridor
```

`common / helper / utils / base` 不能成为新的业务大桶。根包也不能重新承担兼容层。需要新 top-level package 时，必须先说明它代表的稳定角色并同步更新文档与护栏。

## Application Boundary

当前稳定入口是：

```text
LineageController
    ├── LineageQueryService
    └── LineageWriteService

internal publishers / cross-module adapters
    ├── LineageQueryService
    ├── LineageWriteService
    └── LineageMaintenanceService
```

Query 只负责资产定位、搜索和有界图遍历；Write 负责资产/关系注册与批量写入；Maintenance 负责 evidence replacement、清理和 revision guard。调用方按用例依赖，不再进入一个同时承担读写的宽 Service。

稳定入口使用 `@Service`。内部专业角色优先使用 `@Component`、`@Repository` 或普通对象，不通过增加更多 `*Service` 掩盖职责不清。

## Role Vocabulary

- **Service**：跨 HTTP / module caller 的稳定应用入口，负责事务边界和用例编排；
- **Analyzer**：输入事实并产生分析结果，不拥有持久化 truth；
- **Collector**：从运行平台或外部来源采集 lineage evidence；
- **Resolver**：把外部引用解析为统一领域引用；
- **Mapper / Converter**：边界表示转换，不承载业务状态机；
- **Repository**：领域持久化 contract；
- **RepositoryAdapter**：把领域模型翻译为 DAO/PO；
- **DAO**：数据库读写 primitive，不承载应用编排；
- **Configuration**：装配本模块及窄外部基础设施 corridor，不承载业务用例。

## Domain Boundary

`domain/` 只能依赖 JDK 与必要的通用值类型，不依赖：

```text
Spring MVC / Spring Service
MyBatis / Mapper / PO
Controller DTO / VO
Repository implementation
Flink / Spark / Hadoop SDK
```

Asset / Relation / Graph 位于 `domain/`；稳定应用入口位于 `service/`。Domain 不依赖 Service、Repository、Controller、Analyzer 或平台 SDK。

## Analysis Boundary

共享 SQL projection contract 位于：

```text
analysis/sql/SqlProjectionLineageAnalyzer
```

它只描述 source-neutral 输入输出，不依赖 Spring、Repository、DAO、Data Development 或具体 parser library。

真实实现位于拥有 parser 的邻接上下文：

```text
data-development
└── lineage/analysis/DevelopmentSqlProjectionLineageAnalyzer
        -> SQL lineage parser
        -> shared analysis/sql contract
```

Dataset 通过自身 `LineageProjectionAnalyzerAdapter` 和 `ObjectProvider` 使用该 contract。这样保持：

```text
Dataset -> Lineage contract
Data Development -> Lineage contract
Lineage -X-> Data Development parser implementation
```

## Collector Boundary

当前没有为 Flink / Spark / Hadoop 创建空 Collector 层。只有出现真实平台事件、SDK 或采集协议时，才新增：

```text
collector/<platform>/<RoleImplementation>
```

平台实现必须：

- 产出统一 Asset / Relation evidence；
- 通过稳定写入/维护边界落图；
- 把 SDK、事件结构和连接细节留在 adapter 内；
- 不创建 `flink/domain`、`spark/service` 等平行业务层。

## Persistence Boundary

```text
Service / role component
        ↓
LineageRepository
        ↓
LineageRepositoryAdapter
        ↓
LineageDao
        ↓
Mapper / PO / MyBatis / DB
```

固定规则：

- Repository contract 不暴露 DAO model、Mapper、Controller DTO/VO；
- Service 不直接依赖 DAO / Mapper / PO；
- DAO 不反向依赖 Service / Controller / Repository / Domain orchestration；
- Repository adapter 是 Domain ↔ Persistence 的转换位置；
- `LineageJsonCodec` 等持久化表示 helper 留在 `repository.support`。

## Persistence Configuration Corridor

Lineage 使用共享业务数据库，但不允许 Datasource 模块散落到 DAO、Repository 或 Service：

```text
config/ConditionalOnLineagePersistence
    -> datasource.config.ConditionalOnDataSourceEnabled

config/LineagePersistenceConfiguration
    -> datasource.config.BusinessDatabaseConfiguration
    -> datasource.config.DataSourceProperties
```

`LineageDaoImpl` 只使用 `ConditionalOnLineagePersistence`。因此未来 Datasource 开关或配置方式变化时，适配点留在 `config`，不会扩散到持久化 primitive。

该 corridor 由源码级测试精确匹配文件和 imported type；新增外部 business import 默认失败。

## Maven Assembly Boundary

Lineage Maven module 保持自包含的业务实现，但不承担整个应用的运行时装配：

| Capability | Owner |
| --- | --- |
| HTTP endpoint / validation | Lineage Web + Validation dependencies |
| Transaction annotation | `spring-tx` |
| Mapper / MyBatis API | MyBatis-Plus starter |
| Migration API and Lineage migration bean | `flyway-core` |
| OpenAPI source annotations | `swagger-annotations-jakarta` |
| Shared MyBatis SQL parser runtime | Datasource module |
| MySQL Flyway vendor runtime | Datasource module |
| JDBC database drivers | Explicit application/plugin assembly, not Lineage |
| Swagger UI/runtime | Explicit application assembly, not Lineage |

`yak-ops-business-datasource` 在 Lineage POM 中是 optional：Lineage 编译仍能使用明确的配置 corridor，但下游必须显式选择 Datasource，不会因为使用血缘查询 contract 就被动继承数据库运行时。

该表只定义 Lineage 的所有权边界，不宣称本阶段已经统一仓库内其他业务模块的历史 POM；其他模块若因自身持久化仍直接声明 runtime，后续按各自模块独立治理。

以下依赖不得回到 Lineage POM：

```text
spring-boot-starter-jdbc
springdoc-openapi-starter-webmvc-ui
mybatis-plus-jsqlparser-4.9
flyway-mysql
mysql-connector-j
```

## Root Package Rule

`io.yak.ops.business.lineage` 根包必须保持空白，不放 production Java 类型。公开 contract 也必须归属明确角色包：

```text
domain/*
service/*
analysis/sql/*
```

不保留旧根包兼容 wrapper。仓库级源码扫描会阻止旧 Service、Domain 和 Analyzer import 回流。

## Executable Graph

最终 top-level package 图由测试锁定并保持无环：

```text
controller -> service -> analysis / collector / repository / domain
collector  -> analysis / domain
analysis   -> domain
repository -> dao / domain
dao        -> config
config     -> external persistence corridor only
domain     -> no Lineage application/infrastructure package
```

文档中的允许边不代表必须提前创建实现；例如 Collector 仍然只在真实采集需求出现时新增。

## Change Rules

1. 一个 PR 一个主要边界或行为关注点；
2. package、POM 与 behavior change 尽量分开；
3. 新入口稳定后不长期保留 production 双入口；
4. Maven 子模块拆分必须由真实依赖隔离需求驱动，不为目录美观提前拆 jar；
5. 新 dependency 必须同时符合 `ARCHITECTURE.md + DEPENDENCIES.md`；
6. Collector / Resolver 等角色只因真实用例出现，不提前制造空抽象；
7. dependency guard 的白名单只能因真实架构变化收窄或经评审调整，不能为了让测试通过随意扩大。
