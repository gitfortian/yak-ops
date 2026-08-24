# Data Development Review

Review Data Development 改动时，先判断“truth 有没有换 owner”，再看代码是否漂亮。

## 必看

### Domain

- Node、Draft、Revision、Execution 是否仍然分离？
- Published Revision 是否保持 immutable？
- Editor Run 是否仍是 version-0 snapshot，而不是隐式 Publish？
- DATASET / DATA_SERVICE 是否仍然位于 Output Node lifecycle？
- Task Catalog 是否只是 projection，而不是 Revision truth？
- Lineage 是否仍是 derived evidence？

### Role

- 新类的名字是否表达真实职责？
- `Service` 是否真的是稳定应用入口，而不是因为“不知道放哪”？
- Parser / Resolver / Analyzer / Compiler / Worker / Outbox / Gateway 是否保持专业角色？
- 是否向 frozen `service` island 新增了文件？如果是，默认拒绝。

### Dependency

- Controller 是否只进入稳定入口？
- Application role 是否绕过 Repository 直接碰 DAO？
- Domain 是否出现 Spring JDBC / MyBatis / Task Runtime / Lineage Service？
- 新跨模块依赖是否有明确 corridor 和 truth owner？
- 是否出现新的 package cycle？

### Compatibility

纯结构重构必须确认没有顺手改变：

```text
REST route / request / response
Flyway / table schema
Draft optimistic revision
Task Plugin validation
Revision checksum / append-reuse
Task Catalog projection
Editor manual-run snapshot
Data Service contract
SQL lineage outbox / replacement semantics
```

## PR 建议说明

涉及领域或架构修改时，PR 至少写：

```text
Domain Impact Analysis
- Truth owner:
- Invariant/lifecycle impact:
- Domain Gap: yes/no

Architecture Impact Analysis
- Target subsystem:
- Stable entry / role:
- Dependency direction changed: yes/no
- Legacy service island changed: yes/no

Domain Compliance Report
- Rule preserved/implemented:
- Tests:
- Known gaps:
```

## 自动护栏

不要为了让重构通过而删除或放宽 architecture tests。

如果规则确实变化，必须在同一个 PR 中同时更新：

```text
REQUIREMENTS.md
DOMAIN.md
ARCHITECTURE.md
DEPENDENCIES.md
REVIEW.md
architecture tests
```

推荐验证：

```bash
mvn -pl yak-ops-business/yak-ops-business-data-development -am test
```
