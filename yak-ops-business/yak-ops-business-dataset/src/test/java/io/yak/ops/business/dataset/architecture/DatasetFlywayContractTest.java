package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks Dataset Expand -> Backfill -> Contract migration boundaries. */
class DatasetFlywayContractTest {

  @Test
  void datasetNamespaceContainsProjectExpandAndContractMigrations() throws IOException {
    assertThat(sqlFiles(migrationRoot()))
        .containsExactly(
            "V1__baseline_dataset.sql",
            "V2__add_dataset_overview_indexes.sql",
            "V3__expand_project_scope.sql",
            "V4__persist_dataset_query_performance.sql",
            "V5__contract_project_scope.sql");

    String projectScopeExpand =
        Files.readString(migrationRoot().resolve("V3__expand_project_scope.sql"));
    assertThat(projectScopeExpand)
        .contains("ALTER TABLE yak_dataset")
        .contains("ADD COLUMN project_id BIGINT NULL")
        .contains("idx_yak_dataset_project_status_update")
        .contains("idx_yak_dataset_project_development_node");

    String queryPerformanceExpand =
        Files.readString(migrationRoot().resolve("V4__persist_dataset_query_performance.sql"));
    assertThat(queryPerformanceExpand)
        .contains("CREATE TABLE IF NOT EXISTS yak_dataset_query_performance")
        .contains("project_id BIGINT NULL")
        .contains("idx_yak_dataset_query_performance_project_time");

    String projectScopeContract =
        Files.readString(migrationRoot().resolve("V5__contract_project_scope.sql"));
    assertThat(projectScopeContract)
        .contains(
            "ALTER TABLE yak_dataset\n"
                + "    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID'")
        .contains(
            "ALTER TABLE yak_dataset_query_performance\n"
                + "    MODIFY COLUMN project_id BIGINT NOT NULL")
        .contains("performs no implicit backfill")
        .doesNotContain("UPDATE yak_dataset")
        .doesNotContain("UPDATE yak_dataset_query_performance")
        .doesNotContain("project_id = 1")
        .doesNotContain("project_id = 0")
        .doesNotContain("yak_dataset_version")
        .doesNotContain("yak_dataset_field");
  }

  private List<String> sqlFiles(Path root) throws IOException {
    try (var paths = Files.list(root)) {
      return paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-dataset");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-dataset",
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "yak-dataset");
  }
}
