# 基础控件契约

Status: Active

Scope: Button / Spinner、输入与选择基础控件、Collapsible / CollapseSection。共享实现边界见 [Yak UI Rules](../UI_RULES.md)。

## Button / Spinner

[Button](../src/button/Button.tsx) 默认 type 为 button。primary 表面状态由共享主色 Token 派生，不在页面另设深蓝色套件。

loading 替换可见内容为一个居中的 [Spinner](../src/spinner/Spinner.tsx)，但原内容保留尺寸和可访问名称；设置 aria-busy 并阻止重复操作。loading 不套用普通 disabled 的淡化，默认允许保留焦点；显式 focusableWhenDisabled 仍由公开参数控制。不要切换 loading 文案或在文字旁再拼第二个转圈。

Spinner 使用 currentColor 的轻量圆头单弧，按 size 选择几何尺寸；独立使用时保留可访问状态说明，在 Button 内由按钮的忙碌语义承担反馈。具体尺寸、旋转时长以源码为参数入口，reduced-motion 停止旋转。

## Input Family

Input、PasswordInput、Textarea、NumberField 使用同一输入视觉语言。Input / Textarea / SelectTrigger / ComboboxInput 的 filled 为默认表面，outlined 为显式有边框表面；使用 variant，不在页面反复覆盖 border-transparent。

Input / Textarea / ComboboxInput 焦点及 SelectTrigger 展开只使用主色边框，不追加 focus shadow / ring。Textarea 只额外拥有多行高度、内边距与 resize；其他视觉跟随输入控件。尺寸、字体和圆角引用共享 Token，不复制数值表。

## Combobox

[Combobox](../src/combobox/Combobox.tsx) 用于可搜索选择，支持现有单选/多选与选中标记，复用输入及 Select 弹层 Token，但不因此自动拥有 Select 的定向动画。

使用空态时保留 Base UI 的已挂载 live region；有结果时空节点收缩为零布局空间，不能为消除留白直接隐藏或卸载 live region。过滤数据、远程查询及业务选项由调用方持有。

## CollapseSection

[Collapsible / CollapseSection](../src/collapsible/Collapsible.tsx) 区分底层 disclosure 与带标题的配置区壳。CollapseSection 默认展开，支持受控 open / onOpenChange 或 defaultOpen。

整行标题负责边框、hover、pointer、焦点与箭头，Panel 负责折叠和标题到内容的间距；extra 只作展示，不能在 Trigger 内嵌交互控件。内容布局、Card 表面和领域状态归调用方，不把某个页面的“平铺/卡片”偏好提升成所有组件的规则。

折叠保留键盘、disabled 和 reduced-motion 行为。尺寸与动画参数直接读取实现，不能在 UI_RULES 或业务页再维护副本。

## 人工验收

- 连续点击 loading Button 不重复提交，宽度和可访问名称保持，忙碌结束后恢复正常；检查三种常用尺寸与 reduced-motion。
- 输入、Textarea 和 Select/Combobox 在普通、焦点、展开、禁用状态保持共享视觉，表单错误不覆盖可访问关系。
- Combobox 在有结果/无结果间切换无多余留白，空态 live region 仍存在，单选/多选可操作。
- CollapseSection 检查整行点击、键盘、受控切换、默认收起、disabled、内容滚动及 reduced-motion，extra 中没有嵌套按钮。
