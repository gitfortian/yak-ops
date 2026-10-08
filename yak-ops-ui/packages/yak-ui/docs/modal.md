# Modal / Dialog / Drawer 契约

Status: Active

实现与公开类型：[Modal](../src/modal/Modal.tsx)、[Dialog](../src/dialog/)、[Drawer](../src/drawer/Drawer.tsx)。

## Contract

Modal 提供受控 open / onClose、可访问 title、内容及可选 footer。width 默认 640，centered 默认 false，maskClosable 默认 false；样式类及 style 只作为必要布局出口。业务负责步骤、字段、验证、保存和加载状态。

```tsx
<Modal open={open} onClose={close} title="编辑配置" footer={actions}>
  {content}
</Modal>
```

## Layout

默认水平居中、顶部偏移；centered 是显式垂直居中选项。具体视口留白和 max-height 以实现为参数入口。

只有 Body 滚动，Header / Footer 保持可见，没有 footer 时不产生空占位。短且稳定、能容纳于视口的表单可用 centered；长表单、Wizard、搜索/表格结果或容易增长的内容保持顶部偏移。产品层不通过 transform 或 className 冒充 centered。

## Interaction

Base UI Dialog 负责焦点约束、恢复、Escape 与外部点击机制。maskClosable=false 时外部点击不关闭；显式 true 才允许，X 和 Escape 仍请求关闭。onClose 表示请求，父组件仍拥有 open 状态。

定位与视觉动画分离：Popup 的淡入/轻微纵向运动不缩放内容，退出时 Backdrop 稍后结束；过渡属性必须与生成 CSS 实际变化匹配。具体时长和位移在实现维护，不在通用规则复制。reduced-motion 禁用过渡。

## Related Primitives

Dialog 用于组合式对话框及确认，不承接表单保存逻辑。Drawer 用于侧向展开；DrawerContent 默认 animated=true，需要立即开关时用 animated=false，不覆盖 transition class。DrawerBody 拥有滚动，焦点和关闭生命周期继续由 Base UI 管理。

## Boundary

不增加领域专属 Props，不复制业务状态，不把组件变成 AntD 兼容层。产品表单密度见 [Form Rules](../../../apps/web/FORM_RULES.md)。

## 人工验收

验证默认和 centered 定位、长内容仅 Body 滚动、无 footer 无占位；分别检查 maskClosable 两种值、X、Escape、焦点恢复及连续开关。缩窄视口、动态校验内容、退出动画和 reduced-motion 不得造成内容不可达或残影；Drawer 分别验证 animated 开关。
