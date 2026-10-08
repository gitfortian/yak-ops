# Select 契约与定向展开

Status: Active

实现及 Props：[Select.tsx](../src/select/Select.tsx)。本文件拥有 Select 的值/标签、组合、动效和验收，不定义业务查询行为，也不替 Combobox 承诺相同动画。

## Value / Label

领域 value 与显示 label 不同时，调用方通过 Select.items 提供映射；状态和 onValueChange 始终保存领域值，由 SelectValue 显示名称。不能在 Trigger 再做 options.find，不能把资源 ID 或内部复合键当名称。

异步加载的单选保持受控，当前无匹配值使用 null，不从 undefined 的非受控状态切换。value 与 label 天然相同的简单名称列表可以不传 items；不要把单选的 null 约定机械用于多选集合。

## Popup Composition

简单的 SelectContent + SelectItem 组合继续有效。header 可组合 SelectSearch，SelectSearch.extra 承载刷新等动作；footer 可组合 SelectFooter；emptyContent 只替换列表内容，保留 Popup 结构。

过滤、远程搜索、刷新、CRUD 和 Footer 回调归调用方。Yak UI 只拥有布局与键盘隔离：搜索输入的输入/光标按键不泄漏到列表 typeahead，Escape 仍可关闭，Tab 不被自定义逻辑吞掉。Footer 内控件同样保留自己的键盘行为。

## 视觉约定

使用 Popup 本身的 clip-path + opacity，从实际 data-side 对应的输入框侧逐步露出；bottom 从上向下，top 从下向上，left 从右向左，right 从左向右。方向读取碰撞后的属性，不只看请求的 side。

保留 Portal → Positioner → Popup 与完整定位尺寸；不缩放文字、搜索框或 Footer，不通过 height / max-height / grid-template-rows 动画改变定位几何。展开后的负向裁切余量保留阴影，不是布局 padding；调整阴影时必须复查裁切边界。

## 时序约定

展开 360ms / 淡入 160ms，曲线 cubic-bezier(0.16,1,0.3,1)；收回 220ms / 淡出 180ms，曲线 cubic-bezier(0.2,0,0,1)。箭头明确过渡 CSS rotate，打开/关闭分别跟随 360ms / 220ms；选项背景反馈 150ms，不延迟选择或淡化其余全部选项。

Base UI 的 starting / ending 状态驱动过渡和隐藏，不增加计时器、第二套 open 或弹簧动画；连续反向操作应从当前进度继续。ending Popup 禁止指针事件。面板、箭头与选项均遵循 reduced-motion，最终状态不留透明或裁切残影。

## 人工验收

- 异步选项、编辑回显：始终受控，显示 label，提交仍是 value
- 上下左右及视口碰撞：从实际输入框侧展开，定位尺寸不随动画变化
- 打开中关闭并再次打开：从当前进度反向，不跳到完整尺寸，无残余点击区
- 搜索、刷新、空态、Footer：插槽可用，按键隔离正确，内容变化后无裁切残留
- 长列表、不同尺寸、窄窗口：可滚动，文字不变形，圆角/边框/阴影完整
- 选择、Escape、Tab、外部点击：选择及关闭语义正常，焦点恢复，无焦点陷阱
- disabled、reduced-motion：禁用不能打开；减少动态效果时无过渡且状态正确

这些步骤需要真实 React / Base UI 页面；独立 CSS 夹具不能证明碰撞、卸载或焦点行为。结果记录遵循 [Tooling](../../../docs/tooling.md#verification-record)。
