package io.yak.ops.business.dataservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceFlywayContractTest {

  @Test
  void dataServiceOwnsDedicatedFlywayNamespace() throws IOException {
    String configuration = Files.readString(configurationSource());

    assertThat(configuration)
        .contains("@DependsOn(\"opsDataSourceFlyway\")")
        .contains("classpath:db/migration/yak-data-service")
        .contains("yak_data_service_schema_history")
        .contains("MigrationVersion.fromVersion(\"0\")");
  }

  @Test
  void dedicatedNamespaceStartsWithOneConsolidatedVersion() throws IOException {
    List<String> migrations;
    try (var paths = Files.list(dedicatedMigrationRoot())) {
      migrations = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }

    assertThat(migrations)
        .containsExactly("V1__consolidate_data_service_governance_runtime.sql");

    String migration = Files.readString(
        dedicatedMigrationRoot().resolve("V1__consolidate_data_service_governance_runtime.sql"));
    assertThat(migration)
        .contains("idx_yak_ops_data_service_log_api_time_id")
        .contains("project_id")
        .contains("runtime_generation")
        .contains("yak_ops_data_service_rate_window")
        .contains("yak_ops_data_service_call_log_hourly");
  }

  @Test
  void legacySharedHistoryStopsBeforeTheDatasourceV11Boundary() throws IOException {
    List<String> migrations;
    try (var paths = Files.list(legacyMigrationRoot())) {
      migrations = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }

    assertThat(migrations)
        .contains(
            "V3__create_data_service_tables.sql",
            "V4__add_data_service_source.sql",
            "V5__add_data_service_api_security.sql",
            "V6__add_data_service_runtime_resilience.sql",
            "V7__add_data_service_documentation.sql",
            "V9__add_data_service_pagination.sql",
            "V10__add_data_service_overview_indexes.sql")
        .noneMatch(name -> name.startsWith("V11__")
            || name.startsWith("V12__")
            || name.startsWith("V13__"));
  }

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/dataservice/config/DataServiceFlywayConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-service", "src", "main", "java", "io", "yak",
        "ops", "business", "dataservice", "config", "DataServiceFlywayConfiguration.java");
  }

  private Path dedicatedMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-data-service");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-service", "src", "main", "resources", "db",
        "migration", "yak-data-service");
  }

  private Path legacyMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-datasource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-service", "src", "main", "resources", "db",
        "migration", "yak-datasource");
  }
}
