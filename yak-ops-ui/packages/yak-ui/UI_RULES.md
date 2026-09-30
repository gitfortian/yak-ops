# Yak UI Rules

Status: Active

Scope: `yak-ops-ui/packages/yak-ui/**`。

通用前端约束见 [Frontend Rules](../../FRONTEND_RULES.md)。本文件拥有共享组件边界与实现共性；组件行为只在对应契约定义。

## Dependency Direction

```text
App → @yak-ops/yak-ui → Base UI → DOM
```

Base UI 是内部交互实现依赖，不是产品层 API。公共导出以 [src/index.ts](src/index.ts) 为准，不手工维护另一份组件树。

## Must

- Primitive 不含领域文案、请求、CRUD、权限策略或业务状态；公开 Props 保持业务无关、组合优先。
- 已有 Base UI 交互、键盘、焦点、disabled 和弹层生命周期由 Base UI 负责，Yak UI 拥有公共契约、布局、Token 和视觉状态。
- 样式使用 Tailwind 与 [styles.css](src/styles.css) 的共享 Token。主色及控件圆角/字体不在产品页面复制；真实全局变体用 CVA，不为每个局部差异扩张 Props。
- className / style 是必要的布局或定位出口，不是另一套视觉契约；产品层不得通过内部 DOM 选择器重建组件或覆盖动效。
- 动效修改同时检查实际生成的 CSS 过渡属性与 reduced-motion；不能只看 utility 名字猜测执行效果。
- 只在真实使用需求下增加组件，不为了兼容 AntD API、形式对称或未来计划建立新层级。

## Component Contracts

- Button / Spinner / 输入控件 / Combobox / 折叠控件：[基础控件](docs/controls.md)
- Select 值与标签、弹层组合及动效：[Select](docs/select-motion.md)
- Modal / Dialog / Drawer：[弹层](docs/modal.md)
- Table / Pagination：[表格](docs/table.md)
- PageHeader：[页面标题](docs/page-header.md)
- 常驻警告：[Alert](docs/alert.md)
- 瞬时操作反馈：[Toast](docs/toast.md)

小型组件没有独立行为说明时，读取对应源码、类型和适用的共性约束，不为每个实现补一份模板文档。Tabs、Menu、Tooltip、Popover 等继续组合使用；Badge 只提供视觉状态，业务状态映射留给调用方。

## Select Popup Composition

值/标签、搜索、刷新和 Footer 的边界统一见 [Select Contract](docs/select-motion.md#popup-composition)，本节只提供导航。

## Form Boundary

Form / Field 只拥有语义容器、Label、Description、Error、必填标记、可访问关系及视觉状态。字段依赖、动态 Schema、校验文案、请求、数据适配和提交生命周期归产品；产品组合布局见 [Form Rules](../../apps/web/FORM_RULES.md)。不重建 AntD 式表单运行时。

## Table Boundary

完整行为见 [Table Contract](docs/table.md)。业务层提供数据和列内容，Table 不拥有请求、筛选表单或后端分页适配；本节不再复制表格状态和边框规则。

## Upload Boundary

当前没有 Upload 产品组件。文件选择使用原生 input，上传请求、大小/类型策略、进度和重试留在产品层。

## Must Not

- App 直接导入 Base UI，或 Yak UI 依赖业务 Service。
- 为迁移方便盲目透传整套第三方 API，或新增 Ant Design / MUI / Chakra 等第二套框架。
- 把业务校验、网络请求、领域参数、页面状态或未来组件混入 Primitive。

## Boundary

Yak UI 是内部 workspace 包；没有真实跨应用消费者时不引入外部发布体系。公共参数、设计原因和可重复验收在对应组件契约维护；一次实施过程及验证结果不追加到本文件。
