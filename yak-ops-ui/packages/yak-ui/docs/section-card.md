# SectionCard 契约

Status: Active

实现与 Props：[SectionCard.tsx](../src/card/SectionCard.tsx)。公共导入使用 `@yak-ops/yak-ui`。

## Contract

SectionCard 是页面内容区的轻量区块容器，只统一白色 Card 表面、标题左侧主色竖线、标题字重和标题到内容的间距。

公开参数只保留两个：

```ts
interface SectionCardProps {
  title: ReactNode;
  children: ReactNode;
}
```

`title` 负责区块标题，`children` 负责全部业务内容。Form、Table、Select、说明文字、空态及业务操作都由调用方组合。

```tsx
<SectionCard title="基本信息">
  <BaseInfo />
</SectionCard>
```

## Layout

SectionCard 复用 Yak UI `Card` 的 border、圆角、背景和文字 Token；自身提供统一的 16px 内边距。标题行使用主色 3px 竖线与 14px semibold 标题，标题与内容间距为 16px。

SectionCard 不渲染传统 CardHeader 的底部分隔线，也不改变 children 内部布局、宽度或滚动行为。

## Boundary

SectionCard 不提供 extra、description、loading、collapsible、size、headerStyle、bodyStyle 等扩展参数，不拥有请求、CRUD、权限、领域状态或操作按钮。

当页面需要右上角动作、描述、折叠或复杂 Header 时，继续组合 `Card / CardHeader / CardContent` 或已有 Yak UI Primitive，不扩张 SectionCard API 来兼容具体业务页面。

## Acceptance Criteria

- 仅传 title 与 children 即可得到统一区块 Card。
- 标题左侧主色竖线、标题字重、Card padding 和标题到内容间距稳定。
- children 可直接承载 Form、Table 或任意 ReactNode，不被组件重排或注入额外业务结构。
- 组件从 `@yak-ops/yak-ui` 公共导出，不引入新 UI 依赖。
