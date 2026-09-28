# Alert

`Alert` 是 Yak UI 的页面内常驻警告提示条，用于前置条件、风险说明和容易踩坑的操作提示。

Source location:

```text
yak-ops-ui/packages/yak-ui/src/alert/
├── Alert.tsx
└── index.ts
```

## Usage

```tsx
import { Alert } from "@yak-ops/yak-ui";

<Alert>
  【提示】任务交互升级，增加了“发布”动作，在“启动”任务前，需要先保证已经进行了“发布”。
</Alert>;
```

## Contract

- V1 只提供 warning 语义，不增加 success / error / info 变体。
- 内容完全由调用方通过 `children` 提供，Yak UI 不持有业务文案。
- Alert 常驻在页面布局中，不自动关闭，也不拥有 action / dismiss / request 生命周期。
- 默认使用 warning 图标、浅黄色背景和 Yak UI Design Token。
- 组件使用 `role="alert"`，动态插入页面时能够向辅助技术声明警告内容。
- `className` 只用于布局和必要的定位调整，不用于重建另一套视觉样式。

## When to use

适合：

- 操作前必须满足的前置条件。
- 可能导致用户误操作的产品规则。
- 配置兼容性限制。
- 会造成数据、状态或执行结果差异的注意事项。
- 明确用于“避免踩坑”的持续提示。

不适合：

- 保存成功、连接失败等一次性操作结果，使用 Toast。
- 字段级校验，使用 Field / FieldError。
- 普通帮助文案或弱提示，直接使用页面说明文本。

产品层不要重复手写黄色 warning box。
