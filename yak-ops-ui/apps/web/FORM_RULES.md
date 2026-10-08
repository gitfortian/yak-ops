# Web Form Rules

Status: Active

Scope: `apps/web/app/**` 中的产品新增、编辑和配置表单。

本文件只定义产品如何组合控件；控件行为见 [Yak UI](../../packages/yak-ui/UI_RULES.md)，领域校验和提交状态由页面负责。

## Default Density

普通管理表单默认 Compact Horizontal Form：Label `104px`，Control `minmax(0, 1fr)`，列间距 `12px`，字段纵向间距 `10px`；Label 使用 `text-xs leading-4` 与 `pt-1.5` 顶部对齐。

Input、PasswordInput、Select、Textarea 默认使用 `small + outlined`，底栏 Button 使用 `small`。不能只因为组件默认值是 medium 就采用 medium；特殊配置编辑器如需不同布局，应在领域规则中说明，不把局部参数提升成全局默认。

## Horizontal Field

```tsx
<Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3" invalid={Boolean(error)}>
  <FieldLabel required htmlFor="field-id" className="pt-1.5 text-xs leading-4">
    字段
  </FieldLabel>

  <div className="min-w-0">
    <Input id="field-id" size="small" variant="outlined" />
    <FieldError match={Boolean(error)} className="mt-1">
      {error}
    </FieldError>
  </div>
</Field>
```

Label / Control 网格归产品表单，不为包裹该布局另建 FormItem / FormRow 体系。

## Validation

必填标记使用 FieldLabel / FieldRequiredMark，错误使用 FieldError 与控件语义关联；错误文字位于 Control 列下方。业务文案与校验逻辑留在领域，不手写红星、错误配色或第二套错误组件。

## Multi-control Row

Host + Port 等组合放进 Control 列，保持外层 Label 对齐。备注可使用双行紧凑 Textarea；具体最小高度、是否允许缩放由页面决定，不重建基础边框或焦点样式。

## Modal Form

新增/编辑使用 [Modal](../../packages/yak-ui/docs/modal.md)，破坏性确认使用 Dialog。短且稳定的表单可以显式 `centered`；长表单、Wizard、搜索或容易增长的内容保留默认顶部偏移。完整定位、关闭及滚动契约只在 Modal 文档维护。

记录选择弹窗的搜索 Input / Select 仍遵循 small + outlined；结果 Table 不强行塞进表单行布局。

## Select Value / Label

值、标签及异步受控回显遵循 [Select Contract](../../packages/yak-ui/docs/select-motion.md#value--label)。表单只拥有领域选项及加载状态，不在 Trigger 复制一次查找标签逻辑。

## Boundary

普通管理表单不默认采用上下式 Label 布局，不随意创建局部 Label 宽度、动态 Schema 或第二套表单运行时。领域的字段依赖、校验、请求和提交生命周期不下沉到 Yak UI。
