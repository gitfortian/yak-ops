# 基础控件契约

Status: Active

Scope: Button / Spinner、输入与选择基础控件、Collapsible / CollapseSection。共享实现边界见 [Yak UI Rules](../UI_RULES.md)。

## Button / Spinner

[Button](../src/button/Button.tsx) 默认 type 为 button。primary 表面状态由共享主色 Token 派生，不在页面另设深蓝色套件。

loading 替换可见内容为一个居中的 [Spinner](../src/spinner/Spinner.tsx)，但原内容保留尺寸和可访问名称；设置 aria-busy 并阻止重复操作。loading 不套用普通 disabled 的淡化，默认允许保留焦点；显式 focusableWhenDisabled 仍由公开参数控制。不要切换 loading 文案或在文字旁再拼第二个转圈。

Spinner 使用 currentColor 的轻量圆头单弧，按 size 选择几何尺寸；独立使用时保留可访问状态说明，在 Button 内由按钮的忙碌语义承担反馈。具体尺寸、旋转时长以源码为参数入口，reduced-motion 停止旋转。

## Input Family

Input、PasswordInput、Textarea、NumberField 使用同一输入视觉语言。Input / Textarea / SelectTrigger / ComboboxInput 的 filled 为默认表面，outlined 为显式有边框表面；使用 variant，不在页面反复覆盖 border-transparent。

Input / PasswordInput 额外支持显式 underlined：透明底色、单条底线、无外框圆角，文字与底线左端对齐。边框与圆角/内边距按 variant + size 组合，filled / outlined 既有样式不变；不要在页面通过内部选择器或 !important 模拟下划线。underlined 的错误底线在 hover / focus 后仍保持错误色，禁用后保留可识别的底线。

Input / Textarea / ComboboxInput 焦点及 SelectTrigger 展开只使用主色边框，不追加 focus shadow / ring。Textarea 只额外拥有多行高度、内边距与 resize；其他视觉跟随输入控件。尺寸、字体和圆角引用共享 Token，不复制数值表。

### FloatingLabelField

[FloatingLabelField](../src/input/FloatingLabelField.tsx) 是输入区域与真实 FieldLabel 的视觉组合，放在 Field 内，children 是一个 underlined Input 或 PasswordInput；htmlFor 必须指向控件 id。标签/必填标记仍复用 FieldLabel，不引入第二套表单状态、校验或提交运行时。错误提示放在组合之外。

```tsx
<Field name="account" invalid={Boolean(error)}>
  <FloatingLabelField htmlFor="account" label="账号">
    <Input id="account" variant="underlined" autoComplete="username" />
  </FloatingLabelField>
  <FieldError match={Boolean(error)}>{error}</FieldError>
</Field>
```

控件区域（含密码显隐按钮）聚焦或原生输入有内容时，标签缩小并上浮；空值失焦后回落，有值失焦不回落。Input 的 underlined 模式为未提供 placeholder 的情况补充空白占位符，使 :placeholder-shown、:autofill 与原生 DOM 值覆盖自动填充、受控回显及 defaultValue；它不是可访问名称。不依赖另一份 value 副本或只监听 onChange，不从子元素克隆 ref / 事件。浏览器不支持 :has 时保留上方固定标签。

标签移动使用区域内的 transform，不改变输入高度或推挤后续控件；字号与几何尺寸跟随输入的 size，横线/光标/显隐按钮保持稳定。不要在浮动区域内再塞入错误/描述行或其他输入控件。长标签在窄空间视觉截断但保留完整可访问名称；减少动画即时切换标签位置。默认的 filled / outlined 控件继续使用既有外置 FieldLabel。

### PasswordInput

PasswordInput 的 renderVisibilityIcon(visible) 是可选图形出口；默认仍使用原来的静态眼睛/斜线眼睛。渲染函数返回装饰图形，不嵌套按钮；显隐按钮的 aria-label、aria-pressed、type、点击、键盘与焦点仍由控件负责。业务回调在状态 updater 之外调用，同次操作只通知一次，不依赖动画完成回调。

切换只修改同一个输入节点的 type，不重建输入或清空值。鼠标切换在输入仍有焦点时恢复原选区，不对键盘聚焦的显隐按钮强制调用 input.focus；显隐按钮与输入属于同一浮动标签焦点区域。显式 disabled 同时禁用输入及显隐按钮；readOnly 仍允许查看内容。underlined 使用独立的足够大点击区域与零内边距，避免小尺寸 Button 的 padding 挤小图形；其余变体保留现有布局。图形跟随鼠标等场景动效留给调用方，不进入共享控件。

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
- FloatingLabelField 检查三种 size 下的空值、标签点击、聚焦、有值失焦、清空回落、受控更新、defaultValue、自动填充、浏览器恢复值及减少动画。比较前后底线/字段位置；错误和禁用状态正确，Label / Error 与控件仍关联。
- PasswordInput 检查默认及自定义图形、鼠标选区、键盘 Enter/Space、快速显隐、disabled/readOnly、非受控和受控值，输入节点不重建、内容不丢失；StrictMode 中同次显隐回调不重复。
- Combobox 在有结果/无结果间切换无多余留白，空态 live region 仍存在，单选/多选可操作。
- CollapseSection 检查整行点击、键盘、受控切换、默认收起、disabled、内容滚动及 reduced-motion，extra 中没有嵌套按钮。
