# 01: 物理发布不等于元数据登记——发布后资产目录仍为 0

**对应需求:** 第 1 轮验收 轮1 B2 的**登记半边**（P1） | 阶段: P1，**裁决先行（依赖 FYOIP-18）**

**What to build:** 「发布一张物理表」之后，业务同事在资产目录里就该看到它。今天看不到：`dim.dim_item_info`、`dwd.dwd_item_sales_detail` 两次发布都成功，但资产目录持续 `共 0 个资产 / 还没有采集任何资产`，必须**手工**新建采集任务（`e2e_dwd_item_sales_detail_crawl`）并点「立即运行」，资产才出现（`dwd_item_sales_detail` = `108869748396056576`）。产品把「物理发布」与「元数据登记」做成了两套互不通知的语义，而发布回报文案（「已采集元数据」）**冒充了后者**——文案半边已由 [data-modeling/11](../../data-modeling/issues/11-publish-copy-claims-metadata-collected.md) 承接（2026-10-30 已修复：发布回报改为「已回读物理表结构」并提示需采集后登记）。

**模块归属:** **datamap（登记链路）+ modeling（发布事件）**。跨模块。

**Blocked by:** `docs/yoips/FYOIP-18-model-published-asset-registration.md` 接受并落地。

**Status:** needs-decision（本票**不落码**，只做现状与验收口径）

**现状事实（真机取证）**
| 动作 | 结果 |
|---|---|
| 发布 `dim.dim_item_info` / `dwd.dwd_item_sales_detail` | ✅ 建表成功，DDL 含 COMMENT 与 PK |
| 发布回报文案 | 「已采集元数据：1 张表 / 9 个字段」 ← **与事实不符** |
| 资产目录 | 0 条，需手工建任务 + 立即运行 |
| 采集完成后 | 资产出现，但负责人「未指派」、数据域「未分类」、业务描述「暂无」、引擎注释「-」（见 [02](02-asset-detail-missing-model-facts.md)） |

**期望语义（FYOIP-18 的落点，此处只登记不自定）**
- 发布成功 → 产生一条**登记意图**（outbox/事件），由采集侧按已批范围异步登记为资产；
- 范围治理：哪些库/表允许自动登记必须是显式配置，不得默认全实例（与 [data-modeling/08](../../data-modeling/issues/08-database-catalog-scope-leak.md) 同一口径）；
- 未登记/登记失败必须在发布回报里**如实显示**，不能既说「已采集」又什么都不做。

**验收清单（裁决后）**
- [ ] 发布一张新表后，**不做任何手工采集**，资产目录在可接受时限内出现该表（或明确显示「待登记/登记失败」状态）
- [ ] 登记失败有可见原因与重试入口（当前发布失败只入历史、不给重试，见 data-map P2 票）
- [ ] 范围开关默认关闭（与既有决策一致：自动动作默认 OFFLINE），显式开启才自动登记
- [ ] 发布回报文案与真实语义一致（联动 data-modeling/11）
- [ ] 不产生重复资产：同一物理表重发布不新增行（指纹/upsert 口径沿用采集侧既有实现）
