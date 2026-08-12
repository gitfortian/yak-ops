# Task Plugin 阶段 2：插件骨架与发现机制

> 状态：Implemented（阶段 2）  
> 范围：建立平台级 Task Plugin API、SQL 占位插件、ServiceLoader 发现与核心 Registry；不实现真实 SQL 执行。

## 1. 模块结构

```text
yak-ops-plugins/yak-ops-plugin-task
├── yak-ops-plugin-task-api
├── yak-ops-plugin-task-sql
└── yak-ops-plugin-task-all
```

依赖方向：

```text
business / future task-runtime
             |
             v
         yak-ops-core
             |
             v
 yak-ops-plugin-task-api
             ^
             |
       concrete plugins
             |
             v
          task-all
```

`yak-ops-boot` 只依赖 `task-all` 完成内置插件装配，业务模块不直接依赖 SQL 等具体插件。

## 2. 稳定插件契约

阶段 2 固定以下最小契约：

- `TaskPlugin`：声明插件元数据、校验 Definition、按 Attempt 创建 Executor；
- `TaskPluginDescriptor`：任务类型、名称、插件版本、Schema 版本与执行能力；
- `TaskValidationResult / TaskValidationIssue`：结构化定义校验结果；
- `TaskExecutionContext`：Task Runtime 提供给插件的最小运行上下文；
- `TaskExecutor`：一次物理 Attempt 的执行器，并预留取消能力；
- `TaskExecutionResult`：统一执行状态、消息与输出。

这些接口属于平台级 Task Plugin，不属于 Workflow。后续 Manual / Workflow / Schedule 都应通过统一 Task Runtime 使用同一插件。

## 3. 插件发现

具体插件使用 Java `ServiceLoader` 注册：

```text
META-INF/services/io.yak.ops.plugin.task.api.TaskPlugin
```

`yak-ops-core` 中的 `TaskPluginRegistry` 负责：

- 按当前线程 Context ClassLoader 发现插件；
- 将 Task Type 统一规范为大写；
- 拒绝重复 Task Type；
- 提供 `find / require / descriptors` 统一查询入口；
- 返回不可变插件目录。

第一阶段不引入 PF4J、插件热卸载、独立 ClassLoader 或注册中心。等真正出现运行时动态安装需求后再评估。

## 4. SQL 占位插件

`yak-ops-plugin-task-sql` 当前只用于验证架构链路：

```text
SQL TaskPlugin
    -> ServiceLoader
    -> TaskPluginRegistry
```

目前只校验：

- `taskType=SQL`；
- `schemaVersion=1`；
- SQL `content` 非空。

`descriptor.executable=false`，也没有实现 `createExecutor`。这意味着阶段 2 **不会连接数据库、不会执行 SQL、不会读取数据源凭据**。

真实 SQL 执行放在后续 SQL Task Plugin 阶段，并复用 Yak Ops 已有 `DataSourcePlugin`，避免按 MySQL/Doris/PostgreSQL 重复建设任务插件。

## 5. 当前边界

本阶段明确不做：

- 不新增数据库表和 Flyway；
- 不实现 Draft / Revision / Publish；
- 不实现 Task Catalog；
- 不修改 Workflow 持久化和运行逻辑；
- 不修改 Data Development API；
- 不实现 SQL/Shell/Python/HTTP 真实执行；
- 不引入 Worker、Docker 或 Kubernetes Execution Backend。

## 6. 下一步

阶段 3 开始实现数据开发控制面的：

```text
DevelopmentNode
      |
      v
   TaskDraft
      |
   publish
      v
 TaskRevision
```

先完成草稿保存、不可变发布版本与版本查询，再进入 SQL 手动运行和 Task Catalog。
