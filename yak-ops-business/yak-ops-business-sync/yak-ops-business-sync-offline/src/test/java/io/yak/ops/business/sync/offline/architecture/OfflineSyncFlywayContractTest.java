package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineSyncFlywayContractTest {

  @Test
  void offlineSyncOwnsDedicatedFlywayNamespace() throws IOException {
    String configuration = Files.readString(configurationSource());

    assertThat(configuration)
        .contains("classpath:db/migration/yak-offline-sync")
        .contains("yak_offline_sync_schema_history")
        .contains("MigrationVersion.fromVersion(\"0\")");
  }

  @Test
  void firstReleaseNamespaceContainsExactlyOneBaseline() throws IOException {
    List<String> migrations;
    try (var paths = Files.list(migrationRoot())) {
      migrations = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }

    assertThat(migrations).containsExactly("V1__baseline_offline_sync.sql");
  }

  @Test
  void baselineDescribesFinalSchemaInsteadOfMigrationHistory() throws IOException {
    String sql = Files.readString(migrationRoot().resolve("V1__baseline_offline_sync.sql"));
    String upper = sql.toUpperCase();

    assertThat(sql)
        .contains("yak_offline_job_definition")
        .contains("yak_offline_batch_execution")
        .contains("yak_offline_job_execution")
        .contains("yak_offline_execution_event")
        .contains("yak_offline_sync_cursor")
        .contains("project_id")
        .contains("batch_id")
        .contains("logical_job_spec_json")
        .contains("idx_yak_offline_execution_project_created");

    assertThat(upper)
        .doesNotContain("ALTER TABLE")
        .doesNotContain("UPDATE YAK_OFFLINE_")
        .doesNotContain("WAVE 1")
        .doesNotContain("WAVE 2")
        .doesNotContain("WAVE 3")
        .doesNotContain("WAVE 4")
        .doesNotContain("WAVE 5")
        .doesNotContain("WAVE 6");
  }

  @Test
  void migrationContractIsDocumentedAndLinked() throws IOException {
    Path root = moduleRoot();
    assertThat(root.resolve("MIGRATIONS.md")).isRegularFile();
    assertThat(Files.readString(root.resolve("README.md"))).contains("MIGRATIONS.md");
  }

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/sync/offline/config/OfflineSyncConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return moduleRoot().resolve(
        "src/main/java/io/yak/ops/business/sync/offline/config/OfflineSyncConfiguration.java");
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-offline-sync");
    if (Files.isDirectory(local)) return local;
    return moduleRoot().resolve("src/main/resources/db/migration/yak-offline-sync");
  }

  private Path moduleRoot() {
    Path local = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("pom.xml"))
        && local.getFileName() != null
        && "yak-ops-business-sync-offline".equals(local.getFileName().toString())) {
      return local;
    }
    return Path.of(
        "yak-ops-business", "yak-ops-business-sync", "yak-ops-business-sync-offline");
  }
}
