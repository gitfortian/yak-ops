package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HomeReadModelBoundaryTest {

  @Test
  void dataCenterMustComposeDomainReadersInsteadOfPersistenceInternals() throws IOException {
    String source = Files.readString(homeDataCenterSource(), StandardCharsets.UTF_8);

    assertThat(source)
        .contains(
            "OfflineExecutionOverviewReader",
            "WorkflowExecutionOverviewReader",
            "QualityExecutionOverviewReader")
        .doesNotContain(
            ".dao.mapper.",
            ".bean.po.",
            "LambdaQueryWrapper",
            "JdbcTemplate");
  }

  private Path homeDataCenterSource() {
    Path local = Path.of("src/main/java/io/yak/ops/boot/home/HomeDataCenterService.java");
    if (Files.isRegularFile(local)) return local;

    Path repositoryRelative = Path.of(
        "yak-ops-boot",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "boot",
        "home",
        "HomeDataCenterService.java");
    assertThat(Files.isRegularFile(repositoryRelative))
        .as("HomeDataCenterService source must be available")
        .isTrue();
    return repositoryRelative;
  }
}
