# JavaDoc and Comment Convention

Scope: All repository-owned Java production and test sources.

This is the **single repository-wide JavaDoc and implementation comment convention**. It adopts the existing YakFlow/Flink-inspired standard for every Java module: comments explain contracts, invariants, ownership, and non-obvious behavior rather than restating names. `JAVA_RULES.md` and module rules own code design and formatting; they do not define alternative comment policies. Spotless + Palantir Java Format remains the sole mechanical Java formatter.

Existing Java modules may be migrated incrementally. **New and modified comments must follow this standard**; migrating comments does not authorize changing executable behavior or source string literals.

| Boundary | Documentation emphasis |
| --- | --- |
| Core, Runtime and Connectors | Public SPI, mailbox/thread ownership, source and sink lifecycle, checkpoint and recovery contracts |
| Business services and Task plugins | Business invariants, workspace isolation, definition versions, plugin validation boundaries |
| Datasource plugins | Connection ownership, credential handling, catalog metadata, JDBC/SSH lifecycles |
| DAO and Entity | Persistence mapping, unique constraints, sensitive columns, transaction/query boundaries |
| DTO, VO, request/response and model | Field semantics, enum codes, nullability, units, defaults, confidentiality and API compatibility |
| HTTP controllers | Observable API constraints and non-obvious compatibility behavior, not endpoint annotations restated |

## Language

- New and modified **JavaDoc** and implementation comments use **English only**, including DTO/VO/Entity field descriptions.
- Preserve original Java identifiers, protocol names, API references, and formal names such as `Checkpoint`, `SourceReader`, `SinkWriter`, `Mailbox`, `KeyGroup`.
- Do not translate log messages, exception text, data records, SQL, configuration keys or product-facing copy merely to satisfy comment conventions.
- Correct outdated descriptions as part of changing the related API; **do not** claim unsupported Flink features, distributed execution, exactly-once, committer, or rescaling.
- Existing, verifiable `@author` and `@since` provenance may remain. Do **not** invent a creator or creation date, or require these tags in any module. Here, `@since` denotes an actual API version where known, not the edit date.

## Type Javadoc

Every repository-owned production **top-level class, interface, record, enum or annotation** has a Javadoc description. Test types may rely on descriptive test names; add a type comment only when it explains otherwise hidden intent. Meaningful nested public/protected types should also have it.

A useful type comment identifies:
- The owned responsibility and boundary (contract, application service, persistence, runtime or plugin implementation).
- The important thread/lifecycle or mutable-state model when applicable.
- Important supported/unsupported behavior that a caller could otherwise misunderstand.

## Data model fields

- Add concise field JavaDoc to repository-owned DTO, VO, Entity, persistent model and cross-layer request/response contracts when fields carry business meaning.
- Document non-obvious nullability, units, enum/status codes, defaults, ordering, sensitive-data masking, and compatibility semantics; do not merely expand the field identifier into words.
- Do not comment loggers, injected dependencies, ordinary implementation state or every accessor for completeness.
- Persisted field descriptions must agree with the Flyway column meaning and the actual API representation.

A one-sentence type comment is fine for a simple value type. Do not restate the Java type name or invent a paragraph merely to make the file look documented.

## Method Javadoc

- **Application services and repositories**: describe important workspace scoping, transaction rollback, authentication/authorization, paging, updates, and failure guarantees. Do not restate obvious CRUD methods.
- **Task and datasource plugin SPI**: document accepted inputs, normalization, validation, connection ownership, and exception semantics where behavior is not clear from the signature.

- **Core public connector and execution APIs**: describe behavioral contracts, not just method names. Document non-obvious `@param`, `@return`, `@throws` and type parameters when they affect callers.
- **Runtime public/protected entrypoints**: document significant lifecycle transitions, mailbox/thread confinement, `CompletableFuture` completion/failure, Checkpoint ACK/durability, and ownership of resources.
- **Connector Base public SPI and extension points**: document record ownership (one buffer only), blocking fetcher versus mailbox execution, completion markers, nonblocking availability, size/timer/checkpoint flush, and whether close/cancel may flush or commit.
- **JDBC contracts**: document identifier quoting, schema/column ordering, native SQL placeholders, source split/replay and schema fingerprints, write mode and RowKind semantics, connection and statement ownership, transaction boundaries, retries and cancellation.
- **JDBC factory/dialect/catalog/converter interfaces**: document each nontrivial public method's preconditions and observable result. Include useful `@param`/`@return`/`@throws` tags for public operations whose meaning, ordering or failure modes are not obvious. A type-level description alone is **not sufficient** for an SPI with distinct methods.
- **Concrete JDBC implementations**: describe vendor-specific differences at class level and in overridden methods only where behavior differs from the documented interface. Use inherited Javadoc for straightforward overrides; do not duplicate the entire interface contract.
- An implementation overriding a well-documented interface does not have to repeat the same explanation; use inherited Javadoc implicitly, or `{@inheritDoc}` only when extra text is helpful.
- Obvious getters/setters, constructors delegating to a documented overload, and simple internal methods do not require boilerplate Javadoc. Non-obvious private algorithms can use short `//` explanations instead.
- Tests primarily describe intent through behavior-oriented test names. Comment *why* a race, injected failure or checkpoint ordering matters rather than commenting every Arrange/Act/Assert step.

## Formatting

- Write a complete summary sentence ending in a period. For public contracts, lead with what the operation **guarantees**, not "Gets/Creates/Handles the X".
- Use `<p>` between independent paragraphs, and only when a second paragraph adds information.
- Use `{@link Type#method()}` for Java declarations and `{@code ...}` for literals, enum values and short code.
- Include a real description in tags; do not mechanically add `@return the return value` or `@throws Exception on error`.
- Use `@see` where it actually helps callers locate an adjacent API or Flink-inspired contract; do **not** imply YakFlow implements Flink classes or refer to unavailable Flink types in JavaDoc links.
- Document supported behavior precisely: e.g. JDBC Source bounded at-least-once split replay is **not** a consistent full-database snapshot; JDBC Sink synchronous flush commits at-least-once, never XA or exactly-once. A close or cancel must not implicitly flush.
- Maintain correct Markdown/HTML entities inside Javadoc and avoid invalid HTML.
- Keep inline `//` comments short and immediately next to the relevant invariant.

## Good examples

**Core SPI:**

```java
/**
 * Polls available records without blocking the task mailbox.
 *
 * <p>Return {@link InputStatus#NOTHING_AVAILABLE} when no data is ready; the
 * runtime waits for {@link #isAvailable()} before polling again.
 *
 * @param output receives the records emitted by this call
 * @return the availability status after the current input step
 * @throws Exception if reading or delivering a record fails
 */
InputStatus pollNext(ReaderOutput<T> output) throws Exception;
```

**JDBC Dialect contract (SQL placeholder ordering):**

```java
/**
 * Builds a parameterized INSERT statement using the target schema's column order.
 *
 * <p>Each column contributes exactly one JDBC placeholder. The caller binds values
 * in the same order through the dialect's row converter.
 *
 * @param table the target table, including its optional catalog/schema
 * @param schema the resolved target columns in binding order
 * @return an INSERT statement with one placeholder per column
 */
String insertSql(TableId table, TableSchema schema);
```

**Connector Base lifecycle:**

```java
/**
 * Flushes records that have reached the output-owned buffer.
 *
 * <p>Checkpoint and end-of-input callers must reject an incomplete logical update.
 * Closing the output never implies a successful flush or transaction commit.
 */
void flush() throws Exception;
```

**Runtime concurrency:**

```java
// Post-barrier records must not exhaust the buffer before other producers align.
```

**Avoid:**

```java
/** Gets the task status. */
JobStatus getStatus();

// Set flag to true.
finished = true;
```

## Guardrails and verification

- `scripts/ci/verify_java_comments.py` checks objective rules: English-only Java comment tokens (not log strings, SQL, text blocks, or user-facing messages) and production top-level JavaDoc.
- During staged adoption the existing Backend Quality step scans YakFlow/Core plus the migrated Datasource and Task modules and the related DTO/VO/DAO/Controller packages. Other Java modules follow the same **normative standard** immediately, but legacy comments outside this initial scan are migration debt rather than new exceptions.
- `scripts/ci/test_verify_java_comments.py` guards scanner behavior. Keep a single comment checker; do not create a parallel workflow or change Spotless checks.
- Human review covers correctness, method-level contracts, stale notes, misleading links and filler comments. Do not enforce subjective comment quality via regex.
- Comment-only migrations must leave executable Java tokens, exception messages, logging text and runtime behavior unchanged.

## Source references

- Apache Flink: `flink-core/.../api/connector/source/Source.java`, `api/connector/sink2/Sink.java`.
- Apache Flink: `flink-runtime/.../streaming/runtime/tasks/mailbox/MailboxProcessor.java`, `runtime/source/coordinator/SourceCoordinator.java`.
- Apache Flink JDBC Connector: `flink-connector-jdbc-core/.../core/database/dialect/JdbcDialect.java`, `internal/JdbcOutputFormat.java`, `internal/executor/JdbcBatchStatementExecutor.java`.
- Apache Flink: `tools/maven/checkstyle.xml` JavadocType / JavadocMethod / JavadocParagraph / JavadocStyle checks.
