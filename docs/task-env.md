# 任务环境变量框架设计

> 状态：Implemented  
> 范围：系统级环境变量的设置、持久化、自动注入到任务执行上下文的框架化能力。

## 1. 背景与动机

Python、Shell 等外部进程任务依赖运行时环境变量（如 `PYTHON_HOME`、`JAVA_HOME`）来定位解释器和配置行为。早期实现中，每个 Task Adapter 各自手工注入环境变量，存在三个问题：

1. **重复代码**：`PythonTaskExecutorAdapter` 和 `SqlTaskExecutorAdapter` 各自定义私有的 `XxxExecutionContext` record，其中 trigger 防护、参数不可变化、env 合并逻辑完全相同；
2. **直接依赖**：每个 Adapter 直接依赖 `SystemEnvVarService`，手动调用 `resolveMergedEnv()`，新增任务类型需重复同样的拼装套路；
3. **遗漏风险**：`SyncTaskExecutorAdapter` 完全没有注入环境变量，新增任务类型容易忘记。

重构为框架级通用能力后，所有任务类型自动获得环境变量注入，无需感知 `SystemEnvVarService` 的存在。

## 2. 环境变量三层优先级

任务进程的环境变量按以下优先级合并（高优先级覆盖低优先级）：

```text
┌──────────────────────────────────────┐
│  1. Task-level envVars               │  ← 任务定义 configJson 中的 envVars 字段
├──────────────────────────────────────┤
│  2. Application-level env vars       │  ← 设置页面配置（存储在 yak_system_env_var 表）
├──────────────────────────────────────┤
│  3. OS-level environment variables   │  ← System.getenv()，Java 进程继承自操作系统
└──────────────────────────────────────┘
```

合并规则：

- OS 环境变量作为基础层；
- Application-level 变量覆盖同名 OS 变量；
- Task-level `envVars` 覆盖同名 Application-level 变量；
- 每层内部不存在同名冲突。

## 3. 架构设计

### 3.1 整体分层

```text
┌─────────────────────────────────────────────────┐
│                   yak-ops-ui                     │
│         Settings / 环境变量设置面板               │
└────────────────────┬────────────────────────────┘
                     │ REST API
                     v
┌─────────────────────────────────────────────────┐
│           yak-ops-business-job                   │
│  ┌──────────────────┐  ┌──────────────────────┐ │
│  │ SystemEnvVar     │  │ TaskExecution        │ │
│  │ Controller       │  │ ContextFactory       │ │
│  └────────┬─────────┘  └──────────┬───────────┘ │
│           │                       │              │
│  ┌────────▼───────────────────────▼───────────┐ │
│  │          SystemEnvVarService               │ │
│  │   (DB 持久化 + 内存缓存 + 合并解析)         │ │
│  └────────────────────────────────────────────┘ │
└─────────────────────────┬───────────────────────┘
                          │ TaskExecutionContext
                          v
┌─────────────────────────────────────────────────┐
│         yak-ops-plugin-task-api                  │
│  ┌──────────────────────┐  ┌──────────────────┐ │
│  │ TaskExecutionContext │  │ DefaultTask      │ │
│  │   (interface)        │  │ ExecutionContext │ │
│  └──────────────────────┘  └──────────────────┘ │
└─────────────────────────┬───────────────────────┘
                          │
                          v
┌─────────────────────────────────────────────────┐
│    concrete plugins (python / sql / ...)         │
│    通过 context.globalEnvVars() 消费环境变量      │
└─────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### SystemEnvVarService

`io.yak.ops.business.job.env.SystemEnvVarService`

职责：

- 应用级环境变量的 CRUD（持久化到 `yak_system_env_var` 表）；
- 启动时从数据库加载全量到内存 `ConcurrentHashMap` 缓存；
- 修改时同步更新缓存和数据库，无需重启应用即可生效；
- `resolveMergedEnv()`：合并 OS 环境变量 + Application 级变量，返回不可变快照。

关键方法：

```java
// 合并 OS + Application 级变量
Map<String, String> resolveMergedEnv()

// CRUD
Map<String, String> getAll()
void set(String key, String value)
boolean remove(String key)
void batchSave(Map<String, String> variables)
```

#### TaskExecutionContextFactory

`io.yak.ops.business.job.task.TaskExecutionContextFactory`

职责：**环境变量注入的唯一入口**。所有 Task Adapter 通过此工厂获取执行上下文，环境变量自动注入，无需直接依赖 `SystemEnvVarService`。

```java
// 基础用法：Python 等不需要 capability 的任务
TaskExecutionContext context = contextFactory.create(trigger, input);

// 高级用法：SQL 等需要 capability 的任务
TaskExecutionContext context = contextFactory.create(trigger, input,
    builder -> builder.capability(DataSourceExecutionProvider.class, provider));
```

#### DefaultTaskExecutionContext

`io.yak.ops.plugin.task.api.DefaultTaskExecutionContext`

职责：`TaskExecutionContext` 接口的框架级通用实现，所有 Adapter 共享。提供 Builder 模式构建，内置：

- trigger null 防护（默认 `WORKFLOW`）；
- parameters / globalEnvVars / capabilities 不可变化封装；
- capability 按类型查找（支持精确匹配和子类型匹配）。

## 4. 数据模型

### 4.1 数据库表

迁移脚本：`V12__add_system_env_var.sql`（位于 `yak-data-development` 迁移路径）

```sql
CREATE TABLE IF NOT EXISTS yak_system_env_var (
    var_key    VARCHAR(128) NOT NULL,
    var_value  TEXT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (var_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

约束：

- `var_key` 必须符合环境变量命名规则：`[A-Za-z_][A-Za-z0-9_]*`，最长 128 字符；
- `var_value` 使用 TEXT 类型，不限长度；
- 同一 key 通过 `ON DUPLICATE KEY UPDATE` 实现 upsert。

### 4.2 Key 命名规范

| 类别 | 示例 | 说明 |
|------|------|------|
| 解释器路径 | `PYTHON_HOME`、`JAVA_HOME` | 指向安装根目录，由 Plugin 解析为可执行文件路径 |
| 编码配置 | `PYTHONUTF8` | 运行时行为开关 |
| 自定义变量 | `MY_APP_CONFIG` | 业务自定义 |

敏感关键词自动脱敏：key 包含 `PASSWORD`、`SECRET`、`TOKEN`、`KEY`、`CREDENTIAL` 时，日志中输出 `********`。

## 5. 运行时环境变量注入链路

以 Python 任务为例，完整链路如下：

```text
Settings UI 设置 PYTHON_HOME=C:\Python312
    │
    ▼
SystemEnvVarController.set("PYTHON_HOME", "C:\Python312")
    │
    ▼
SystemEnvVarService.set()
    ├─→ DB: INSERT ... ON DUPLICATE KEY UPDATE
    └─→ cache.put("PYTHON_HOME", "C:\Python312")
    │
    ▼  (下次任务执行时)
TaskExecutionContextFactory.create(trigger, input)
    │
    ▼
SystemEnvVarService.resolveMergedEnv()
    ├─→ base = System.getenv()          ← OS 环境变量
    └─→ base.putAll(cache)              ← Application 级覆盖
    │
    ▼
DefaultTaskExecutionContext { globalEnvVars = 合并结果 }
    │
    ▼
PythonTaskPlugin.createExecutor(definition, context)
    │
    ├─→ config = PythonTaskConfig.parse(configJson, context.globalEnvVars())
    │       └─→ defaultPythonExecutable(globalEnv)
    │               └─→ globalEnv.get("PYTHON_HOME") → "C:\Python312"
    │                   → resolvePythonFromHome() → "C:\Python312\python.exe"
    │
    └─→ new PythonTaskExecutor(definition, config, globalEnvVars)
            │
            ▼
        startProcess()
            ├─→ command = ["C:\Python312\python.exe", scriptFile]
            ├─→ env.putAll(globalEnvVars)     ← PYTHON_HOME 等注入进程环境
            └─→ env.putAll(config.envVars())  ← Task-level 覆盖
```

## 6. PYTHON_HOME 解析规则

`PythonTaskConfig.defaultPythonExecutable(Map<String, String> env)` 的解析逻辑：

```text
globalEnv 中存在 PYTHON_HOME 且非空？
  ├─ YES → Windows: $PYTHON_HOME\python.exe
  │        Linux/macOS: $PYTHON_HOME/bin/python
  └─ NO  → 回退 "python"（由操作系统 PATH 解析）
```

当 `pythonExecutable` 在任务定义 `configJson` 中被显式指定时，优先使用显式值，`PYTHON_HOME` 不再生效。

## 7. 跨平台兼容性

| 问题 | 解决方案 |
|------|----------|
| Windows Python 默认 GBK 编码 | 仅 Windows 自动注入 `PYTHONUTF8=1`（`putIfAbsent`，可被 Application/Task 级覆盖） |
| Java 读取进程输出乱码 | `process.inputReader(StandardCharsets.UTF_8)` 显式指定 UTF-8 读取 |
| Windows `python` 可能是 App Execution Alias | 设置 `PYTHON_HOME` 指向真实安装目录，解析为绝对路径 `python.exe` |
| OS 检测不应硬编码 | `System.getProperty("os.name")` 运行时检测，`IS_WINDOWS` 类常量复用 |

## 8. REST API

### 8.1 查询所有环境变量

```
GET /api/v1/system/env-vars
→ 200  { "data": [{ "key": "PYTHON_HOME", "value": "C:\Python312" }, ...] }
```

### 8.2 批量保存（全量替换）

```
PUT /api/v1/system/env-vars
Body: [{ "key": "PYTHON_HOME", "value": "C:\Python312" }, ...]
→ 200  { "data": null }
```

### 8.3 删除单个环境变量

```
DELETE /api/v1/system/env-vars/{key}
→ 204
```

## 9. 前端集成

设置页面 `Settings > 环境变量` 面板（`EnvironmentSettingsPanel.tsx`）提供：

- 键值对表格展示当前所有环境变量；
- 增 / 删 / 改操作，保存时批量提交；
- Key 输入校验（合法环境变量命名规则）；
- 值输入支持多行文本。

Python 任务结果面板（`PythonEditor.tsx`）显示：

- `解释器：C:\Python312\python.exe` — 展示实际使用的 Python 可执行文件路径，用于验证 `PYTHON_HOME` 是否生效。

## 10. 扩展指南

新增任务类型（如 Shell）接入环境变量框架只需两步：

1. Adapter 构造器注入 `TaskExecutionContextFactory`；
2. `start()` 方法中调用 `contextFactory.create(trigger, input)`。

无需关心 `SystemEnvVarService`，无需定义私有 Context record。

```java
@Service
public class ShellTaskExecutorAdapter implements TaskExecutor {

  private final TaskExecutionContextFactory contextFactory;

  public ShellTaskExecutorAdapter(TaskExecutionContextFactory contextFactory, ...) {
    this.contextFactory = contextFactory;
  }

  @Override
  public TaskExecution start(...) {
    // 环境变量自动注入
    TaskExecutionContext context = contextFactory.create(trigger, input);
    TaskPlugin plugin = pluginRegistry.require("SHELL");
    TaskExecutor executor = plugin.createExecutor(definition, context);
    ...
  }
}
```

如需 capability，使用带 `customiser` 的重载：

```java
TaskExecutionContext context = contextFactory.create(trigger, input,
    builder -> builder.capability(SomeProvider.class, provider));
```

## 11. 模块与文件索引

| 模块 | 文件 | 职责 |
|------|------|------|
| `yak-ops-plugin-task-api` | `TaskExecutionContext.java` | 接口：`globalEnvVars()` default 方法 |
| `yak-ops-plugin-task-api` | `DefaultTaskExecutionContext.java` | 通用实现：Builder + 不可变封装 |
| `yak-ops-plugin-task-python` | `PythonTaskConfig.java` | `defaultPythonExecutable(env)` — PYTHON_HOME 解析 |
| `yak-ops-plugin-task-python` | `PythonTaskExecutor.java` | 进程启动：env 注入 + UTF-8 编码 |
| `yak-ops-plugin-task-python` | `PythonTaskPlugin.java` | `createExecutor()` — 传递 `globalEnvVars` |
| `yak-ops-business-job` | `SystemEnvVarService.java` | CRUD + 缓存 + `resolveMergedEnv()` |
| `yak-ops-business-job` | `SystemEnvVarController.java` | REST API |
| `yak-ops-business-job` | `TaskExecutionContextFactory.java` | 自动注入 env 的工厂（唯一入口） |
| `yak-ops-business-job` | `PythonTaskExecutorAdapter.java` | 使用 Factory 创建上下文 |
| `yak-ops-business-job` | `SqlTaskExecutorAdapter.java` | 使用 Factory + capability 注册 |
| `yak-ops-business-data-development` | `V12__add_system_env_var.sql` | 数据库迁移脚本 |
| `yak-ops-ui` | `EnvironmentSettingsPanel.tsx` | 设置面板 UI |
| `yak-ops-ui` | `envVars.ts` | 前端 API service |
| `yak-ops-ui` | `PythonEditor.tsx` | 显示解释器路径 |
