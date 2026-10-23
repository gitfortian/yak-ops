# Frontend Rules

Scope: `yak-ops-ui/apps/**`、`yak-ops-ui/packages/**`、`yak-ops-ui/scripts/**`。

本文件维护持续有效的前端工程约束。当前产品与组件行为以源码、TypeScript 类型、测试和后端 API 为准。验证入口见 [Frontend Tooling](docs/tooling.md)，重大设计统一遵循仓库的 [YOIP 流程](../.github/CONTRIBUTING.md#yak-ops-improvement-proposals-yoips)。

## Ownership and Dependencies

- `apps/web` 是产品 Web Root；`packages/yak-ui` 提供业务无关的共享组件，公共导出以 `packages/yak-ui/src/index.ts` 为准。
- `apps/web/app/<domain>` 拥有页面、局部交互及状态；`app/layout` 拥有共享 Shell；`app/router` 拥有入口装配。离线与实时同步共用的编辑和运行态界面由 `app/data-sync` 承载。
- 产品请求沿 `app/<domain> → service/<domain> → service/http` 流动。`service/http` 是唯一 HTTP transport，负责统一结果、网络与认证失败处理；领域 Service 负责 API、参数适配和请求/响应类型。Service 不依赖 App。
- UI 依赖沿 `App → @yak-ops/yak-ui → Base UI / DOM` 流动。App 只消费 Yak UI 的公共导出；共享组件不承担领域接口、权限、数据读写或业务文案。
- 页面、Tab、弹窗等按真实用户流程建立功能目录；组件、Hook、局部类型、测试与小工具就近维护。跨页面状态才考虑应用级 `context`，跨领域代码只有出现稳定的真实复用后才上移。不要为了未来复用建立 `common` 或 `shared` 目录。
- Workspace-scoped 页面随 Workspace 身份隔离运行态；请求作用域由应用与 transport 按真实 API 契约维护，页面不手工拼接 Workspace Header。系统级接口按其后端契约处理。
- 领域 Service 只导出存在真实调用方的能力；UI 读取业务数据，不自行解析 transport 结果或将后端失败改写为成功。


## Feature Ownership and Components

- 按产品行为纵向组织 `app/<domain>/<surface>`：入口 `index.tsx` 负责路由接入与组装；`components/`、`hooks/`、`__tests__/` 只在确有内容时创建。不要为了套目录模板增加空层级。
- 让最小的行为所有者负责自己的数据、加载/错误/空状态、事件和生命周期。父组件仅协调真正跨区域共享的选择、提交、导航或必须保留的一致快照。
- 组件拆分以独立界面区域、交互生命周期及行为归属为依据，而不是行数。超过 400 行应检查职责，超过 600 行需在评审中说明保留原因；不是硬性失败阈值。
- 禁止把巨型页面等量搬到一个 `usePageModel` / `useEditor` Hook。Hook 应拥有清晰的查询、订阅或交互职责；不要为纯透传或重命名创建 Hook。
- 功能之间通过明确的公共入口或稳定契约合作；不要深度引用另一个功能的私有 `components/`、`hooks/`。迁移现有调用方时先定义公共入口，再逐步清理历史导入，不为保持旧路径创建永久转发层。
- 页面使用 `@yak-ops/yak-ui` 公共 API，不复制基础控件内部逻辑。共享控件不得引用产品路由、领域 Service、Workspace 业务状态或特定业务文案。
- Props 传递领域身份和真实类型；能够根据数据推导的状态不应与原始数据并行传递。避免无行为的包装组件和复制粘贴的类型别名。
- 组件的 `loading / empty / error` 在使用数据的区域呈现；骨架屏优先匹配实际内容，避免把多个独立区域的加载状态提升成一个页面级开关。

## State, Queries and Effects

- 本地交互状态 (`dialog open`、草稿、输入、选中) 留在其真实生命周期所有者；可分享的筛选、分页、Tab 和执行实例选择由 URL 管理。不能把同一身份同时交给 URL 和另一套独立状态。
- 服务端数据属于查询缓存，不属于手写的页面 `useState + useEffect` 缓存。新增或迁移的查询优先使用统一的 TanStack Query Client；查询 Key 必须包含实际影响结果的参数和 Workspace 身份，不能跨 Workspace 复用缓存。
- 领域 Service 保持 HTTP、接口适配、版本兼容与真实后端类型的职责。Query/Mutation 在消费行为附近维护，任务变更后失效相关查询，不额外维护平行的刷新计数器或请求结果镜像。
- 共享状态库只用于兄弟组件确实需要同一来源、或者工作流状态必须跨局部卸载保存的场景；不因组件层级较深就引入全局 Store。引入新状态依赖前需明确所有者。
- `useEffect` 用于订阅、计时器、DOM 或外部系统同步；不用于可在渲染时推导的状态、提交事件处理或复制 Query 的结果。轮询应随任务状态、页面可见性和组件卸载正确停止。
- 表单拥有输入默认值、校验、dirty 状态与 Payload 组装。跨 Task Definition、Schedule、Publish 的提交顺序与局部失败恢复必须由一个清晰的提交流程协调，不能因拆分 UI 变成多个互不知情的保存动作。
- 异步失败不能伪装为成功或空结果。请求取消、旧响应、重试与最终反馈都需有明确的所有者；现有手写请求在迁移前保留行为，不为采用新库而重写业务语义。

## Tailwind and CSS Boundaries

- 业务布局与普通交互状态默认使用 Tailwind CSS v4，并优先使用 Yak UI 的语义 Token、现有工具类和组件状态属性。禁止在新增业务 UI 中散落 `text-[#...]`、`bg-[#...]` 等重复魔法颜色；颜色需求先检查主题 Token。
- 优先使用标准 Utility 和语义状态 `data-*`；只有动态几何值、专用关键帧、第三方 DOM 覆盖、日志语法着色等 Tailwind 难以自然表达的场景才保留局部 CSS。已有 CSS 按所属功能逐步迁移，不批量机械删除。
- 局部 CSS 不得覆盖 Yak UI 通用基础组件，不依赖别的业务页面的私有 Class。交互状态需要覆盖亮暗主题与 `prefers-reduced-motion`；CSS 变量应从共享 Token 获取颜色，不另建平行的主题系统。
- 禁止为规避语义 Token 规则，把硬编码颜色从 `className` 挪到 `style`；内联样式只用于真正运行时决定的数值。新增通用视觉能力应在 `packages/yak-ui` 内实现。

## Testing and Architecture Enforcement

- 纯函数和业务转换使用就近单测；组件的输入、异步、焦点与错误状态使用组件行为测试；跨页面主要流程使用现有 Playwright。新增单测框架必须统一纳入 workspace，而不是各领域各装一套。
- Data Sync 拆分时至少保护草稿恢复、字段依赖重置、校验、任务保存、调度局部失败、上线失败、Workspace 切换及执行轮询；测试保持真实断言，不为迁移放松已有契约。
- `scripts/check-architecture.mjs` 以实际依赖方向为边界，禁止领域 Service 反向依赖 App、业务页面绕过 Service 访问 HTTP、Yak UI 反向依赖业务。支持别名、相对路径与动态导入的边界检查；不要禁止 `hooks/`、`editor/`、`model/` 这类本身有合理用途的目录名。
- 架构检查的新增规则需要回归测试。对尚未迁移的历史交叉依赖先记录并逐步收敛，不能用假通过、宽泛忽略或删测试来掩盖；格式、Lint、类型、契约、构建和浏览器测试分别报告实际结果。

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
