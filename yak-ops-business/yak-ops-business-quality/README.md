# Yak Ops Data Quality

数据质量模块遵循 Yak Ops 统一业务分层：

```text
Controller -> DTO -> Service -> Domain
           -> Repository -> Adapter -> DAO
           -> BaseMapper / Mapper XML -> PO -> MySQL
```

分页统一遵循：

```text
DAO / Mapper             Repository / Service             HTTP
持久化查询  -> Adapter -> PageData<Domain> -> View ->     现有分页 VO / PagingData
```

约束：

- Repository 对外分页统一使用 `io.yak.framework.common.PageData<T>`，不暴露 MyBatis `IPage`。
- `QualityDomain.Page` 仅作为第一阶段兼容旧 Adapter 实现的废弃别名，不再作为 Repository 契约；后续清理 Adapter 时直接删除。
- Service 继续按现有 VO 契约输出 `records/total/current/pageSize` 等质量模块接口字段，本次不修改 HTTP JSON。
- DAO / Mapper 仍可使用持久化层自己的分页参数、limit/offset 和 MyBatis 能力。
- 新增分页功能不得再创建模块私有 `Page/XxxPage` 类型，应直接复用 `PageData<T>`。

全局分页边界规范由 Yak Framework `docs/pagination-conventions.md` 定义。
