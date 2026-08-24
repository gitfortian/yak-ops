package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DataSourceBeanNameConventionTest {

  @Test
  void controllerMappersDoNotReusePersistenceMapperSimpleNames() throws IOException {
    Set<String> persistenceNames = javaSimpleNames(productionRoot().resolve("dao/mapper"));
    Set<String> controllerNames = javaSimpleNames(productionRoot().resolve("controller/v1/mapper"));
    Set<String> duplicates = new LinkedHashSet<>(controllerNames);
    duplicates.retainAll(persistenceNames);

    assertThat(duplicates)
        .as("controller mapper simple names must not collide with MyBatis mapper bean names")
        .isEmpty();
  }

  private Set<String> javaSimpleNames(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) return Set.of();
    try (Stream<Path> paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .map(path -> path.getFileName().toString())
          .map(name -> name.substring(0, name.length() - ".java".length()))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/datasource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-datasource",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "datasource");
  }
}
