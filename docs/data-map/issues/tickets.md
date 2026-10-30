# 数据治理（数据地图）模块——Ticket 清单（验收轮 1+2 合并）

> 状态：v1.0（2026-10-29）
> 来源：两轮真机验收报告。建模侧「发布 → 资产登记」是同一闭环的两半，跨模块票在此登记，建模侧文案见 [docs/data-modeling/issues/11](../data-modeling/issues/11-publish-copy-claims-metadata-collected.md)。
> 编号从 **01** 起，模块内有序。

## 一、Ticket 总览

| 编号 | Ticket | 优先级 | 阻塞于 | 状态 | 归属 | 原报告 |
|---|---|---|---|---|---|---|
| 01 | 物理发布 ≠ 元数据登记：发布后资产目录仍为 0，需手工建采集任务 | P1（**裁决先行**） | FYOIP-18 | needs-decision | datamap + modeling | 轮1 B2 登记半边 |
| 02 | 资产详情未继承模型的中文名/负责人/数据域/业务描述，且无回链与血缘入口 | P1 | 01（口径同步） | ready-for-agent | datamap（+ 登记时携带模型事实） | 轮1 B2 详情半边 |
| 03~07 | P2 体验批（重新采集零反馈、整行不可点、徽章语义、placeholder、未知路由） | P2 | 各自独立 | backlog | datamap | 见 [p2-backlog-2026-10-29.md](p2-backlog-2026-10-29.md) |

## 二、与 FYOIP 的关系（不要绕过）

- 编号说明：本批立票时「发布即登记」提案记作 FYOIP-16；上游已把 16 号让给 `FYOIP-16-data-asset-governance.md`（Data Asset Governance，Accepted），该提案重编为 **FYOIP-18**，下文按新编号引用。
- `docs/yoips/FYOIP-18-model-published-asset-registration.md`＝「发布即登记 + 范围治理」提案；**01 的落地以 FYOIP-18 被接受为前提**。本批只做现状记录与文案纠偏，不抢跑实现。
- `docs/yoips/FYOIP-17-asset-entity-registry.md`＝统一 entity+关系+搜索；02 的「模型↔资产回链/血缘」应落在 FYOIP-17 的实体关系上，**不新造第二套关联表**。
- 提案自带的门禁：FYOIP-17 的 Phase 0 要等 FYOIP-18 发版后在生产上跑满两周、登记链路无未关闭的 P0/P1 才能开工（maintainer 2026-10-28 决定）；本批 02 若触碰 entity 注册，须等该门禁。

## 三、授权边界

- 可改前端：`yak-ops-ui/apps/web/app/data-map/**`。
- 后端 `yak-ops-business-datamap`（采集/资产服务）与建模侧发布服务可改，但**契约/存储级改动一律先出 FYOIP**，不写散装 `docs/engineering/*` 草稿。
- `scripts/**`、`packages/yak-ui/**` 属 upstream，禁改。

## 四、明确不做

- 不在本批实现「发布即采集/登记」的自动化（等 FYOIP-18）。
- 不给资产表挂监控四元组唯一约束——`record` 表挂不上唯一键的结论已在项目备忘，勿重复试。
- 血缘键必须走 `PhysicalTableAssetKey` 归一（既有契约），禁止另立字符串键，防裂节点。
