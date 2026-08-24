# Data Development Requirements

本文件定义 Data Development 的长期需求语义。它回答“这个模块需要提供什么能力”，不承担代码分层说明。

领域硬规则看 `DOMAIN.md`，角色和依赖边界看 `ARCHITECTURE.md`。

## 模块定位

Data Development 是数据开发工作台的 authoring context。它负责组织开发节点、维护可编辑定义、发布不可变版本，并把可执行任务交给共享 Task Runtime，把发布状态投影到 Task Catalog，把 SQL 血缘证据交给 Lineage 子系统。

它不是调度引擎、数据目录、血缘图谱或 Task Runtime 的替代实现。

## 核心能力

### 1. 开发节点

- `DevelopmentNode` 是工作区中的长期身份，负责名称、类型、项目/目录位置和配置状态。
- 节点类型必须显式声明能力；不能通过 `PROCESSING / OUTPUT` 分类推断是否允许进入任务生命周期。
- SQL / SHELL / HTTP / PYTHON / JAVA 可以进入 Task Draft -> Revision 生命周期。
- DATASET / DATA_SERVICE 是输出型节点，不复用 Task Draft -> Revision 生命周期。

### 2. Task Authoring

可执行节点必须支持：

```text
DevelopmentNode
    -> mutable DevelopmentTaskDraft
    -> immutable DevelopmentTaskRevision
```

要求：

- Draft 使用独立的 `draftRevision` 做并发控制；
- 保存 Draft 必须校验 Node Type 与 TaskDefinition Type 一致；
- `configJson` 在进入持久化前规范化为 JSON Object；
- Publish 必须基于明确的 Draft Revision；
- Publish 必须执行对应 Task Plugin 的校验；
- 相同 Draft Revision + 相同 Definition Digest 重复发布时复用已有 Revision；
- Published Revision 不可被后续编辑覆盖。

### 3. Editor Run

编辑器运行当前内容时，不要求先 Publish。

```text
current editor definition
    -> TaskVersionSnapshot(version = 0)
    -> shared Task Runtime
    -> DevelopmentTaskExecution history
```

Editor Run 与 Published Revision 是两个概念。运行当前编辑器内容不能隐式创建 Published Revision，也不能修改 Task Catalog 的发布指针。

### 4. Release Projection

发布后的 Data Development Task 通过 Task Catalog 暴露上线、下线和历史版本切换能力。

Task Catalog 是发布状态和跨模块任务资产入口；`DevelopmentTaskRevision` 仍然是 Data Development 内部不可变版本事实。

### 5. Data Service

DATA_SERVICE 使用独立的 Draft / Revision / Runtime contract，不得伪装成通用 Task Node。

Data Service 发布前至少要保证：

- Runtime 支持的 HTTP method；
- 有明确 DataSource；
- SQL 非空；
- Response Contract 已确认；
- Request Contract 满足当前 Runtime 能力。

### 6. Lineage

SQL Task Revision 发布后可以异步生成表级和字段级 lineage evidence。

Data Development 只拥有“哪个 Revision 产生了什么解析证据”的责任；Lineage Asset / Relation 的最终事实由 `yak-ops-business-lineage` 持有。

Lineage 解析或写入失败不能回滚已经成功提交的业务发布；可靠处理通过 durable outbox / worker 完成。

## 兼容要求

Stage 1 重构必须保持：

- `/api/v1/development/**` 现有 REST contract；
- 现有数据库表和 Flyway migration；
- Draft optimistic revision 语义；
- Revision number / checksum 语义；
- Task Catalog publish / online / offline 行为；
- Editor Run 走共享 Task Runtime；
- SQL lineage outbox 行为。

## Stage 1 非目标

本阶段不处理：

- 全量删除 `service` package；
- 重写 Data Service / Release / Lineage 全部实现；
- 新增数据库模型；
- 修改 API v1；
- 改造共享 Task Runtime；
- 抽取 Data Development 与 Offline/Realtime Sync 的 Shared Kernel。

Stage 1 的重点是先建立 Domain Truth，并让 Task 主链开始按明确角色组织。