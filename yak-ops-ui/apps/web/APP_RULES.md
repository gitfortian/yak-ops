# Web App Rules

Status: Active

Scope: `yak-ops-ui/apps/web/**`。

目录与产品入口职责见 [Architecture](../../ARCHITECTURE.md)，通用限制见 [Frontend Rules](../../FRONTEND_RULES.md)。本文件只维护 App 层交互与状态约束。

## App Domain

任务定义、Task Detail 与运维入口分离，复用 `app/data-sync` 的共享实现，不复制离线/实时编辑器或 Execution 展示组件。Task Detail 留在数据集成内，只读展示当前 Task 的 Execution / Attempt 运行事实；Run / Start / Stop、Schedule Runtime 和跨 Task 观察仍由运维中心负责。任务发布与执行语义见 [Data Sync Contract](../../../docs/capabilities/data-sync/README.md)；表单遵循 [Form Rules](FORM_RULES.md)。

## App Shell

- 认证后产品复用同一参数化 AppLayout。数据集成、运维中心为 Workspace-scoped，管理中心不受当前 Workspace gate，也不显示 Workspace Switcher。
- Workspace-scoped Outlet 按当前 Workspace 身份重建，防止列表、选择和表单状态跨空间泄漏。页面填充父容器，不自行用 `calc(100vh - ...)` 扣减 Shell 高度。
- Router 只负责 URL 到产品入口的装配；Context 只持有应用级运行态。
- `navigation.ts` 定义完整产品 Registry；User Preference 只存稳定 product id，不复制标签、路由和图标。

## Product Launcher

- 一级真实产品只显示 `PRODUCT_MENU` 中当前用户收藏、且 Registry 仍存在的产品，按服务端 `sortOrder` 排列；无收藏显示空态，不注入默认产品。
- `所有产品` 是独立 `view-all` 入口，不混入真实产品数组。二级展示完整 Registry，收藏变更立即反映到一级。
- 收藏请求由 `service/preference` 承担。允许乐观更新；失败只回滚对应产品，不覆盖其他已完成变更。跨登录、跨设备持久化由服务端负责。
- 两级菜单是 overlay，不改变 Sidebar / Outlet 布局，不增加外层阴影。二级必须紧贴同一 Launcher Track 的右边缘；Track 承担共同位移，关闭先收二级、再短暂错峰滑动 Track，全程不能产生中间空隙。
- 默认 Sidebar 不被关闭后的深色层残留遮挡。菜单触发器有 pointer，打开后原位切换为 X；X、Escape、路由变化、一次空白区点击都能关闭完整 Launcher。
- 二级打开时 `view-all` 保持激活。具体宽度、配色和时序在 [ProductLauncher](app/layout/ProductLauncher.tsx) 与 [AllProductMenu](app/layout/AllProductMenu.tsx) 维护，不在 Architecture 再复制参数表。

## Product Sidebar

菜单使用整行而非圆角卡片；选中态包含浅色背景、右侧高亮线和加粗文字，图标随选中状态变化。非选中项使用普通字重与轻量 hover；保留分组标签。

实际字体、间距与色值在 [ProductSidebar](app/layout/ProductSidebar.tsx) 维护；非选中图标跟随普通文字，选中图标使用高亮。

## Enforcement

依赖与目录检查见 [Tooling](../../docs/tooling.md)。Shell 验收需在真实页面检查：切换 Workspace 不泄漏局部状态；三类产品入口可达；收藏失败回滚隔离；完整菜单各关闭路径只需一次操作，动画期间两级不分离。
