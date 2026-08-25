# Lineage Architecture

本文件定义 Lineage 的**长期架构 contract**：package 表达职责，稳定 Service 只作为应用入口，专业能力以角色命名，外部技术停在边界。

需求看 `REQUIREMENTS.md`，领域硬规则看 `DOMAIN.md`，依赖矩阵看 `DEPENDENCIES.md`，仓库风格看根目录 `CODE_STYLE.md`。

## Design Principles

1. **Domain first.** Asset / Relation / Evidence / Graph 不被 HTTP、MyBatis、Flink/Spark/Hadoop 类型污染。
2. **Stable entry, specialized roles.** Controller 只进入稳定应用 Service；内部用 Analyzer / Collector / Resolver / Mapper / Repository 等角色表达职责。
3. **One lineage model.** 不为 Flink、Spark、Hadoop、Dataset、SQL Task 各建一套 Asset/Relation。
4. **External technology stops at boundary.** Parser、SDK、CLI、HTTP client、MyBatis PO 不能穿透到 Core Domain。
5. **Read does not mutate.** Graph/query side 不因读取失败反向改写领域事实。
6. **Structure change is not behavior change.** package move、class split 与 REST / DB / Flyway / Domain semantic change 分开评审。
7. **Architecture is executable.** 文档 contract 必须由 architecture/dependency tests 持续保护。

## Target Package Map

```text
io.yak.ops.business.lineage
├── controller          # HTTP inbound + DTO/VO/converter
├── service             # stable application facades
├── domain              # framework-free core domain / value objects
├── analysis            # source-neutral analysis contracts + implementations
├── collector           # platform/source ingestion roles
├── repository          # persistence contracts + adapters
│   └── support         # persistence-only codecs/mapping helpers
├── dao                 # MyBatis primitives / mapper / PO
└── config              # module configuration
```

`common / helper / utils / base` 不能成为新的业务大桶。需要新 top-level package 时，必须先更新架构文档并说明它代表的稳定业务角色。

## Application Boundary

当前稳定入口仍是：

```text
LineageController
    └── LineageService

internal publishers
    ├── LineageService
    └── LineageMaintenanceService
```

Stage 1 不改 production API。后续 Service 收敛的目标是：

```text
LineageController
    ├── LineageQueryService
    └── LineageWriteService

internal publishers
    ├── LineageWriteService
    └── LineageMaintenanceService
```

稳定入口使用 `@Service`。内部专业角色优先使用 `@Component`、`@Repository` 或普通对象，不通过增加更多 `*Service` 掩盖职责不清。

## Role Vocabulary

- **Service**：跨 HTTP / module caller 的稳定应用入口，负责事务边界和用例编排；
- **Analyzer**：输入事实并产生分析结果，不拥有持久化 truth；
- **Collector**：从 Flink / Spark / Hadoop 等来源获取 lineage evidence；
- **Resolver**：把外部引用解析为统一领域引用；
- **Mapper / Converter**：边界表示转换，不承载业务状态机；
- **Repository**：领域持久化 contract；
- **RepositoryAdapter**：把领域模型翻译为 DAO/PO；
- **DAO**：数据库读写 primitive，不承载应用编排。

## Domain Boundary

未来 `domain/` 只能依赖 JDK 与必要的通用值类型，不依赖：

```text
Spring MVC / Spring Service
MyBatis / Mapper / PO
Controller DTO / VO
Repository implementation
Flink / Spark / Hadoop SDK
```

当前 Asset / Relation / Graph 等仍位于根包，这是**显式过渡债务**，不是推荐的新代码位置。

## Analysis / Collector Boundary

SQL projection 分析已经具备 source-neutral contract。后续实现按角色归位：

```text
analysis/
└── sql/
    └── ...SqlProjectionLineageAnalyzer

collector/
├── flink/
├── spark/
└── hadoop/
```

技术实现复用统一 Domain，不允许出现 `flink/domain`、`spark/domain` 等平行领域模型。

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
- DAO 不反向依赖 Service / Controller / Repository；
- Repository adapter 是 Domain ↔ Persistence 的转换位置；
- `LineageJsonCodec` 等持久化表示 helper 留在 `repository.support`。

## Transitional Root Debt

Stage 1 明确冻结以下根包 production 类型：

```text
LineageAsset
LineageAssetDraft
LineageAssetType
LineageDirection
LineageGraph
LineageMaintenanceService
LineageRelation
LineageRelationDraft
LineageRelationType
LineageService
SqlProjectionLineageAnalyzer
```

在后续 Domain / Service / Analysis 阶段完成前，不允许继续向根包增加新的 production 类型。新增能力应直接进入长期角色 package，或在对应重构阶段更新 contract。

## Change Rules

1. 一个 PR 一个主要边界或行为关注点；
2. Stage 1 不改 REST、DB schema、Flyway 和领域语义；
3. package move 与 behavior change 尽量分开；
4. 新入口稳定后不长期保留 production 双入口；
5. Maven 子模块拆分必须由真实依赖隔离需求驱动，不为目录美观提前拆 jar；
6. 新 dependency 必须同时符合 `ARCHITECTURE.md + DEPENDENCIES.md`；
7. dependency guard 的白名单只能因真实架构变化收窄或经评审调整，不能为了让测试通过随意扩大。
