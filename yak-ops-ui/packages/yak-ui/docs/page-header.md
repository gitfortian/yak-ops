# PageHeader 契约

Status: Active

实现与 Props：[PageHeader.tsx](../src/page-header/PageHeader.tsx)。公共导入使用 `@yak-ops/yak-ui`。

## Contract

title 必填，description、extra 可选；bordered 默认 false，仅控制底部分隔线。文案、按钮回调、路由与权限由产品层提供，PageHeader 不解释或重排业务动作。

```tsx
<PageHeader title="任务管理" extra={actions} bordered />
```

## Layout Contract

标题占剩余空间，extra 位于右侧且窄窗口可换行；没有 description / extra 时不保留对应占位。分隔线属于 Header 根节点。背景默认透明，视觉使用共享 Token。

外部页面宽度、Header 后的内容间距、是否固定及哪个区域滚动由调用方决定；className 不用于穿透内部结构改标题样式。

## Accessibility

标题保持真正的 h1 语义；extra 保留子控件的语义与键盘行为，不增加多余 ARIA role 或可聚焦外壳。

## Relationship to App Layout

TopBar 表达应用和产品位置，PageHeader 表达当前页面标题；不能合并两者。页面接入不改变组件的业务无关边界。

## Boundary

不内置面包屑、返回路由、Tabs、loading、sticky、请求、授权、标题编辑或动作数组；需要这些行为时先由页面组合，不为了预测需求扩张 API。

## Acceptance Criteria

检查 title-only、description、extra、bordered 的组合；省略插槽时无空节点；窄窗口标题和动作可读可操作；保留标题语义、公开导出与必要 className 布局能力。
