# Analysis Architecture

## 1. Purpose

Analysis 是 reusable analytical definition control plane，不是 query execution runtime。

架构目标：让 Definition、Query Semantics、Visualization、Dataset Binding、Reference、Lineage Projection 和 Persistence 各自拥有明确角色。

## 2. Package Map

```text
io.yak.ops.business.analysis
├── AnalysisService
├── AnalysisReferenceService
├── AnalysisDeletionGuard
├── controller/v1
│   ├── dto
│   ├── vo
│   └── mapper
├── definition
├── domain
├── query
├── visualization
├── reference
├── lineage
├── gateway
│   ├── dataset
│   └── lineage
├── repository
│   └── codec
├── dao
└── config
```

根 package 的三个 public type 是稳定兼容边界。新的实现角色必须进入明确 subsystem。

## 3. Stable Facades

### AnalysisService

保留给 HTTP 与已有 application caller：

```text
AnalysisService
    -> AnalysisManager
    -> AnalysisReader
```

它不拥有另一套 lifecycle，不直接依赖 Repository/DAO/Dataset/Lineage。

### AnalysisReferenceService

提供下游模块最小引用能力：

```text
requireExists(analysisId)
```

Dashboard 不需要通过完整 AnalysisService 获取内部定义来做引用校验。

### AnalysisDeletionGuard

稳定 cross-domain extension point。Dashboard 等 owner 可以阻止删除仍被引用的 Analysis。

## 4. Definition

`definition` 拥有当前 Analysis command/read lifecycle：

```text
AnalysisManager
AnalysisReader
AnalysisDefinitionNormalizer
AnalysisSaveCommand
AnalysisChangedEvent
```

Create/update 流程：

```text
validate command
    -> normalize query
    -> normalize visual config
    -> validate Dataset binding
    -> persist current definition
    -> publish AnalysisChangedEvent
```

Delete 流程：

```text
require current Analysis
    -> deletion guards
    -> delete metadata
    -> publish deleted fact
```

## 5. Query And Visualization

`query` 只表达 declarative analysis semantics 和标准化角色。

```text
AnalysisQueryNormalizer
    -> AnalysisChartBindingPolicy
```

`visualization` 拥有 chart type、chart binding constraint 和 chart-local visual defaults。

没有 Analysis Runtime/Engine package；真正查询 Dataset 的能力不在本模块。

## 6. Dataset Boundary

Analysis-owned Port：

```text
AnalysisDatasetGateway
```

Adapter：

```text
DatasetAnalysisAdapter
    -> DatasetBindingPolicy
```

只有 adapter 可以理解 Dataset 模块的具体 API。Definition 只依赖自己的 Gateway。

## 7. Reference Boundary

```text
AnalysisReferenceService
    -> AnalysisReferenceReader
    -> AnalysisReader
```

Reference 是窄 read-side，不产生 mutation。

## 8. Lineage Projection

```text
AnalysisChangedEvent
    -> AnalysisLineageRefreshListener   (AFTER_COMMIT)
    -> AnalysisLineageSynchronizer      (REQUIRES_NEW)
    -> AnalysisLineageGraphGateway
    -> LineageAnalysisAdapter
    -> shared Lineage module
```

`AnalysisFieldUsageExtractor` 负责把 QuerySpec 转成稳定 usage evidence。

Synchronizer 只依赖 Analysis-owned gateway，不依赖 LineageService、LineageMaintenanceService、LineageAsset 或 ObjectMapper。

## 9. Persistence

```text
Definition / Reader
    -> AnalysisRepository
    -> AnalysisRepositoryAdapter
    -> AnalysisDao
    -> AnalysisMapper / AnalysisPO
    -> MySQL
```

`AnalysisJsonCodec` 位于 `repository/codec`，负责 JSON column representation。

Application/Domain 不读取或写入 `query_spec_json` / `visual_config_json` 字符串。

## 10. Infrastructure Dependency

Analysis 复用业务 DataSource 与 `ConditionalOnDataSourceEnabled` 等基础设施 wiring；这不表示 Analysis 拥有 Datasource 业务领域。

`AnalysisPersistenceConfiguration` 当前仍依赖 Dataset Flyway 初始化顺序，这是部署/初始化约束，不是 Dataset truth ownership。

## 11. Stereotypes

- `@Service`：仅稳定 `AnalysisService` / `AnalysisReferenceService`；
- `@Component`：Manager、Reader、Normalizer、Policy、Collector、Extractor、Synchronizer、Gateway Adapter、Codec、HTTP Mapper；
- `@Repository`：Repository/DAO persistence adapter；
- Domain/value object：不使用 Spring stereotype。

## 12. Compatibility Rule

架构重构默认保持 REST、DB、Dataset binding、Dashboard reference、Lineage evidence 和 query semantic 行为。

如果要新增 Analysis Version、Execution 或 Runtime，必须作为独立领域设计，而不是扩张现有 Manager/Normalizer。