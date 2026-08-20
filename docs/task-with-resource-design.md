# 任务开发支持编译型语言设计：资源引用模式

> 状态：Draft  
> 范围：通过引用资源管理中的文件（JAR、脚本等），让所有任务类型统一支持编译型语言与脚本文件的在线运行。

## 1. 背景与动机

当前任务开发支持 SQL、Python、Shell、HTTP 四种类型，全部是解释型/脚本型语言——用户在编辑器中编写源码文本，平台直接执行。`TaskDefinition.content` 字段承载内联代码文本，对解释型语言天然友好。

编译型语言（如 Java）无法直接在编辑器中写源码后立即运行：源码需要先编译为 class/JAR，再配置 classpath 和依赖。如果要在平台内集成编译能力（javac、Maven 仓库代理等），复杂度和维护成本都很高。

**核心思路**：不在线编译，而是让用户在本地 IDE 完成开发编译后，将 JAR 上传到已有的**资源管理**模块，任务开发时通过 `resourceId` 引用该文件即可。这个模式同时惠及脚本型任务——大脚本文件同样可以上传引用，而不是内联编辑。

## 2. 设计目标

1. 所有任务类型统一支持"内联代码"和"引用资源文件"两种模式。
2. 不修改 `TaskDefinition` 核心模型，资源引用通过 `configJson` 承载。
3. 复用资源管理已有的上传、下载、版本、校验、权限能力，不重复建设文件管理。
4. 任务插件通过 `ResourceResolver` 抽象接口获取资源文件，底层存储（LOCAL/MINIO/HDFS）透明切换。
5. 发布时锁定资源版本，保证生产运行的可追溯性。

## 3. 现有架构分析

### 3.1 资源管理已有能力

| 能力 | 说明 | 关键代码 |
|------|------|---------|
| 文件上传 | 支持任意文件，最大 100MB | `ResourceFileOperations.upload()` |
| 文件下载 | 按 ID 返回 InputStream | `ResourceService.download()` → `ResourceDownload(fileName, contentType, fileSize, inputStream)` |
| 文件元数据 | 存储路径、后缀名、SHA-256 校验值、版本号 | `ResourceNode`：`storagePath`, `suffix`, `checksum`, `version` |
| 多存储后端 | LOCAL / MINIO / HDFS 插件化 | `StorageOperator.download(path) → InputStream` |
| 版本管理 | 替换文件时自动递增 version | `ResourceServiceSupport.nextVersion()` |
| 权限控制 | `resource:view / upload / download / update / delete` | `ResourcePermissionCode` |
| 目录树 | 完整的目录层级管理 | `ResourceService.tree()`, `list()`, `page()` |

### 3.2 任务插件执行模型

```
TaskDefinition(taskType, schemaVersion, content, configJson)
        │
        ▼
  TaskPlugin.validate()          → 校验定义
        │
        ▼
  TaskPlugin.createExecutor()    → 创建 TaskExecutor
        │
        ▼
  TaskExecutor.execute()         → 返回 TaskExecutionResult
```

`TaskExecutionContext.capability()` 已支持按接口类型注入平台级服务，任务插件不依赖 Spring 和具体业务模块。

### 3.3 当前任务类型

| 类型 | content 含义 | 执行方式 |
|------|-------------|---------|
| SQL | SQL 文本 | 委托 `SqlExecutionRuntime` |
| PYTHON | Python 脚本源码 | 写临时 `.py` → `python script.py` |
| SHELL | Shell 脚本源码 | 写临时 `.sh` → `bash script.sh` |
| HTTP | 空（配置在 configJson） | HTTP 客户端调用 |

## 4. 统一资源引用设计

### 4.1 两种执行模式对比

```
内联模式（现有）：                          资源引用模式（新增）：
┌─────────────────────────┐               ┌─────────────────────────┐
│  任务开发编辑器           │               │  任务开发编辑器           │
│  content = "源码文本"     │               │  content = ""            │
│  configJson = {          │               │  configJson = {          │
│    类型私有参数            │               │    "resourceId": 42,     │
│  }                       │               │    类型私有参数            │
└────────────┬────────────┘               │  }                       │
             │                            └────────────┬────────────┘
             ▼                                         ▼
       直接执行内联代码                        从资源管理下载文件
                                                     │
                                                     ▼
                                           执行 JAR / 脚本文件
```

### 4.2 各任务类型支持矩阵

| 任务类型 | 内联模式（现有） | 资源引用模式（新增） |
|---------|----------------|-------------------|
| SQL | `content = "SELECT ..."` | 一般不需要，SQL 通常内联 |
| PYTHON | `content = "print('hello')"` | `resourceId → etl_job.py` |
| SHELL | `content = "echo hello"` | `resourceId → deploy.sh` |
| JAVA | 不适用（编译型） | `resourceId → app.jar` |
| HTTP | configJson 定义 URL/Method | 一般不需要 |

### 4.3 configJson 统一约定

资源引用模式在 `configJson` 中使用以下公共字段：

```json
{
  "resourceId": 42,
  "resourceVersion": 3,
  "timeoutSeconds": 120
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `resourceId` | long | 是 | 资源管理中的文件 ID。存在此字段即表示资源引用模式 |
| `resourceVersion` | int | 否（发布时自动填充） | 锁定资源版本。发布时固化到 TaskRevision，保证可追溯 |
| `checksum` | string | 否（发布时自动填充） | 资源文件的 SHA-256 校验值，发布时由平台自动写入，运行时用于完整性验证 |
| `timeoutSeconds` | int | 否 | 执行超时，默认由平台配置 |

> **约定**：`resourceId` 的存在是资源引用模式的唯一判定依据。各任务类型不在公共字段中定义 `entryPoint` 等语义模糊的通用入口字段，而是使用类型私有的、语义明确的字段名（如 Java 的 `mainClass`），避免跨类型歧义。

各任务类型在公共字段之上追加私有参数：

**JAVA 示例**：

```json
{
  "resourceId": 42,
  "resourceVersion": 3,
  "mainClass": "com.example.data.EtlJob",
  "jvmArgs": ["-Xmx512m", "-Dfile.encoding=UTF-8"],
  "programArgs": ["--source", "kafka://...", "--sink", "paimon://..."],
  "timeoutSeconds": 300,
  "envVars": {
    "JAVA_HOME": "/usr/lib/jvm/java-17"
  }
}
```

等价于执行：

```bash
/usr/lib/jvm/java-17/bin/java \
  -Xmx512m -Dfile.encoding=UTF-8 \
  -jar /tmp/yak-task-xxx/app.jar \
  --source kafka://... --sink paimon://...
```

**PYTHON 资源引用示例**：

```json
{
  "resourceId": 15,
  "scriptArgs": ["--config", "/etc/etl.yaml"],
  "timeoutSeconds": 60,
  "envVars": {}
}
```

**SHELL 资源引用示例**：

```json
{
  "resourceId": 28,
  "scriptArgs": ["production"],
  "timeoutSeconds": 120
}
```

## 5. 执行链路

### 5.1 整体流程

```
TaskPlugin.createExecutor(definition, context)
        │
        ▼
  从 configJson 解析 resourceId + resourceVersion
        │
        ▼
  context.requireCapability(ResourceResolver.class)
        │  ← 平台注入的资源解析能力
        ▼
  ResourceResolver.resolve(resourceId, resourceVersion)
        │
        │  内部调用 ResourceDownloadProvider.download(resourceId)
        │  将 InputStream 写入本地临时文件
        │  校验下载文件的 checksum 与元数据一致
        │
        ▼
  返回 ResolvedResource（本地临时文件 Path + 元数据 + checksum）
        │
        ▼
  根据任务类型执行：
    JAVA   → java -jar /tmp/yak-task-xxx/app.jar [args]
    PYTHON → python /tmp/yak-task-xxx/etl_job.py [args]
    SHELL  → bash /tmp/yak-task-xxx/deploy.sh [args]
        │
        ▼
  执行完毕 → ResolvedResource.close() 清理临时文件
```

### 5.2 ResourceResolver 接口

放在 `yak-ops-spi` 中，与 `DataSourceExecutionProvider` 等平台级能力接口保持一致。任务插件通过 `TaskExecutionContext.capability()` 获取此接口，不依赖资源管理的具体实现：

```java
package io.yak.ops.spi.resource;

import java.nio.file.Path;

/**
 * 平台提供的资源解析能力。
 *
 * <p>任务插件通过 {@code TaskExecutionContext.capability(ResourceResolver.class)} 获取此接口，
 * 将资源管理中的文件引用解析为本地可访问的临时文件。
 *
 * <p>存在 {@code configJson.resourceId} 即表示资源引用模式，
 * 此约定应在各 TaskPlugin 的 Javadoc 中明确记录。
 */
public interface ResourceResolver {

  /**
   * 将资源管理中的文件（最新版本）解析到本地临时目录。
   * 适用于开发调试阶段。
   *
   * <p>返回的 ResolvedResource 实现了 AutoCloseable，
   * 调用方应在执行完毕后调用 close() 清理临时文件。
   */
  ResolvedResource resolve(long resourceId);

  /**
   * 将资源管理中的文件（指定版本）解析到本地临时目录。
   * 适用于生产运行阶段，保证版本一致性。
   *
   * <p>如果指定版本不存在（已被清理），抛出 IllegalStateException。
   */
  ResolvedResource resolve(long resourceId, int version);
}
```

```java
package io.yak.ops.spi.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 已解析到本地的资源文件。
 *
 * @param localPath 临时文件路径
 * @param fileName  原始文件名
 * @param suffix    文件后缀（如 "jar", "py", "sh"）
 * @param fileSize  文件大小（字节）
 * @param checksum  SHA-256 校验值，用于完整性验证
 */
public record ResolvedResource(
    Path localPath,
    String fileName,
    String suffix,
    long fileSize,
    String checksum
) implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ResolvedResource.class);

  @Override
  public void close() {
    if (localPath == null) return;
    try {
      Path parent = localPath.getParent();
      if (parent != null) {
        // 递归清理临时目录
        deleteRecursively(parent);
      }
    } catch (IOException e) {
      // 临时文件清理失败不抛异常，但记录日志以便排查磁盘问题
      log.warn("Failed to cleanup temp directory for resource '{}': {}",
          fileName, e.getMessage());
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<>() {
      @Override
      public java.nio.file.FileVisitResult visitFile(
          java.nio.file.Path file, java.nio.file.BasicFileAttributes attrs) throws IOException {
        Files.deleteIfExists(file);
        return java.nio.file.FileVisitResult.CONTINUE;
      }
      @Override
      public java.nio.file.FileVisitResult postVisitDirectory(
          java.nio.file.Path dir, IOException exc) throws IOException {
        Files.deleteIfExists(dir);
        return java.nio.file.FileVisitResult.CONTINUE;
      }
    });
  }
}
```

### 5.3 平台侧实现

`ResourceDownloadProvider` 定义在 `yak-ops-spi` 中作为顶层接口，避免内部接口带来的引用冗长和 Mock 困难。`DefaultResourceResolver` 实现放在 `yak-ops-business-resource` 中：

```java
package io.yak.ops.spi.resource;

import java.io.InputStream;

/**
 * 资源文件下载的最小抽象。
 *
 * <p>平台侧实现此接口，委托 {@code ResourceService.download()}，
 * 使 ResourceResolver 不直接依赖 resource 业务模块。
 */
public interface ResourceDownloadProvider {

  ResourceDownloadResult download(long resourceId);

  ResourceDownloadResult download(long resourceId, int version);
}
```

```java
package io.yak.ops.spi.resource;

import java.io.InputStream;

/**
 * 资源下载结果。 */
public record ResourceDownloadResult(
    String fileName,
    String suffix,
    long fileSize,
    String checksum,
    InputStream inputStream
) {}
```

```java
package io.yak.ops.business.resource.resolver;

import io.yak.ops.spi.resource.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;

/**
 * ResourceResolver 的平台实现。
 * 从资源管理下载文件到本地临时目录，并校验 checksum。
 */
public class DefaultResourceResolver implements ResourceResolver {

  private final ResourceDownloadProvider downloadProvider;

  public DefaultResourceResolver(ResourceDownloadProvider downloadProvider) {
    this.downloadProvider = downloadProvider;
  }

  @Override
  public ResolvedResource resolve(long resourceId) {
    ResourceDownloadResult result = downloadProvider.download(resourceId);
    return doResolve(resourceId, result);
  }

  @Override
  public ResolvedResource resolve(long resourceId, int version) {
    ResourceDownloadResult result = downloadProvider.download(resourceId, version);
    return doResolve(resourceId, result);
  }

  private ResolvedResource doResolve(long resourceId, ResourceDownloadResult result) {
    try {
      Path tempDir = Files.createTempDirectory("yak-task-");
      Path localFile = tempDir.resolve(result.fileName());
      String actualChecksum;
      try (InputStream in = result.inputStream()) {
        actualChecksum = copyWithChecksum(in, localFile);
      }
      // 校验下载文件的完整性
      if (result.checksum() != null && !result.checksum().isBlank()
          && !result.checksum().equalsIgnoreCase(actualChecksum)) {
        deleteRecursively(tempDir);
        throw new IllegalStateException(
            "Resource #" + resourceId + " checksum mismatch: expected "
                + result.checksum() + ", actual " + actualChecksum);
      }
      return new ResolvedResource(
          localFile,
          result.fileName(),
          result.suffix(),
          result.fileSize(),
          actualChecksum
      );
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to resolve resource #" + resourceId + " to local file", e);
    }
  }

  /** 边复制边计算 SHA-256，避免二次读取文件。 */
  private static String copyWithChecksum(InputStream in, Path target) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream din = new DigestInputStream(in, digest);
           var out = Files.newOutputStream(target)) {
        din.transferTo(out);
      }
      StringBuilder hex = new StringBuilder(64);
      for (byte b : digest.digest()) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var entries = Files.list(path)) {
        for (Path entry : entries.toList()) {
          deleteRecursively(entry);
        }
      }
    }
    Files.deleteIfExists(path);
  }
}
```

### 5.4 JavaTaskConfig 配置解析

与 `PythonTaskConfig` 保持一致的设计模式，将 `JAVA_HOME` 解析和可执行文件路径计算集中在 Config 中：

```java
record JavaTaskConfig(
    long resourceId,
    int resourceVersion,
    String mainClass,
    List<String> jvmArgs,
    List<String> programArgs,
    Map<String, String> envVars,
    int timeoutSeconds) {

  static final String JAVA_HOME_ENV = "JAVA_HOME";
  static final int DEFAULT_TIMEOUT_SECONDS = 300;
  static final int MAX_TIMEOUT_SECONDS = 7200;

  /**
   * 根据 JAVA_HOME 解析 java 可执行文件路径。
   *
   * <p>优先级：config.envVars.JAVA_HOME > globalEnvVars.JAVA_HOME > 系统环境变量 > "java"
   */
  static String defaultJavaExecutable(Map<String, String> globalEnv, Map<String, String> taskEnv) {
    String javaHome = resolveJavaHome(globalEnv, taskEnv);
    if (javaHome != null && !javaHome.isBlank()) {
      return resolveJavaFromHome(javaHome.trim());
    }
    return "java";
  }

  private static String resolveJavaHome(Map<String, String> globalEnv, Map<String, String> taskEnv) {
    if (taskEnv != null && taskEnv.containsKey(JAVA_HOME_ENV)) return taskEnv.get(JAVA_HOME_ENV);
    if (globalEnv != null && globalEnv.containsKey(JAVA_HOME_ENV)) return globalEnv.get(JAVA_HOME_ENV);
    return System.getenv(JAVA_HOME_ENV);
  }

  private static String resolveJavaFromHome(String javaHome) {
    if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
      return Path.of(javaHome, "bin", "java.exe").toString();
    }
    return Path.of(javaHome, "bin", "java").toString();
  }

  static JavaTaskConfig parse(String configJson, Map<String, String> globalEnv) {
    // 解析逻辑与 PythonTaskConfig.parse() 模式一致
    // 必须包含 resourceId，可选 resourceVersion / mainClass / jvmArgs / programArgs / envVars / timeoutSeconds
    // ...
  }
}
```

### 5.5 JavaTaskExecutor 执行逻辑

> **注意**：stdout/stderr 必须在 `waitFor` 之前异步消费，否则进程输出量大时会填满管道缓冲区导致死锁。

```java
final class JavaTaskExecutor implements TaskExecutor {

  private final TaskDefinition definition;
  private final JavaTaskConfig config;
  private final Map<String, String> globalEnvVars;
  private final ResourceResolver resourceResolver;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<Process> activeProcess = new AtomicReference<>();

  @Override
  public TaskExecutionResult execute() throws Exception {
    if (cancelled.get()) {
      return cancelledResult("Java execution was cancelled before start");
    }

    // 1. 从资源管理下载 JAR 到本地临时目录（按版本下载，保证生产一致性）
    try (ResolvedResource resource = config.resourceVersion() > 0
        ? resourceResolver.resolve(config.resourceId(), config.resourceVersion())
        : resourceResolver.resolve(config.resourceId())) {
      if (cancelled.get()) {
        return cancelledResult("Java execution was cancelled after resource download");
      }

      // 2. 构建 java -jar 命令
      List<String> command = buildCommand(resource);
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(false);

      // 3. 设置环境变量
      Map<String, String> env = builder.environment();
      env.putAll(globalEnvVars);
      env.putAll(config.envVars());

      // 4. 启动进程
      Process process = builder.start();
      activeProcess.set(process);

      // 5. 异步读取 stdout/stderr，避免管道缓冲区满导致死锁
      CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() ->
          truncate(readStream(process.inputReader(UTF_8))));
      CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() ->
          truncate(readStream(process.errorReader(UTF_8))));

      // 6. 等待完成
      boolean finished = process.waitFor(
          config.timeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        activeProcess.set(null);
        return timeoutResult();
      }

      String stdout = stdoutFuture.get(10, TimeUnit.SECONDS);
      String stderr = stderrFuture.get(10, TimeUnit.SECONDS);
      int exitCode = process.exitValue();
      activeProcess.set(null);

      return buildResult(exitCode, stdout, stderr);
    }
  }

  private List<String> buildCommand(ResolvedResource resource) {
    List<String> command = new ArrayList<>();
    command.add(JavaTaskConfig.defaultJavaExecutable(globalEnvVars, config.envVars()));
    command.addAll(config.jvmArgs());
    command.add("-jar");
    command.add(resource.localPath().toAbsolutePath().toString());
    command.addAll(config.programArgs());
    return command;
  }

  @Override
  public void cancel() {
    cancelled.set(true);
    Process process = activeProcess.get();
    if (process != null) process.destroyForcibly();
  }
}
```

## 6. 涉及代码变更

### 6.1 后端

| 变更位置 | 变更内容 | 影响范围 |
|---------|---------|--------|
| `yak-ops-spi` | 新增 `ResourceResolver` + `ResolvedResource` + `ResourceDownloadProvider` + `ResourceDownloadResult` | 新增 4 个文件，不影响现有代码 |
| `yak-ops-plugin-task-java`（新模块） | 新增 `JavaTaskPlugin` + `JavaTaskExecutor` + `JavaTaskConfig` | 新增 Maven 模块 |
| `yak-ops-plugin-task-all` | 添加对 `yak-ops-plugin-task-java` 的依赖 | pom.xml 一行改动 |
| `yak-ops-business-job` | 新增 `JavaTaskExecutorAdapter`；构建 `TaskExecutionContext` 时注入 `ResourceResolver` capability | 新增 1 个文件 + 小改 |
| `yak-ops-business-resource` | 实现 `ResourceDownloadProvider`；新增 `DefaultResourceResolver` | 小改，委托已有 `download()` |
| `yak-ops-plugin-task-python` | `validate()` 适配双模式：content 为空且无 resourceId 时报错 | 小改 |
| `DevelopmentNodeType` | 新增 `JAVA` 枚举值，`supportsTaskLifecycle()` 返回 true | 一行改动 |

> **说明**：当前代码库中 Shell 任务没有独立的 `yak-ops-plugin-task-shell` 模块，Shell 执行通过 `SyncTaskExecutorAdapter` 处理。阶段 1 暂不为 Shell 新建插件模块，资源引用模式的 Shell 支持放在阶段 2。

### 6.2 前端

| 变更位置 | 变更内容 | 影响范围 |
|---------|---------|--------|
| `types.ts` | `DevelopmentTaskType` 新增 `'JAVA'` | 类型定义 |
| `node-model.ts` | 新增 JAVA 分类映射 `PROCESSING` | 小改 |
| `CreateTaskModal.tsx` | 新增 Java 选项 | 小改 |
| 任务编辑器 | JAVA 类型使用资源选择器组件，而非代码编辑器 | 新增组件 |
| 资源选择器组件 | 新增 `ResourcePicker`：从资源管理选择文件，调用 `ResourceService.list()` / `ResourceService.tree()` | 新增组件 |

### 6.3 模块依赖方向

```
yak-ops-spi
  ├─ ResourceResolver（抽象接口）
  ├─ ResolvedResource（数据结构）
  ├─ ResourceDownloadProvider（下载抽象）
  └─ ResourceDownloadResult（下载数据）
         ▲
         │ 依赖
         │
yak-ops-plugin-task-java
  └─ JavaTaskPlugin → 使用 ResourceResolver 下载 JAR → 子进程执行

yak-ops-business-job
  └─ JavaTaskExecutorAdapter 桥接 TaskExecutionGateway
  └─ 构建 TaskExecutionContext 时注入 ResourceResolver 实现
         │
         │ 委托
         ▼
yak-ops-business-resource
  └─ DefaultResourceResolver + ResourceDownloadProvider 实现
  └─ ResourceService.download() 提供文件流
         │
         │ 委托
         ▼
yak-ops-plugin-storage
  └─ StorageOperator.download() 从 LOCAL/MINIO/HDFS 读取
```

约束：

- `yak-ops-spi` 不依赖任何业务模块，只定义接口和数据结构。
- `yak-ops-plugin-task-java` 只依赖 `task-api` + `yak-ops-spi`，不直接依赖 `resource` 或 `storage`。
- `ResourceDownloadProvider` 作为 `yak-ops-spi` 顶层接口，实现方（`yak-ops-business-resource`）可独立 Mock 和测试。
- 资源解析的平台实现在 `yak-ops-business-resource` 中装配，`yak-ops-business-job` 通过 Spring 依赖注入获取。

## 7. 发布与版本追溯

### 7.1 发布时校验

任务发布（Draft → Revision）时，除了现有的 `TaskPlugin.validate()` 之外，增加资源引用校验：

1. 如果 `configJson` 包含 `resourceId`，验证该资源文件存在且为 FILE 类型。
2. 记录当前 `resourceVersion` 和 `checksum` 到 `TaskRevision` 的元数据中。
3. 资源被删除后，已发布的任务显示"资源缺失"告警，Task Runtime 拒绝执行。

### 7.2 资源版本锁定流程

发布时自动将当前资源的 `version` 和 `checksum` 写入 `configJson`，锁定不可变快照：

```
Draft 阶段：
  configJson = { "resourceId": 42, "mainClass": "...", ... }
  ↓ 用户点击发布
  ↓ 平台查询 ResourceNode(id=42)，获取 version=3, checksum=abc123
  ↓ 自动写入 resourceVersion
  configJson = { "resourceId": 42, "resourceVersion": 3, "checksum": "abc123", "mainClass": "...", ... }
  ↓ 存入 TaskRevision.definitionSnapshotJson

发布 v1 时：resourceId=42, resourceVersion=3, checksum=abc123
资源替换后：resourceId=42, resourceVersion=4, checksum=def456
发布 v2 时：resourceId=42, resourceVersion=4, checksum=def456

Workflow 引用 v1 → ResourceResolver.resolve(42, 3) → 使用 resourceVersion=3 的 JAR
Workflow 引用 v2 → ResourceResolver.resolve(42, 4) → 使用 resourceVersion=4 的 JAR
```

> **注意**：`resourceVersion` 和 `checksum` 由平台在发布时自动填充，用户无需手动设置。Draft 阶段始终使用最新版本。

### 7.3 运行时资源解析

Task Runtime 执行时，根据 `resourceVersion` 是否存在选择下载策略：

| 场景 | `resourceVersion` | 调用方式 | 适用阶段 |
|------|-------------------|---------|---------|
| Draft 手动运行 | 不存在 | `resolve(resourceId)` → 最新版本 | 开发调试 |
| Revision 生产运行 | 已锁定 | `resolve(resourceId, version)` → 指定版本 | 生产运行 |

��前资源管理的 `ResourceNode.version` 字段已支持版本追踪，`replaceFile` 时自动递增。**阶段 1 采用"校验当前版本"策略**：`resolve(id, version)` 下载当前最新版本后，校验其 `version` 是否与请求的 `version` 一致，不一致则报错（版本已被更新）。按版本下载物理历史文件需要存储层保留历史版本，放在阶段 2 实现。

### 7.4 插件 validate() 双模式适配

现有插件的 `validate()` 需要适配资源引用模式。以 Python 为例，校验规则改为：

```java
// 修改前：
if (definition.content() == null || definition.content().isBlank()) {
    issues.add(new TaskValidationIssue(
        "PYTHON_CONTENT_REQUIRED", "content",
        "Python script content must not be blank"));
}

// 修改后：content 和 resourceId 至少提供一个
boolean hasContent = definition.content() != null && !definition.content().isBlank();
boolean hasResource = parseResourceId(definition.configJson()) != null;
if (!hasContent && !hasResource) {
    issues.add(new TaskValidationIssue(
        "PYTHON_CONTENT_OR_RESOURCE_REQUIRED", "content",
        "Python task must have either inline content or a referenced resource"));
}
if (hasContent && hasResource) {
    issues.add(new TaskValidationIssue(
        "PYTHON_CONTENT_RESOURCE_CONFLICT", "content",
        "Python task cannot have both inline content and a referenced resource"));
}
```

JAVA 任务类型只需要资源引用模式（编译型语言无法内联），校验规则为 `resourceId` 必填。

## 8. 风险与应对

| 风险 | 影响 | 应对策略 |
|------|------|---------|
| 资源文件被删除后任务无法运行 | 生产任务失败 | 发布时校验资源存在性；运行时 `ResourceDownloadProvider` 抛出明确异常；前端显示"资源缺失"告警 |
| JAR 文件安全性（恶意代码） | 安全风险 | **资源管理层（上传时）**：黑名单拒绝 `.exe` / `.bat` / `.cmd` / `.dll` / `.so` 等系统级可执行文件；**任务插件层（validate 时）**：白名单校验任务类型匹配——JAVA 只允许 `.jar`，PYTHON 只允许 `.py`，SHELL 只允许 `.sh`；后续可加沙箱执行 |
| 下载文件完整性被破坏 | 执行错误或安全风险 | `DefaultResourceResolver` 下载后校验 SHA-256 checksum 与元数据一致，不匹配时拒绝执行并清理临时文件 |
| 大文件下载耗时 | 执行启动慢 | 可按 checksum 做本地缓存，命中时跳过下载；临时文件复用 |
| 多节点部署时临时文件路径冲突 | 文件覆盖 | 临时目录使用 `Files.createTempDirectory("yak-task-")`，各节点独立无冲突 |
| 资源文件更新后任务行为变化 | 不可预期 | 发布时自动锁定 `resourceVersion`；Workflow 绑定 Revision 不受影响 |
| 磁盘空间被临时文件占满 | 磁盘告警 | `ResolvedResource.close()` 确保清理（含 warn 日志）；增加定时清理任务扫描 `yak-task-*` 前缀临时目录，删除超过 24 小时的残留 |
| 进程 stdout/stderr 输出过大导致死锁 | 任务卡死 | `JavaTaskExecutor` 在 `waitFor` 前异步消费 stdout/stderr，避免管道缓冲区满 |
| 指定版本资源被清理 | 生产执行失败 | `resolve(id, version)` 版本不存在时抛出 `IllegalStateException`，携带明确的版本信息；平台保留最近 N 个版本（N 可配置） |

## 9. 方案优势

1. **零模型变更**：不需要修改 `TaskDefinition` 核心模型，资源引用完全通过 `configJson` 承载。
2. **统一模式**：Python/Shell 也能受益——大脚本文件同样可以上传到资源管理，而不是内联编辑。
3. **版本可追溯**：资源管理已有 `version` + `checksum`，任务发布时锁定资源版本，审计链路完整。
4. **存储无关**：任务插件通过 `ResourceResolver` 抽象接口获取文件，底层 LOCAL/MINIO/HDFS 透明切换。
5. **安全隔离**：JAR 下载到临时目录执行，执行完毕清理，不会污染运行环境。
6. **权限复用**：资源管理的 `resource:view/download` 权限体系直接生效。
7. **渐进演进**：先支持 JAVA 资源引用，后续 Python/Shell 可平滑加入资源引用模式，不影响现有内联模式。

## 10. 实施路径

```
阶段 1（本迭代）                 阶段 2（后续）               阶段 3（远期）
───────────────────────────────────────────────────────────────────────
JAVA 资源引用模式                Python/Shell 资源引用        在线编译支持
+ ResourceResolver 能力          + 前端资源选择器复用          + javax.tools 内存编译
+ ResourceDownloadProvider 抽象  + 编辑器模式切换              + 多文件项目编辑
+ JavaTaskPlugin 新模块          + Shell 任务插件化            + Maven 依赖解析
+ JavaTaskConfig/JavaTaskExecutor+ Python/Shell validate 适配
+ JavaTaskExecutorAdapter        + 资源版本锁定发布
+ 前端 JAVA 节点类型 + ResourcePicker              + 按版本下载资源
+ Python validate 双模式适配
+ 发布时资源校验

改动范围：                      改动范围：                   改动范围：
• 新增 SPI 接口 (4 个文件)      • 各插件支持双模式            • 扩展 TaskDefinition
• 新增 Java 插件模块            • 新建 Shell 插件模块          • 集成编译工具链
• DevelopmentNodeType +JAVA     • 前端编辑器增强              • 模型需扩展
• 前端类型 +JAVA + ResourcePicker• 发布校验增强
• Python validate 适配
无核心模型变更 ✓                 模型小改 ✓                   模型需扩展 △
```

## 11. 后续阶段展望

1. **在线编译**：集成 `javax.tools.JavaCompiler`，支持单文件 Java 源码直接执行，降低简单脚本场景的门槛。
2. **多文件项目**：扩展 `TaskDefinition` 支持 `files[]` 字段，或在 `configJson` 中管理多文件内容，支持完整 Maven 项目结构。
3. **Maven 依赖管理**：平台代理 Maven 仓库，`configJson` 中声明 `dependencies`，执行时自动解析 classpath。
4. **资源缓存**：按 `checksum` 对已下载的资源做本地缓存，避免每次执行重复下载。
5. **沙箱执行**：通过 Docker/Container 隔离执行用户上传的 JAR，防止恶意代码影响平台。

## 12. 待优化项

> 以下为设计评审中发现的待修正点，在正式实现前需要逐一处理。

### 12.1 JavaTaskExecutor.execute() 资源泄漏风险

当前 5.5 节代码中，`ResolvedResource` 声明在 try-with-resources 外部，如果 `resolve()` 成功后、进入 `try` 之前抛出异常，资源不会被关闭：

```java
// 当前写法——有风险
ResolvedResource resource = config.resourceVersion() > 0
    ? resourceResolver.resolve(config.resourceId(), config.resourceVersion())
    : resourceResolver.resolve(config.resourceId());
// ↑ resolve 成功，但此处到 try 之间抛异常时 resource 未关闭
try (resource) {
```

应改为：

```java
try (ResolvedResource resource = config.resourceVersion() > 0
    ? resourceResolver.resolve(config.resourceId(), config.resourceVersion())
    : resourceResolver.resolve(config.resourceId())) {
  // ...
}
```

### 12.2 JavaTaskConfig.defaultJavaExecutable 优先级注释与代码不一致

Javadoc（第 445 行）写的优先级是 `globalEnvVars.JAVA_HOME > config.envVars.JAVA_HOME > 系统环境变量`，但代码实际是 taskEnv 优先：

```java
// 代码（第 456-458 行）：
if (taskEnv != null && taskEnv.containsKey(JAVA_HOME_ENV)) return taskEnv.get(JAVA_HOME_ENV);  // ← task 先
if (globalEnv != null && globalEnv.containsKey(JAVA_HOME_ENV)) return globalEnv.get(JAVA_HOME_ENV);
```

建议统一为"任务级覆盖全局级"语义（与 `PythonTaskConfig` 的 envVars 覆盖逻辑一致），修改 Javadoc 为：

```
优先级：config.envVars.JAVA_HOME > globalEnvVars.JAVA_HOME > 系统环境变量 > "java"
```

### 12.3 按版本下载需要存储层支持历史版本保留

7.3 节提到 `resolve(resourceId, version)` 用于生产运行，但当前资源管理的 `StorageOperator` 在 `replaceFile` 时是**覆盖写同一路径**（`resource.getStoragePath()`），历史版本的物理文件已被覆盖，无法按版本下载。

需要在实施前明确选择策略：

- **方案 A（推荐阶段 1）**：阶段 1 不支持按版本下载。`resolve(id, version)` 仅校验当前版本是否匹配，不匹配则报错。版本保留放阶段 2 实现。
- **方案 B**：阶段 1 同时实现存储层版本保留——上传时路径带版本号（如 `data/file-v3.jar`），`replaceFile` 不覆盖旧文件而是新增版本文件。

建议在文档中明确选择，避免实现时发现前置依赖未就绪。

### 12.4 发布时 checksum 字段未在 4.3 公共字段表中定义

7.2 节的发布流程中自动写入了 `checksum` 字段：

```json
{ "resourceId": 42, "resourceVersion": 3, "checksum": "abc123", ... }
```

但 4.3 的公共字段表只定义了 `resourceId`、`resourceVersion`、`timeoutSeconds`，缺少 `checksum`。应在 4.3 表格中补充：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `checksum` | string | 否（发布时自动填充） | 资源文件的 SHA-256 校验值，发布时由平台自动写入，运行时用于完整性验证 |

### 12.5 文件类型安全校验描述需明确作用层

风险表中的文件后缀黑名单描述存在歧义，且资源管理当前允许上传任意文件类型。建议明确为两层校验：

- **资源管理层（上传时）**：黑名单拒绝 `.exe`、`.bat`、`.cmd`、`.dll`、`.so` 等系统级可执行文件。
- **任务插件层（validate 时）**：白名单校验任务类型匹配——JAVA 只允许 `.jar`，PYTHON 只允许 `.py`，SHELL 只允许 `.sh`。

这样职责分离更清晰：资源管理做通用安全拦截，任务插件做类型专属约束。

### 12.6 ResolvedResource.deleteRecursively 实现与命名不一致

当前代码使用 `Files.list()` 只列出一层子项，不是真正的递归遍历。虽然对于临时目录下只有一个文件的场景完全够用，但方法名 `deleteRecursively` 和注释"递归清理临时目录"具有误导性。

两种修正方式：

- 改用 `Files.walkFileTree()` 实现真正的递归删除（更健壮）。
- 将方法改名为 `deleteDirectoryContents`，注释改为"清理临时目录内容"（更准确）。

### 12.7 阶段 1 中 Python validate 适配的范围需明确

阶段 1 实施路径包含了"Python validate 双模式适配"，但 Python 资源引用模式的执行链路要阶段 2 才实现。**阶段 1 的 Python 改动仅为校验逻辑预留**：允许 `content` 为空且 `resourceId` 存在时不报错，但 `PythonTaskExecutor` 暂不实现资源引用执行分支。阶段 2 再补全 Python 的 `ResourceResolver` 调用链路。
