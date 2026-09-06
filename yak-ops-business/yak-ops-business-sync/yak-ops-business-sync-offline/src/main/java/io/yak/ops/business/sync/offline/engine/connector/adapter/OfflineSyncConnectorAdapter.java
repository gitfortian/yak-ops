package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.List;
import java.util.Objects;

/**
 * Connector-specific boundary between Yak Ops offline definitions and Link-Up endpoint options.
 *
 * <p>The factory owns job-level orchestration. Implementations own connector option semantics and
 * execution-time datasource translation. Logical JobSpec construction cannot access DataSourcePO;
 * datasource credentials are only available during {@link #resolveForExecution(ExecutionContext)}.</p>
 */
public interface OfflineSyncConnectorAdapter {

  boolean supports(String connectorId, Role role);

  boolean requiresDataSource(String connectorId, Role role);

  BuildResult build(BuildContext context);

  void resolveForExecution(ExecutionContext context);

  enum Role {
    SOURCE,
    SINK
  }

  record BuildContext(
      String connectorId,
      Role role,
      String mode,
      String jobName,
      JsonNode config,
      JsonNode channel,
      ObjectNode options,
      int batchSize,
      int fetchSize,
      List<String> sourceTables) {

    public BuildContext {
      Objects.requireNonNull(connectorId, "connectorId");
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(mode, "mode");
      Objects.requireNonNull(jobName, "jobName");
      Objects.requireNonNull(config, "config");
      Objects.requireNonNull(channel, "channel");
      Objects.requireNonNull(options, "options");
      sourceTables = sourceTables == null ? List.of() : List.copyOf(sourceTables);
    }
  }

  record BuildResult(
      ObjectNode options,
      String tableView,
      List<String> sourceTables) {

    public BuildResult {
      Objects.requireNonNull(options, "options");
      sourceTables = sourceTables == null ? List.of() : List.copyOf(sourceTables);
    }
  }

  record ExecutionContext(
      String connectorId,
      Role role,
      String endpointLabel,
      DataSourcePO dataSource,
      ObjectNode options) {

    public ExecutionContext {
      Objects.requireNonNull(connectorId, "connectorId");
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(endpointLabel, "endpointLabel");
      Objects.requireNonNull(options, "options");
    }
  }
}
