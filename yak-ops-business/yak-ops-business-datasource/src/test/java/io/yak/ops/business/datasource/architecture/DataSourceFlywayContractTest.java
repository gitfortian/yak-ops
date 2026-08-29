package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceFlywayContractTest {

  @Test
  void datasourceOwnsDedicatedFlywayNamespace() throws IOException {
    String configuration = Files.readString(configurationSource());

    assertThat(configuration)
        .contains("classpath:db/migration/yak-datasource")
        .contains("yak_datasource_schema_history")
        .contains("MigrationVersion.fromVersion(\"0\")");
  }

  @Test
  void datasourceNamespaceContainsBaselineAndProjectScopeExpandMigration() throws IOException {
    assertThat(sqlFiles(migrationRoot()))
        .containsExactly(
            "V1__baseline_datasource.sql",
            "V2__add_project_scope_to_sql_execution.sql");

    String baseline = Files.readString(migrationRoot().resolve("V1__baseline_datasource.sql"));
    assertThat(baseline)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_source")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_sql_execution")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_sql_statement_execution")
        .contains("project_id")
        .doesNotContain("ALTER TABLE");

    String projectScope =
        Files.readString(
            migrationRoot().resolve("V2__add_project_scope_to_sql_execution.sql"));
    assertThat(projectScope)
        .contains("ALTER TABLE yak_ops_sql_execution")
        .contains("ADD COLUMN project_id BIGINT NULL")
        .contains("idx_yak_ops_sql_execution_project_started")
        .contains("idx_yak_ops_sql_execution_project_status_started");
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

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/datasource/config/DataSourceConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-datasource", "src", "main", "java", "io", "yak",
        "ops", "business", "datasource", "config", "DataSourceConfiguration.java");
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-datasource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-datasource", "src", "main", "resources", "db",
        "migration", "yak-datasource");
  }
}
