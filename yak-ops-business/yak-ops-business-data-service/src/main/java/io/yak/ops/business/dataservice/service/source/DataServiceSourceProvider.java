package io.yak.ops.business.dataservice.service.source;

import java.time.Instant;
import java.util.List;

/**
 * Supplies immutable, publishable query sources to Data Service without coupling the module to a
 * concrete authoring domain such as Data Development.
 */
public interface DataServiceSourceProvider {

  String sourceType();

  SourcePage list(int pageNo, int pageSize, String keyword);

  /** Resolves the source currently selected by its upstream release/catalog pointer. */
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
      Integer timeoutSeconds,
      String defaultPath,
      Instant updateTime) {}

  /** Server-only material used to create the immutable Data Service runtime snapshot. */
  record ResolvedSource(SourceDescriptor descriptor, String sql) {}
}
