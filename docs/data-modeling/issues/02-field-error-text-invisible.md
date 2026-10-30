# 02: 弹窗必填校验文案不可见（指标/规划/标准全线命中）

**对应需求:** 第 1 轮验收 轮1 B1（P0） | 阶段: P0

**What to build:** 空必填提交后，用户必须**看得见**「请输入 XXX」这句文案，读屏也能拿到 `aria-describedby` 指向的非空节点。第 1 轮实测：指标弹窗点保存后 `aria-invalid=true` 落在 `:rh:-name`、`:rh:-code`，其 `aria-describedby` 指向 `base-ui::ru:` / `base-ui::r12:` 节点，**节点存在但 `textContent=""`、`offsetHeight=0`** → 只见红框、零文案。

**模块归属:** **modeling（纯前端）**——共享字段组件 + 表单宿主。[shared/form-fields.tsx](../../../yak-ops-ui/apps/web/app/data-modeling/shared/form-fields.tsx)（`InputField`/`SelectField`/`TextareaField`）、13 个自有校验弹窗的 `<Form>` 容器、[warehouse-planning/node-dialog.tsx](../../../yak-ops-ui/apps/web/app/data-modeling/warehouse-planning/node-dialog.tsx) 的 3 个原生 `<Input required>`、[data-standards/lookup-section.tsx:303](../../../yak-ops-ui/apps/web/app/data-modeling/data-standards/lookup-section.tsx)（值编码行）。`packages/yak-ui/**` 属 upstream，**未改**。

**Blocked by:** 无。

**Status:** 已解决（2026-10-30 落码 + 真机复验，弹窗 12 个入口逐一走查；保存链路已实际发出 POST）。

**复验结论（先复现，后改码）**
第 1 轮结论**成立且比原票更严重**：不只是「文案看不见」，而是**弹窗的保存链路被两层校验器互相卡死**。

1. **Base UI `Form.Root` 是第二校验器，会吞掉应用的 submit。**
   `node_modules/@base-ui/react/form/Form.js:96-108`：`onSubmit` 先 `fields.forEach((f) => f.validate())`，再 `if (focusFirstInvalid()) { event.preventDefault(); return; }` —— 只要登记字段报 `validityData.state.valid === false`，应用的 `onSubmit` **一次都不会执行**。
   `node_modules/@base-ui/react/field/root/FieldRoot.js:81`：`const invalid = invalidProp === true || hasFormError;`，即 `Field invalid={Boolean(errors.x)}` 会被并入 Form 的登记状态。⇒ 应用校验器一旦设过 error，**后续每次提交都被 Form 拦掉**；应用既无法重跑校验清掉自己的文案，也无法保存。应用状态里的校验器与 Form 的原生校验器互为开关，谁也不让谁。
2. **原生 `required` 让首屏空提交走不到应用校验器，同时留下一个 0 高度的文案节点。**
   `InputField` 把 `required` 透传给 `<Input required>`；首提交被原生约束判 invalid → Form 直接 return → 应用 `errors` 仍是 `{}`。
   与此同时 `Field.Error` 的渲染条件是 `match === true || hasFormError || validityData.state.valid === false`，原生 invalid 会让它**挂载**；children 走 `rendered ? errorMessage : lastRenderedMessage`，此时应用文案为 `undefined` → 得到一个**有 id、无文本、`offsetHeight=0`** 的 `aria-describedby` 目标。这正是第 1 轮「只见红框、零文案」的物证。
3. **两类宿主的不对称**由此解释：逻辑模型编辑器是页面级表单，没有 Base UI `Form` 包裹，所以它的 `InputField` 文案一直正常（`editor.tsx` 走同一套共享组件）。命中面不是「指标专属」，而是**所有把 `Form` 当容器、又在 React state 里自校验的弹窗**。

**修法（已实施，四处）**
1. `shared/form-fields.tsx`：`InputField` 的原生 `required` → `aria-required`（`SelectField` 原本就是 `aria-required`，此改动让三者一致）；错误节点改为 `{props.error ? <FieldError match>{props.error}</FieldError> : null}` —— **只在有文案时挂载**，`FieldError` 仍是唯一渲染者，没有在调用点复制第二套 `<p>`。
2. 13 个自校验弹窗由 `<Form>` 改原生 `<form noValidate>` 并移除 `Form` 导入：data-standards/{category-dialog,standard-dialog,lookup-section,unit-section,naming-entry-section}、dimensions/dimension-dialog、metrics/{atomic,composite,derived,modifier,time-period}-section、warehouse-planning/{layer-dialog,node-dialog}。`noValidate` 同时中和残留的原生约束（如 standard-dialog 的是/否 radio）。
   未改的两个 `<Form>`：`metrics/metric-toolbar.tsx`、`data-standards/kind-toolbar.tsx` —— 它们没有 `Field invalid`，不参与本缺陷。
3. `warehouse-planning/node-dialog.tsx`：3 个裸 `<Input … required>` → `aria-required`（该文件不走共享 `InputField`）。
4. 走查中补的一处：`data-standards/lookup-section.tsx` 值编码行原本只有 `aria-invalid`，`<p>` 没有 id、也没被 `aria-describedby` 引用 → 补 `${id}-value-${index}-error` 关联，读屏能拿到同句。

**验收清单（真机读数，127.0.0.1:5173 + 真实 MySQL 数据源）**
- [x] 复验结论落本票尾
  - 修复前：`:rh:-name` `aria-invalid=true`、`aria-describedby=base-ui::ru:`、目标 `textContent=""`、`offsetHeight=0`。
  - 修复后同一路径：`:rh:-name` → `[请输入1至128个字符的中文名称]h16`。
- [x] 空必填提交 → `aria-describedby` 目标 `textContent` 非空且 `offsetHeight>0`
  - 原子指标：`name → [请输入1至128个字符的中文名称]h16`、`code → [须以小写字母开头，仅含小写字母、数字或下划线，最多64位]h16`、`process → [请选择所属业务过程]h16`；三个控件 `req=false ariaReq=true`，提交前 `desc=null`（无幽灵节点）。
- [x] 指标 5 段 + 数仓规划 + 数据标准弹窗逐一走查（同一改法生效）
  - 原子指标：上条；**保存链路实测**：填中文名/编码 + 选业务过程 → `POST /api/v1/data-modeling/metrics/atomic-metrics [200]` → 列表刷新、弹窗关闭、toast「保存成功」，新行 `票02校验复验金额 / ticket02_recheck_gmv` 落到列表。这条同时证明「Form 吞 submit」的死锁已解除。
  - 时间周期：`请输入1至128个字符的中文名称`(h16)、`须以小写字母…最多64位`(h16)。
  - 修饰词：`请选择所属数据域`(h16)、中文名(h16)、编码(h16)。
  - 派生指标：`请选择原子指标`(h16)、`请选择时间周期`(h16)。
  - 复合指标：中文名(h16)、编码(h16)、`表达式只能包含 +、-、*、/、括号、数字与派生指标编码`(h16)。
  - 数仓规划·新建分层：`请输入1至64个字符的分层名称`(h16)、`须以大写字母开头…最多32位`(h16)。
  - 数仓规划·新建数据集市（裸 `<Input>` 宿主）：中文名/英文名/编码/负责人 4 条全 h16，控件 `req=false ariaReq=true`；**保存链路实测**：`POST /api/v1/data-modeling/warehouse-planning/datamarts [200]`、弹窗关闭、树刷新，新集市 `票02集市验证 / ticket02_mart_recheck` 可见。
  - 数据标准·新建目录：`请输入1至128个字符的节点名称`(h16)。
  - 数据标准·新建度量单位：中文名(h16)、编码(h16)、英文名(h16)。
  - 数据标准·新建词根：`词根须以小写字母开头…`(h32，两行)、中文名(h16)、英文名(h16)。
  - 数据标准·新建标准代码：中文名/编码/英文名 3 条 h16；值编码行 → `aria-describedby=:rv:-value-0-error`、`[值编码必填，且不超过64位]h16`。
  - 数据标准·新建标准：中文名(h16)、编码(h16)。
  - 维度建模·新建概念维度：`请输入中文名`(h16)、`请输入英文名`(h16)、`请输入英文缩写`(h16)。
- [x] 逻辑模型编辑器既有可见文案零回归
  - `dwd_trade_order_detail`：清空表名后缀 + 表中文名 → `model-suffix → [请输入表名后缀]h16`、`model-cn-name → [请输入表中文名]h16`，两条都走 `aria-describedby`；保存被前端拦住、未发 PUT。
- [x] 补 DOM 契约测试锁死，防止退化
  - 新增 [shared/form-fields.test.mjs](../../../yak-ops-ui/apps/web/app/data-modeling/shared/form-fields.test.mjs)（4 条，已登记进 `test:unit`）：① 自校验弹窗不得再被 Base UI `Form` 包裹；② 共享字段不得出现原生 `required`；③ 错误节点必须由条件挂载（禁止 `match={Boolean(...)}` 这种「恒挂载」写法）；④ 文案只能走 `FieldError`，不得在调用点复制 `<p>`。

**实际执行过的门禁**
- `npx tsc --noEmit` → exit 0。
- `npx oxlint apps/web/app/data-modeling --deny-warnings` → 0 warnings / 0 errors（82 files）。
- `npx oxfmt --check`（仅本次触碰的 4 个文件）→ 全部合规。
- `npm run test:unit` → 104 pass / 0 fail（含本票新增 4 条）。

**边界偏差记录（需评审知悉）**
原票要求「只许在 `shared/form-fields.tsx` 内解决」。实测该边界**不足以修复**：根因一半在宿主（`Form` 容器 = 第二校验器），共享组件无法阻止 Form 拦提交。因此多改了 13 个弹窗的容器元素与 `node-dialog` 的 3 个裸 `required`；**仍未**改 `packages/yak-ui`，**也未**在调用点复制错误 `<p>`（判定见契约测试 ④）。

**残留 / 未做**
- `data-standards/category-dialog.tsx` 全仓 `oxfmt --check` 仍报红，来自**另一在途会话**的 `hint={…: undefined}` 行，与本票无关；按约定只上报、未顺手格式化。
- 发布弹窗、批量录入等非 `Field` 弹窗不属本票范围，未改。
- 本票不涉及后端；`node-dialog` 的负责人 `请选择有效负责人` 等文案为既有实现，未改口径。
