# Web App Rules

Status: Active

Scope: `yak-ops-ui/apps/web/**`。

目录与产品入口职责见 [Architecture](../../ARCHITECTURE.md)，通用限制见 [Frontend Rules](../../FRONTEND_RULES.md)。本文件只维护 App 层交互与状态约束。

## App Domain

任务定义、Task Detail 与运维入口分离，复用 `app/data-sync` 的共享实现，不复制离线/实时编辑器或 Execution 展示组件。Task Detail 留在数据集成内，只读展示当前 Task 的 Execution / Attempt 运行事实；离线与实时 Task Detail 统一使用左侧 Execution 列表与右侧详情的 master-detail 布局，右侧 PageHeader 固定，详情区域独立滚动，当前 Execution 仍通过 URL 查询参数保持可恢复。OFFLINE Task 列表允许对已上线且无活动 Execution 的任务执行一次手工运行；Editor 和 Task Detail 不承载运行/停止命令，REALTIME Start / Stop 与 Schedule Runtime 也不因此迁入数据集成页面。跨 Task 观察仍由运维中心负责。任务发布与执行语义见 [Data Sync Contract](../../../docs/capabilities/data-sync/README.md)；表单遵循 [Form Rules](FORM_RULES.md)。

Task Detail 的信息层级优先“执行结果 → 失败原因 → 指标 → 排障细节”：主视图不直接展示内部 Execution ID；失败原因前置到执行概览；仅在存在真实重试或等待重试时展示重试记录。选中 Execution 使用 `执行情况 / 配置快照 / 执行日志` 三个 Tab；`配置快照` 必须读取该 Execution 的冻结 definitionSnapshot，而不是当前 Task 定义，并按 `数据来源 / 数据去向 / 执行策略` 分组。Source 只承载读端与 Connector Source 参数，Sink 只承载写端与 Connector Sink 参数，Execution Strategy 承载版本、Checkpoint、超时与 Retry 等运行策略；前端只基于现有 Snapshot 字段分组展示，不在本层发明新的后端配置模型。readRows / writeRows 继续遵循后端持久化指标语义。

## App Shell

- 认证后产品复用同一参数化 AppLayout。数据集成、运维中心为 Workspace-scoped，管理中心不受当前 Workspace gate，也不显示 Workspace Switcher。
- Workspace-scoped Outlet 按当前 Workspace 身份重建，防止列表、选择和表单状态跨空间泄漏。页面填充父容器，不自行用 `calc(100vh - ...)` 扣减 Shell 高度。
- Router 只负责 URL 到产品入口的装配；Context 只持有应用级运行态。
- `navigation.ts` 定义完整产品 Registry；User Preference 只存稳定 product id，不复制标签、路由和图标。

## Product Launcher

- 一级真实产品只显示 `PRODUCT_MENU` 中当前用户收藏、且 Registry 仍存在的产品，按服务端 `sortOrder` 排列；无收藏显示空态，不注入默认产品。
- `所有产品` 是独立 `view-all` 入口，不混入真实产品数组。二级展示完整 Registry，收藏变更立即反映到一级。
- 收藏请求由 `service/preference` 承担。允许乐观更新；失败只回滚对应产品，不覆盖其他已完成变更。跨登录、跨设备持久化由服务端负责。
- 一级收藏产品支持快速取消：鼠标悬停或键盘聚焦该 Item 时，右侧显示 `X`；点击只更新该产品的 `favorite=false`，不得触发产品导航或关闭 Launcher。一级与二级必须复用同一收藏 mutation 与 optimistic rollback，禁止维护第二套本地删除状态。
- 两级菜单是 overlay，不改变 Sidebar / Outlet 布局，不增加外层阴影。二级必须紧贴同一 Launcher Track 的右边缘；Track 承担共同位移，关闭先收二级、再短暂错峰滑动 Track，全程不能产生中间空隙。
- 默认 Sidebar 不被关闭后的深色层残留遮挡。菜单触发器有 pointer，打开后原位切换为 X；X、Escape、路由变化、一次空白区点击都能关闭完整 Launcher。
- 二级打开时 `view-all` 保持激活。具体宽度、配色和时序在 [ProductLauncher](app/layout/ProductLauncher.tsx) 与 [AllProductMenu](app/layout/AllProductMenu.tsx) 维护，不在 Architecture 再复制参数表。

## Product Sidebar

菜单使用整行而非圆角卡片；选中态包含浅色背景、右侧高亮线和加粗文字，图标随选中状态变化。非选中项使用普通字重与轻量 hover；保留分组标签。

实际字体、间距与色值在 [ProductSidebar](app/layout/ProductSidebar.tsx) 维护；非选中图标跟随普通文字，选中图标使用高亮。

## Login Scene

登录插画由 [LoginCharacters](app/login/LoginCharacters.tsx) 私有维护，不进入共享 Yak UI。场景整体定位角色群，SVG 下边缘与角色共用的落地基线对齐；底部保留场景留白，不通过逐个平移角色抬高构图。缩放同时受可用宽度与视口高度约束，矮屏不裁掉默认角色轮廓；小屏继续由登录页隐藏插画。

橙色角色的出场终点、待机变形与减少动画模式共用同一套静态轮廓基准，调整饱满度时同步校准眼睛、眨眼和各嘴型的相对位置。保留角色遮挡顺序和黄色长嘴的轮廓外延伸；静态校准不顺带修改表单状态、鼠标跟随系数或动画时序。几何参数留在实现中，不在规则里复制参数表。

静态验收先启用系统减少动画偏好并刷新真实登录页，检查四个角色可见、共线落地、留白和五官比例；再检查普通桌面、矮屏、平板与小屏布局。恢复动画后检查橙色出场终点衔接及现有交互，不把独立 SVG 夹具截图当成完整产品交互验收。

## Enforcement

依赖与目录检查见 [Tooling](../../docs/tooling.md)。Shell 验收需在真实页面检查：切换 Workspace 不泄漏局部状态；三类产品入口可达；收藏失败回滚隔离；完整菜单各关闭路径只需一次操作，动画期间两级不分离。
