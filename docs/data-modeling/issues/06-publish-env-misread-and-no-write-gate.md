# 06: 发布弹窗把连接状态当环境显示「环境未知」，写路径缺环境门禁

**对应需求:** 第 2 轮验收 轮2 B6 前端半边（P1）；与轮1「数据源环境标注不一致：同步侧『数仓ods（开发）』、发布侧『数仓dim（环境未知）』」同源 | 阶段: P1

**What to build:** 发布是**写路径**，弹窗必须把目标数据源的真实环境（开发/测试/生产）显示出来，并据此决定要不要多一道确认。今天它显示「（环境未知）」，而登记表里这台数据源明明是 `DEVELOP`——因为代码读的是**连接状态** `connStatus`（`CONNECTED/DISCONNECTED/UNKNOWN`），却被拿去查环境的字典 `ENV_LABEL`（`PROD/TEST/DEVELOP`）。同时：读路径（从表/视图导入、逆向建模）有「确认非生产环境」门禁，**写路径只有「确认发布目标」而没有任何环境判定**。

**模块归属:** **modeling（纯前端，1 处显示 + 1 处门禁）**。库候选范围越界另开票 → [08](08-database-catalog-scope-leak.md)。

**Blocked by:** 无。

**Status:** 已解决（2026-10-29 落码 + 真机复验显示与写路径；PROD/断连/环境缺失三条分支**未真机验证**，理由见清单，本机不许改数据源登记）。

**根因证据（本仓已复核）**
- [logical-models/publish-dialog.tsx:111](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/publish-dialog.tsx) `setConnection(source.connStatus ?? null);`
- 同文件 `:39` `ENV_LABEL = { PROD:"生产", TEST:"测试", DEVELOP:"开发" }`，`:274` `{datasourceName || datasourceId}（{ENV_LABEL[connection ?? ""] ?? "环境未知"}）` —— **两个字典永不相交**，故恒为「环境未知」；`:239` `const disconnected = connection === "DISCONNECTED"` 才是 connStatus 的正确用法。
- 数据**已经在接口里**：后端 `yak-ops-common/.../bean/vo/modeling/LayerDataSourceVO.java:12` `record LayerDataSourceVO(String id, String name, String dbType, String environment, String connStatus)`；前端类型 `service/data-modeling/warehouse-planning/layers.ts:32-33` 同时有 `environment` 与 `connStatus`——`environment` 在发布弹窗**完全没被使用**。
- 读路径门禁参照物（同屏已有正确做法，不要另造）：`use-catalog-picker.ts:100-101` `nonProduction = datasource.environment !== "PROD"`、`blocked = nonProduction && !envConfirmed`，UI 见 `import-dialog.tsx:308-380`。
- 立票时未记录、复核时补上的两点：①`ENV_LABEL` 在 data-modeling 内**被复制三份**（`publish-dialog.tsx:39`、`import-dialog.tsx:38`、`reverse/index.tsx:47`），只有发布弹窗喂错了 key —— 漂移正是复制的代价；②`environment` 在前端类型里是 `string | null`（可为空），所以「未登记」是**可达状态**，不是假想分支。

**修法（已实施）**
1. 拆两个 state：`connection` 继续只喂 `disconnected`；新增 `environment`（`source.environment ?? null`）专供环境显示。
2. 显示改走 `describeEnvironment(environment)`（新函数落在 [publication-target.ts](../../../yak-ops-ui/apps/web/app/data-modeling/logical-models/publication-target.ts)：发布目标的纯前端规则已在该模块，且它零 `@/` 值导入、`test:unit` 已注册，能直接单测）；未登记返回 **「环境未登记」**，与 PROD 判定分开，不再用 `?? "环境未知"` 把缺值糊成友好文案。发布弹窗本地的 `ENV_LABEL` 副本**删除**（另两份属读路径、本次不动，避免顺手扩大改动面）。
3. 写路径门禁：`environmentMissing` 并入 `canPublish`，发布按钮 disable 且给 Alert「该数据源未登记环境，无法判断目标是否为生产库；发布已禁用，请先在数据源页面补全环境」——**不给忽略开关**（写库失败不可回滚，读路径那句「环境只作提示，不做拦截」不适用于写）。
4. PROD 确认升级：`environment === "PROD"` 时在「确认发布目标」勾选框内前置一句 danger 文案「目标数据源登记为生产环境，这张表会直接建在生产库中。」，勾选本身保持不变（修法 2 原样）。

**验收清单**
- [x] 发布 `dim_item_info`（数仓dim，DEVELOP）→ 弹窗显示「数仓dim（开发）」，不再出现「环境未知」
  - 真机：`/data-modeling/logical-models/108866872718319616` 点「发布」→ 数据源行 = **「数仓dim（开发）」**，`dlg.innerText.includes('环境未知') === false`，无多余 Alert。
  - 顺带取证：接口 `GET /layers/datasources` 7 台数据源 `environment` **全为 `DEVELOP`** → 旧文案「环境未知」确系读错字典，而非登记缺失。
- [x] 选库→「生成建表脚本」→ 确认框与发布门不破（DEVELOP 不加生产前缀）：确认句仍为「我确认在「dim」创建或校验物理表「dim_item_info」；…」，未勾选时 发布 `disabled=true`。
- [x] 逆向建模/从表导入既有「非生产环境」门禁零回归：真机选「数仓dim（开发）」后见「即将从「开发」环境读取字段， 请确认连接的是目标库；环境只作提示，不做拦截。」+「请先确认非生产环境」；该路径代码本次**未触碰**。
- [x] 前端契约测试：`ENV_LABEL` 只接受环境枚举（把「两个字典永不相交」锁成断言）
  - `publication-target.test.mjs` 新增 2 例：`names only the environment dictionary`（PROD/TEST/DEVELOP）与 `refuses to read a connection status as an environment`（`CONNECTED`/`DISCONNECTED`/`UNKNOWN`/`null`/`""` **全部**→「环境未登记」）。
  - 实跑：单文件 `tests 8 / pass 8 / fail 0`；整套 `npm run test:unit` → **tests 86 / pass 86 / fail 0**。
  - `npx tsc --noEmit` exit 0；`npx oxlint apps/web/app/data-modeling/logical-models --deny-warnings` 0/0；`npx oxfmt --check` 3 个改动文件全合规。
- [~] 连接断开时仍显示断连告警（`disconnected` 分支不破）—— 该分支代码原样保留（`connection === "DISCONNECTED"`），但**未真机验证**：需要把一台真实数据源打成断连，属共享开发库外部状态，不该为验证去动。
- [~] PROD 数据源 → 出现生产确认文案，未勾选无法发布 —— **未真机验证**：7 台登记全是 DEVELOP，制造 PROD 需修改数据源环境登记（改共享登记数据、且要改回）。`describeEnvironment("PROD")` 有单测；`production &&` 的 JSX 分支只能靠人工把某台临时改 PROD 后开弹窗复看。
- [~] `environment` 缺失 → 显示「环境未登记」并禁用发布 —— 同上，**未真机验证**（无一台缺失登记）。类型上是 `string | null` 故分支可达；单测覆盖返回值，禁用逻辑待人工把某台环境清空后复看。
