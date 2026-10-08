# Datasource Frontend Rules

Status: Active

Scope: `apps/web/app/datasource/**`、`apps/web/service/datasource/**`。

数据源能力、连接模式和常用类型行为见 [Datasource Contract](../../../../../docs/capabilities/datasource/README.md)；通用布局见 [Form Rules](../../FORM_RULES.md)。本文件维护页面特有的组合与交互约束。

## Ownership

`index.tsx` 拥有筛选、分页、选择、加载和弹窗状态；`table.tsx` 组合列、行操作与批量底栏；`form.tsx` 复用新增/编辑字段、校验、连接测试和保存。类型由 `service/datasource/types.ts` 定义，文案归本领域 i18n。

活动 Workspace 由 App 提供，页面不把 `workspaceId` 放入业务 DTO、不手工拼接 Workspace Header；空间切换的局部状态隔离由共享 Shell 负责。

## List

- 页面使用中性底色，筛选、Table 与分页放在同一无阴影、无额外圆角的白色内容面板。
- 工具栏顺序为“新增数据源 → 数据源类型 → 数据源名称”，不把新增入口放进 PageHeader extra；筛选使用 outlined 控件。
- 只使用共享 Table，采用 medium 密度；边框、高度、加载遮罩和分页位置遵循 [Table Contract](../../../../packages/yak-ui/docs/table.md)，不能用页面高度把分页推到底部。`pageSizeLabel` 提供“每页显示：”。
- 行内只有居中的“编辑｜删除”，不提供单行连接测试。表单连接测试与批量测试保留。
- 受控选择保留跨页 ID，筛选变化清空选择，单次最多 100 条；表头及底部全选只作用当前页可选记录。
- Table footer 承载批量删除与批量测试，右侧复用分页。删除二次确认；连接测试反馈逐条结果汇总，不能把失败伪装成成功。

## Create / Edit

新增为“类型选择 → 连接配置”两步 Modal，编辑直接进入同一配置表单且不可修改 dbType。类型选择区保留固定高度、分类及搜索；Item 仅显示图标与名称。常用类型按能力契约排序和补足，只映射实际支持类型；记录 usage 失败不阻断表单。

当前使用 `MYSQL / ORACLE / POSTGRE_SQL`；PostgreSQL 别名进入表单后归一为 `POSTGRE_SQL`，显示人类可读名称。新增类型须先有后端 Provider 再接入产品，不建设动态 Provider UI。

Create 默认环境为 DEVELOP，Edit 保留原环境；环境不是当前可编辑字段。访问身份与认证占位选项不扩张后端契约。MySQL 支持 AUTO / MYSQL_8 / MYSQL_5 驱动选择；PostgreSQL 与 Oracle 不提交 driverId。

## Connection Form

Create / Update / Connection Test 共用结构化 `connectionParams`，dbType 由外层字段路由，不重复塞入连接对象，也不由 App / Service 手工序列化成 JSON 字符串。

MySQL / PostgreSQL 使用结构化 Host / Port / Database 和只读 JDBC Preview。Oracle 接收完整原生 JDBC URL；校验非空与 Vendor 前缀，不在产品表单解析或重写 SID / Service Name / RAC，不展示高级参数、驱动版本或结构化预览。历史 Oracle properties 编辑时可透明保留。

PostgreSQL 采用数据库连接目标，不增加顶层 schema 或默认 public；默认 search path 通过通用 currentSchema 属性传递。端口默认值与 Preview 实现见 [form.tsx](form.tsx)，连接标准化和真实连接测试仍由后端负责。

## Advanced Properties

候选 Key 从领域 Service 调用后端 discovery 获取，不维护 Vendor 参数列表。推荐值用 Combobox 搜索/多选，多选拆成独立行；自定义属性仍可使用 Input。Key 非空且大小写不敏感去重；候选不是白名单，值的类型、枚举与 Provider 语义由后端校验。Oracle 不进入 discovery 流程。

## Boundary

不恢复 Summary 卡片、SSH UI、Driver Manager、动态表单或仅为局部状态建立的 editor / hooks 层。通用控件和表单视觉只引用对应契约，不在本领域复制第二份规则。
