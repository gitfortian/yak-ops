# Frontend Rules

Scope: `yak-ops-ui/apps/**`、`yak-ops-ui/packages/**`、`yak-ops-ui/scripts/**`。

本文件维护持续有效的前端工程约束。当前产品与组件行为以源码、TypeScript 类型、测试和后端 API 为准。验证入口见 [Frontend Tooling](docs/tooling.md)，重大设计统一遵循仓库的 [YOIP 流程](../.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips)。

## Ownership and Dependencies

- `apps/web` 是产品 Web Root；`packages/yak-ui` 提供业务无关的共享组件，公共导出以 `packages/yak-ui/src/index.ts` 为准。
- `apps/web/app/<domain>` 拥有页面、局部交互及状态；`app/layout` 拥有共享 Shell；`app/router` 拥有入口装配。离线与实时同步共用的编辑和运行态界面由 `app/data-sync` 承载。
- 产品请求沿 `app/<domain> → service/<domain> → service/http` 流动。`service/http` 是唯一 HTTP transport，负责统一结果、网络与认证失败处理；领域 Service 负责 API、参数适配和请求/响应类型。Service 不依赖 App。
- UI 依赖沿 `App → @yak-ops/yak-ui → Base UI / DOM` 流动。App 只消费 Yak UI 的公共导出；共享组件不承担领域接口、权限、数据读写或业务文案。
- 页面私有组件与逻辑就近维护。应用级跨页面状态归 `context`，实际跨组件复用的 Hook 归 `hooks`；公共 `utils/types/constants` 仅承载真实跨领域需求。拆分以独立行为、实际复用和阅读复杂度为依据。
- Workspace-scoped 页面随 Workspace 身份隔离运行态；请求作用域由应用与 transport 按真实 API 契约维护，页面不手工拼接 Workspace Header。系统级接口按其后端契约处理。
- 领域 Service 只导出存在真实调用方的能力；UI 读取业务数据，不自行解析 transport 结果或将后端失败改写为成功。

## Design System and Interaction

- 页面统一使用 Yak UI 的 PageHeader、Table、Form/Field、输入控件、Select、Modal/Dialog、Alert 和 Toast 等公共组件。视觉样式使用 Tailwind 与共享 Token，组件内部样式和实际动画参数由源码维护。
- 普通管理表单采用紧凑横向 Label / Control 布局，默认 104px 标签列与 `small + outlined` 控件。字段依赖、校验和提交由领域页面负责；必填、错误和可访问关联使用 Yak UI Field 系列组件。
- 资源 Select 使用真实领域值及对应显示标签；搜索、刷新、异步候选和数据请求由调用方管理，键盘与弹层生命周期由共享组件管理。
- Table 负责通用选择、排序、分页与固定列呈现；请求、筛选、行操作及服务端分页适配归业务页面。固定列和分页行为通过公共参数配置，不由页面复制内部 CSS 或状态算法。
- Modal、Dialog 和 Drawer 使用共享的焦点、Escape、关闭及滚动行为；表单弹层默认由显式操作关闭，破坏性操作使用明确确认。产品层拥有 open、提交与错误状态。
- 常驻风险和关键前置条件使用 Alert，瞬时操作结果使用 Toast，字段级错误使用 FieldError；产品页面使用同一语义，不增加另一套消息组件。
- 交互保留键盘可达、焦点反馈、正确的语义关联和 `prefers-reduced-motion`。动画从当前状态连续过渡，隐藏、卸载及中断后及时清理相关资源。
- 公共组件的 Props、默认值、CSS 时序和详细行为由实现与类型定义；真实交互需求和验证证据决定是否扩展公共组件。

## UI Copy Minimalism

产品页面默认禁止新增解释性、帮助性、教程性或重复性文案。优先通过字段名称、控件类型、Placeholder、Disabled / Loading / Empty 状态、Field Error 与真实业务状态表达语义。

只有以下情况允许增加额外文案：

- 缺失信息可能造成数据丢失或不可逆操作。
- 用户必须知道的执行前置条件，或不提示会导致对实际执行结果产生明显错误理解。
- 当前操作被阻断，且控件状态或 Field Error 无法准确表达原因。

额外文案必须按需出现、紧邻相关操作并保持最短。禁止重复解释 Label / Select / Input / Switch 已经表达的含义；禁止把后端实现细节、技术契约或内部机制直接铺在普通业务表单中；禁止仅因“帮助用户理解”而增加持久说明；禁止用 Label + Description + Helper Text + Alert 重复表达同一语义。

当交互本身能够表达含义时，不增加额外文案。

## Quality and Verification

- 保留 Oxfmt、Oxlint、TypeScript、架构检查和生产构建。检查只验证其实际覆盖范围，不能用类型或构建通过替代浏览器交互验收。
- 维护真实的依赖、目录、HTTP 和 Workspace 边界检查；测试与实现同步演进，修复产生失败的源码，不放宽检查或增加宽泛的 lint disable。
- 按变更范围验证格式、类型、构建及相关可执行检查；需要交互验证的更改记录真实浏览器、键盘、响应式和减少动画场景。实际通过、跳过及未执行的验证分别报告。

## Documentation and Proposals

- 日常页面开发、视觉调整、组件内部实现与缺陷修复，通过源码、类型、测试和聚焦的 PR 表达；不为实现进度维护领域 `*_RULES.md`、组件契约 Markdown 或描述性架构副本。
- 对重大子系统、产品公开配置、监控接口或其他重要公开契约变更，按仓库统一的 YOIP 提案、讨论与接受流程进行。普通页面布局和内部组件重构不需要提案文档。
- 用户可观察的操作、兼容性或部署行为发生变化时维护用户文档；工程命令统一见 [Frontend Tooling](docs/tooling.md)。
- 规则与注释使用当前职责、行为、依赖和约束的正向表述；设计来源、取舍与实施记录保存在 YOIP 或 PR 讨论中。已发布的格式兼容和安全限制按真实契约准确描述。
