# 数据同步手工 E2E 操作手册

状态：启用

适用范围：

- Yak Ops 数据同步产品主流程。
- 离线与实时手工端到端验收。
- 基于真实数据库、由人工执行的验证。

## 目标

手工 E2E 位于自动化单元测试、集成测试、验收测试之上，用于验证产品级完整链路。

它只回答一个问题：

> 用户能否准备真实数据库数据，通过 Yak Ops 配置数据同步，运行任务，并从头到尾验证最终数据库结果？

验证链路如下：

```text
源数据库
   ↓
数据源资源
   ↓
任务定义
   ↓
Yak Ops UI 操作
   ↓
数据同步业务层
   ↓
YakFlow 本地执行引擎
   ↓
连接器
   ↓
目标数据库
   ↓
结果验证
```

## 文档语言规则

E2E 文档面向人工执行和产品验收，说明文字统一使用中文。

必须遵循：

- 除 SQL 代码块外，标题、背景、步骤、操作说明、预期结果、验收清单、注意事项统一使用中文。
- SQL 代码可以保留英文关键字、表名、字段名、测试数据等原始内容。
- 系统真实枚举、状态值、字段名、配置键属于可执行标识，不强行翻译；使用行内代码保留原值，例如 `OFFLINE`、`REALTIME`、`APPEND`、`RUNNING`、`SUCCEEDED`、`CANCELED`、`readRows`、`writeRows`。
- 技术标识周围的解释必须使用中文，禁止出现整段英文说明。
- 新增或修改 E2E 用例时，同样遵循本规则。

## 手工 E2E 不是什么

手工 E2E 不替代自动化测试。

```text
单元测试
  验证局部行为

集成测试 / 验收测试
  验证运行时 / 连接器在真实数据库上的行为

手工 E2E
  验证用户可见的完整产品流程
```

不能为了覆盖所有参数组合，把手工 E2E 文档扩展成大型自动化测试矩阵。

## 用例设计规则

每个用例只代表一个有明确意义的用户场景。

必须：

- 从数据库准备开始，以结果验证结束。
- 从第一步到最后一步可以独立执行。
- 明确给出源端 / 目标端 DDL 和初始化数据。
- 使用确定性的 `e2e_*` 表名。
- 产品已有 UI 的步骤必须描述 Yak Ops UI 操作，不直接调用 Java Service、Repository 或数据库内部表。
- 当当前版本明确没有对应配置 UI 时，可以按具体 E2E 文档说明使用已经对外发布的 Data Sync HTTP Endpoint 完成前置配置；执行、运行态观察和结果验证仍优先使用 UI / 真实数据库。
- 记录关键任务参数。
- 明确实例预期生命周期。
- 使用 SQL 验证目标数据库。
- 在条件允许时明确写出预期业务数据。
- 提供验收清单。
- 提供清理 SQL。
- 文档中不得出现数据库密码、Token、SSH 密钥等凭证。
- 与 `main` 分支当前已经实现的能力契约保持一致。

禁止：

- 把“任务保存成功”当成同步成功。
- 把 `RUNNING` 当成实时 CDC 已正确工作的证明。
- 当场景依赖 UPDATE / DELETE 语义时，只验证行数。
- 声称具备 exactly-once 语义。
- 把一个场景扩展成数据库、写入模式、运行参数的笛卡尔积测试矩阵。
- 逐行复制自动化连接器验收测试。

## 执行约定

执行用例前：

1. 使用非生产数据库环境。
2. 确认所需数据源资源可以正常连接。
3. 使用干净的 `e2e_*` 表状态执行用例。
4. 除非文档明确声明依赖关系，否则不要复用其它 E2E 用例的数据。
5. 任何与文档预期结果不一致的情况，在原因明确前都按失败处理。

实时用例中，每完成一次源端变更，都要等待该变更出现在目标端后，再执行下一次 INSERT / UPDATE / DELETE。这样可以确保每次观察结果都能对应到单一操作。

## V1 必选手工 E2E

`v1.0.0` 发布前必须完整执行以下 5 个场景：

| ID | 场景 | 目的 |
| --- | --- | --- |
| OFFLINE-001 | [MySQL → MySQL / APPEND](offline/01-mysql-to-mysql-append.md) | 验证完整离线产品链路，以及 `APPEND` 保留目标端旧数据。 |
| OFFLINE-002 | [MySQL → MySQL / OVERWRITE](offline/02-mysql-to-mysql-overwrite.md) | 验证目标表先清空，再写入本次 Source 数据。 |
| OFFLINE-003 | [MySQL → MySQL / UPSERT](offline/03-mysql-to-mysql-upsert.md) | 验证按目标主键更新已有数据、插入新数据并保留无关旧数据。 |
| REALTIME-001 | [MySQL CDC → MySQL](realtime/01-mysql-cdc-to-mysql.md) | 验证 initial snapshot 以及 INSERT / UPDATE / DELETE CDC 完整产品链路。 |
| REALTIME-002 | [MySQL CDC 停止后续传](realtime/02-mysql-cdc-restart-continuation.md) | 验证同一 Task / definitionVersion 停止后复用 persisted offset，而不是 fresh snapshot。 |

正式执行结果统一记录在 [v1.0.0 Release Readiness](../../release/v1.0.0-readiness.md)。

## v1.1 Automation & Recovery 必选手工 E2E

v1.1 Release 前新增两条必选场景：

| ID | 场景 | 目的 |
| --- | --- | --- |
| AUTOMATION-001 | [离线 Cron 调度与停用](automation/01-offline-cron-disable.md) | 验证 Schedule 启用、Scheduler Next Run、自动 `SCHEDULE` Execution 与停用后不再触发。 |
| AUTOMATION-002 | [实时同步进程重启自动恢复](automation/02-realtime-process-restart-auto-recovery.md) | 验证旧 Execution → `LOST`、新 `AUTO_RECOVERY` Execution、同版本 CDC state 续传与 Stop 后不再恢复。 |

完整验收证据矩阵见 [v1.1 Automation & Recovery 验收](automation/README.md)。

Retry / Backoff、`SKIP_IF_RUNNING` 等难以通过稳定人工故障注入复现的状态机规则，以 `DataSyncAutomationAcceptanceIT` 作为 Release 硬证据；手工 E2E 不为覆盖状态矩阵而人为制造不稳定环境。

## 扩展规则

只有当新用例能够验证明显不同的产品行为时，才新增手工 E2E 用例。

后续适合增加的场景包括：

- MySQL → PostgreSQL 离线产品级兼容性。
- MySQL → Oracle 离线产品级兼容性。
- 离线 split + 并行 Reader 执行。
- MySQL CDC → PostgreSQL 产品级兼容性。
- MySQL CDC → Oracle 产品级兼容性。
- 进程重启后旧实例变为 `LOST`，新实例继续执行。

如果一个 PR 修改了上述行为，应在 PR 中明确指出实现完成后由哪个手工 E2E 用例验证该行为。
