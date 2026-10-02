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

出场编排保留角色差异：紫色由旋转方块展开，黑色下落时改变身体轮廓并在落地后回弹，橙色沿弧线进入、压缩后展开并衰减回位。变形使用固定的 SVG 坐标系支点，黑色脸部随身体弯曲，不随包围盒漂移；出场结束精确回到默认轮廓。黑色轮廓变形读取其 CSS 出场动画的实际进度，不另维护一套延迟与时长。提交或结果先于出场结束时立即收尾，不阻塞登录，也不在失败重试时重播出场。

鼠标与姿态跟随按 RAF 时间差计算：普通通道保持原 60 Hz 响应比例，橙色使用有阻尼的时间积分弹性跟随。长帧限制积分跨度，长时间挂起后丢弃旧弹性速度，不用积攒的帧数追赶。脸部先响应、身体稍后跟随，保留原目标映射与场景优先级；不要通过增加动作幅度掩盖时间计算问题。

表单通过 [Login Interaction](app/login/login-interaction.ts) 将焦点、密码显示模式和提交结果归一为唯一场景状态，CSS 表情与动画运行时使用同一状态。优先级为成功/失败及提交中高于密码可见，密码可见高于普通焦点与待机；密码仍可见时，移开焦点不能恢复围观。密码控件及其显隐按钮属于同一焦点区域，鼠标点击、Tab 和 Shift+Tab 在区域内切换不触发退出或重复入场。插画只接收状态与显示标记，不接收、读取或记录密码内容。

用户名与隐藏密码输入时，橙色睁眼并使用小圆嘴，紫色使用竖向输入嘴型。进入隐藏密码状态时紫色短暂前倾，随后回到普通关注姿态；持续输入不重播前倾，状态切换会中断旧过渡。显示密码时橙色闭眼回避，紫色转开，黑色保持独立的睁眼观察反应，不对所有角色统一闭眼。

[LoginPanel](app/login/LoginPanel.tsx) 拥有单次提交生命周期：校验通过才进入提交中，期间阻止重复提交；插画暂停待机呼吸并收敛跟随，不再响应鼠标目标。成功/失败反馈覆盖输入姿态，失败结束后根据当前焦点和显示模式恢复。组件卸载时使旧异步 UI 回调失效并清理反馈等待；这不表示取消了认证请求。减少动画模式下保留即时表情反馈，不执行姿态动画或强制等待反馈时长。

静态验收先启用系统减少动画偏好并刷新真实登录页，检查四个角色可见、共线落地、留白和五官比例；再检查普通桌面、矮屏、平板与小屏布局。恢复动画后检查橙色出场终点衔接，不把独立 SVG 或替代控件夹具当成完整产品交互验收。

交互验收覆盖：用户名与隐藏密码输入、紫色前倾后回位、输入框与显隐按钮间的键盘切换、快速反复切换显隐、明文保持时移开焦点、校验失败不发请求、慢请求期间重复 Enter/点击、成功和失败结果覆盖、失败恢复、请求或反馈期间卸载，以及减少动画模式。验证记录区分真实产品、React 组件夹具和静态采样；不使用真实密码作为截图或日志证据。

动效验收同时检查出场关键时刻与稳定状态：旋转方块、黑色弯曲下落、落地压缩及回弹、橙色最终轮廓衔接；检查 30/60/120/144 Hz 时间采样、快速反向移动、长帧恢复、出场期间提交、失败重试不重播，以及减少动画下刷新。数值采样不等于在真实高刷新率屏幕完成验收。

## Enforcement

依赖与目录检查见 [Tooling](../../docs/tooling.md)。Shell 验收需在真实页面检查：切换 Workspace 不泄漏局部状态；三类产品入口可达；收藏失败回滚隔离；完整菜单各关闭路径只需一次操作，动画期间两级不分离。
