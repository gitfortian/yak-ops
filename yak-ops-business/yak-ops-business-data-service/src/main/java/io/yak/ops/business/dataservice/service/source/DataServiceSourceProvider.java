package io.yak.ops.business.dataservice.service.source;

import java.time.Instant;
import java.util.List;

/**
 * Supplies immutable, publishable query sources to Data Service without coupling the runtime module
 * to a concrete authoring domain such as Data Development.
 */
public interface DataServiceSourceProvider {

  String sourceType();

  /**
   * Whether service-facing definition fields are owned by the upstream authoring revision.
   *
   * <p>When true, Data Service treats name/path/contracts/maxRows/timeout/description/pagination as
   * immutable source definition and only owns runtime concerns such as enablement, auth, rate limits,
   * cache, circuit breaking and observability.
   */
  default boolean managesServiceDefinition() {
    return false;
  }

  SourcePage list(int pageNo, int pageSize, String keyword);

  /** Resolves the latest published immutable revision for one stable source identity. */
  ResolvedSource resolve(String sourceRef);

  record SourcePage(
      List<SourceDescriptor> records,
      long total,
      int pageNo,
      int pageSize) {
    public SourcePage {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  /** Management-facing source metadata. Executable SQL is intentionally not exposed here. */
  record SourceDescriptor(
      String sourceType,
      String sourceRef,
      String name,
      String sourceKind,
      String status,
      Long sourceRevisionId,
      Integer sourceRevisionNo,
      Long dataSourceId,
      Integer maxRows,
      Integer timeoutSeconds,
      String defaultPath,
      String description,
      Instant updateTime,
      Boolean paginationEnabled) {

    /** Keeps existing full descriptors source-compatible while pagination ownership evolves. */
    public SourceDescriptor(
        String sourceType,
        String sourceRef,
        String name,
        String sourceKind,
        String status,
        Long sourceRevisionId,
        Integer sourceRevisionNo,
        Long dataSourceId,
        Integer maxRows,
        Integer timeoutSeconds,
        String defaultPath,
        String description,
        Instant updateTime) {
      this(
          sourceType,
          sourceRef,
          name,
          sourceKind,
          status,
          sourceRevisionId,
          sourceRevisionNo,
          dataSourceId,
          maxRows,
          timeoutSeconds,
          defaultPath,
          description,
          updateTime,
          Boolean.FALSE);
    }

    /** Keeps existing unmanaged providers source-compatible while definition ownership evolves. */
    public SourceDescriptor(
        String sourceType,
        String sourceRef,
        String name,
        String sourceKind,
        String status,
        Long sourceRevisionId,
        Integer sourceRevisionNo,
        Long dataSourceId,
        Integer timeoutSeconds,
        String defaultPath,
        Instant updateTime) {
      this(
          sourceType,
          sourceRef,
          name,
          sourceKind,
          status,
          sourceRevisionId,
          sourceRevisionNo,
          dataSourceId,
          null,
          timeoutSeconds,
          defaultPath,
          null,
          updateTime,
          Boolean.FALSE);
    }
  }

  record ParameterContract(
      String name,
      String type,
      boolean required,
      String description,
      String example) {}

  record ResponseFieldContract(
      String name,
      String type,
      boolean nullable,
      String description,
      String example) {}

  record SourceContract(
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields) {
    public SourceContract {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
      responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
    }

    public static SourceContract empty() {
      return new SourceContract(List.of(), List.of());
    }
  }

  /** Server-only material used to create the immutable Data Service runtime snapshot. */
  record ResolvedSource(
      SourceDescriptor descriptor,
      String sql,
      SourceContract contract) {
    public ResolvedSource(SourceDescriptor descriptor, String sql) {
      this(descriptor, sql, SourceContract.empty());
    }
  }
}
