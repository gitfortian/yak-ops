# Data Sync Task Editor Rules

Status: Active

Scope: `app/data-sync/task-editor.tsx` 及其编辑器辅助组件。

业务语义见 [Data Sync Contract](../../../../../docs/capabilities/data-sync/README.md)；App 职责见 [App Rules](../../APP_RULES.md)。实例列表和任务运维虽复用 `app/data-sync` 目录，但不是编辑器的职责。

## Ownership

DataSyncTaskEditorPage 显式接收 syncType，不按 URL pathname 猜模式。OFFLINE / REALTIME 共用数据源选择、绑定范围展示、Catalog 查询、字段映射和保存实现；差异留在模式参数、运行配置、文案及返回路径，不复制完整编辑器。

## Data / Validation

- REALTIME Source 只展示 MySQL，Target 只展示 MySQL / PostgreSQL / Oracle；最终拓扑、主键和兼容性由后端校验。
- Datasource 保存 ID、显示名称；表选择保留稳定身份与异步回显，遵循 [Select](../../../../packages/yak-ui/docs/select-motion.md)。数据源已绑定的数据库/Schema 不提供重复覆盖输入。
- 只读展示后端自动同名映射，不在前端重建 JDBC 类型兼容或 Transform。修改依赖字段后重新查询，过期结果不得覆盖当前选择。
- OFFLINE 只发送 runtimeConfig；REALTIME 只发送 realtimeConfig，Task writeMode 保持 APPEND。
- Retry Policy 使用共享“重试策略”折叠区配置：`maxAttempts` 范围 1～10，包含首次执行；1 表示关闭自动重试。启用重试时 `backoffSeconds` 范围 0～3600，表示固定等待秒数。前端只做同范围输入校验，最终仍由后端 DTO 校验。
- Retry Policy 同时适用于 OFFLINE / REALTIME，并随 Task 定义保存及 Execution definitionSnapshot 冻结。页面不得增加第二个 enabled 字段，也不得把 Retry 做成 Quartz / Schedule 配置。
- 不暴露 Debezium 状态目录、offset、schema history 或 serverId。

## Save / Publish

编辑接口要求任务未上线。已上线任务进入编辑页时只提供说明和返回入口，不形成另一条可编辑路径。

保存是 Task → 可选 OFFLINE Schedule；保存并上线是 Task → Schedule → Publish。Schedule 失败不继续 Publish，明确“任务已保存但调度失败”；Publish 失败保留已保存且未上线的任务，并返回可继续处理的编辑身份。保存不隐式启用调度，也不启动任务。

新任务未配置 Cron 且没有历史 Schedule 时为手工运行；已有 Schedule 不能用清空 Cron 冒充删除。Cron / Time Zone 保存与任务列表启停分开，运行时间从后端读模型获取。

OFFLINE Cron 使用 Yak UI `CronSchedulerPicker`，主表单不再要求用户直接手输表达式；高级 Cron 仍可在 Picker 内原样保留。Time Zone 使用 Select，默认 `Asia/Shanghai`，并保留历史已有 IANA Zone 值。Picker 的“未来 5 次执行时间”只调用 Data Sync Schedule Preview API，不在前端自行计算 Quartz 触发时间。普通 Cron/Time Zone 帮助文案和 Schedule enable/disable Alert 不重复铺在定义表单；启停属于 OFFLINE Task list。

## Layout

编辑区使用 [CollapseSection](../../../../packages/yak-ui/docs/controls.md#collapsesection)。基本信息、数据源、来源、去向、映射及 OFFLINE 调度默认展开；重试策略、运行参数默认收起。共享组件管理标题交互；数据同步内容继续保留白色 Card + border，不把业务内容外观改成共享组件默认规则。

OFFLINE 可编辑页面使用父容器高度、固定 PageHeader 与左侧局部滚动；右侧锚点导航在滚动区外，不能跟随内容滚走。REALTIME 当前保持原页面滚动方式，不把离线布局描述为两种模式都已采用。具体列宽与断点归 [task-editor.tsx](task-editor.tsx)，不照抄普通管理 Modal 的密度覆盖配置页。

## Risk Presentation

持久风险使用 [Alert](../../../../packages/yak-ui/docs/alert.md)：未上线前置条件、OVERWRITE、Split 快照限制、CDC 前置条件及执行定义变化后的重新快照。普通帮助文案默认不展示，优先通过字段名称、控件、Placeholder、状态与校验结果表达；只有数据风险、执行前置条件、阻断原因或重要执行差异允许额外提示，并遵循 [Frontend Rules](../../../../FRONTEND_RULES.md#ui-copy-minimalism)。不将“保存成功”当成同步结果。

## Verification

在真实页面验证离线/实时加载和异步回显、上线不可编辑、Schedule/Publish 部分失败、配置保存不自动运行，以及离线标题/锚点固定和折叠交互。业务结果验证复用已有 E2E，不在本规则记录一次执行的 PASS。
