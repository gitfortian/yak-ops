package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for the permanent Lineage package roles and dependency boundaries. */
class LineageDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.lineage";

  private static final Set<String> DECLARED_TOP_LEVEL_PACKAGES =
      Set.of(
          "analysis",
          "collector",
          "config",
          "controller",
          "dao",
          "domain",
          "repository",
          "service");

  private static final Set<String> STABLE_SERVICE_TYPES =
      Set.of(
          "LineageMaintenanceService.java",
          "LineageQueryService.java",
          "LineageWriteService.java");

  private static final Set<String> ANALYSIS_ROLE_TYPES =
      Set.of("sql/SqlProjectionLineageAnalyzer.java");

  @Test
  void rootPackageMustRemainEmpty() throws IOException {
    Set<String> actual = new HashSet<>();
    try (Stream<Path> files = Files.list(productionRoot())) {
      files.filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .map(path -> path.getFileName().toString())
          .forEach(actual::add);
    }

    assertThat(actual)
        .as("Lineage root package is not a compatibility bucket")
        .isEmpty();
  }

  @Test
  void stableServiceSetIsExplicit() throws IOException {
    Set<String> actual = new HashSet<>();
    try (Stream<Path> files = Files.list(productionRoot().resolve("service"))) {
      files.filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .map(path -> path.getFileName().toString())
          .forEach(actual::add);
    }

    assertThat(actual)
        .as("Lineage stable application facade set is explicit")
        .containsExactlyInAnyOrderElementsOf(STABLE_SERVICE_TYPES);
  }

  @Test
  void analysisRoleSetIsExplicit() throws IOException {
    Path root = productionRoot().resolve("analysis");
    Set<String> actual;
    try (Stream<Path> files = Files.walk(root)) {
      actual =
          files.filter(path -> path.toString().endsWith(".java"))
              .map(root::relativize)
              .map(this::normalize)
              .collect(Collectors.toSet());
    }

    assertThat(actual)
        .as("Lineage analysis contracts are explicit role-owned types")
        .containsExactlyInAnyOrderElementsOf(ANALYSIS_ROLE_TYPES);
  }

  @Test
  void productionTopLevelPackagesMustBeDeclared() throws IOException {
    Set<String> actual = new HashSet<>();
    try (Stream<Path> paths = Files.list(productionRoot())) {
      paths.filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .forEach(actual::add);
    }

    assertThat(DECLARED_TOP_LEVEL_PACKAGES)
        .as("New top-level Lineage packages require an architecture contract update")
        .containsAll(actual);
  }

  @Test
  void controllerCannotReachPersistenceImplementation() throws IOException {
    assertNoImports(
        "controller",
        BASE + ".repository.",
        BASE + ".dao.",
        "com.baomidou.mybatisplus",
        "JdbcTemplate");
  }

  @Test
  void serviceCannotReachPersistenceImplementation() throws IOException {
    assertNoImports(
        "service",
        BASE + ".dao.",
        "com.baomidou.mybatisplus",
        "JdbcTemplate");
  }

  @Test
  void analysisPackageMustRemainSourceNeutral() throws IOException {
    assertNoImports(
        "analysis",
        "org.springframework",
        "com.baomidou.mybatisplus",
        BASE + ".controller.",
        BASE + ".service.",
        BASE + ".repository.",
        BASE + ".dao.",
        BASE + ".collector.",
        BASE + ".config.");
  }

  @Test
  void repositoryCannotReachHttpContracts() throws IOException {
    assertNoImports(
        "repository",
        BASE + ".controller.",
        ".controller.v1.dto.",
        ".controller.v1.vo.");
  }

  @Test
  void daoCannotPointBackToApplicationRoles() throws IOException {
    assertNoImports(
        "dao",
        BASE + ".controller.",
        BASE + ".service.",
        BASE + ".analysis.",
        BASE + ".collector.",
        BASE + ".repository.",
        BASE + ".domain.");
  }

  @Test
  void domainPackageMustRemainFrameworkAndPersistenceFree() throws IOException {
    assertNoImports(
        "domain",
        "org.springframework",
        "com.baomidou.mybatisplus",
        BASE + ".controller.",
        BASE + ".service.",
        BASE + ".repository.",
        BASE + ".dao.",
        BASE + ".analysis.",
        BASE + ".collector.");
  }

  @Test
  void serviceStereotypeCannotLeakIntoInfrastructureOrDomain() throws IOException {
    for (String packageName :
        Set.of("analysis", "collector", "config", "dao", "domain", "repository")) {
      Path root = productionRoot().resolve(packageName);
      if (!Files.isDirectory(root)) continue;
      for (Path file : javaFiles(root)) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(source)
            .as("@Service is reserved for stable application facades: %s", normalize(file))
            .doesNotContain("@Service");
      }
    }
  }

  @Test
  void legacyRootContractsCannotReturnAcrossProductionSources() throws IOException {
    Set<String> forbiddenImports =
        Set.of(
            "import " + BASE + ".LineageService;",
            "import " + BASE + ".LineageMaintenanceService;",
            "import " + BASE + ".LineageAsset;",
            "import " + BASE + ".LineageAssetDraft;",
            "import " + BASE + ".LineageAssetType;",
            "import " + BASE + ".LineageDirection;",
            "import " + BASE + ".LineageGraph;",
            "import " + BASE + ".LineageRelation;",
            "import " + BASE + ".LineageRelationDraft;",
            "import " + BASE + ".LineageRelationType;",
            "import " + BASE + ".SqlProjectionLineageAnalyzer;");

    try (Stream<Path> files = Files.walk(repositoryRoot())) {
      for (Path file : files.filter(this::isProductionJava).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        for (String forbiddenImport : forbiddenImports) {
          assertThat(source)
              .as("Legacy Lineage root contract in %s", normalize(file))
              .doesNotContain(forbiddenImport);
        }
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotAppear() {
    Path root = productionRoot();
    for (String forbidden : Set.of("common", "helper", "utils", "base")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad business bucket '%s' must not exist under Lineage", forbidden)
          .isFalse();
    }
  }

  private void assertNoImports(String packageName, String... forbiddenTokens) throws IOException {
    Path root = productionRoot().resolve(packageName);
    if (!Files.isDirectory(root)) return;

    for (Path file : javaFiles(root)) {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      String imports =
          source.lines()
              .filter(line -> line.startsWith("import "))
              .reduce("", (left, right) -> left + right + "\n");
      for (String token : forbiddenTokens) {
        assertThat(imports)
            .as("Forbidden dependency '%s' in %s", token, normalize(file))
            .doesNotContain(token);
      }
    }
  }

  private Set<Path> javaFiles(Path root) throws IOException {
    Set<Path> result = new HashSet<>();
    try (Stream<Path> files = Files.walk(root)) {
      files.filter(path -> path.toString().endsWith(".java")).forEach(result::add);
    }
    return result;
  }

  private boolean isProductionJava(Path path) {
    String normalized = normalize(path);
    return normalized.endsWith(".java")
        && normalized.contains("/src/main/java/")
        && !normalized.contains("/target/");
  }

  private Path repositoryRoot() {
    Path current = Paths.get(".").toAbsolutePath().normalize();
    if (Files.isDirectory(current.resolve("yak-ops-business"))) return current;

    Path candidate = current.resolve("../..").normalize();
    assertThat(Files.isDirectory(candidate.resolve("yak-ops-business")))
        .as("Unable to locate repository root from %s", current)
        .isTrue();
    return candidate;
  }

  private Path productionRoot() {
    Path moduleLocal = Paths.get("src/main/java/io/yak/ops/business/lineage");
    if (Files.isDirectory(moduleLocal)) return moduleLocal;

    Path repositoryRelative =
        Paths.get(
            "yak-ops-business",
            "yak-ops-business-lineage",
            "src/main/java/io/yak/ops/business/lineage");
    assertThat(Files.isDirectory(repositoryRelative))
        .as(
            "Unable to locate Lineage production source root from %s",
            Paths.get(".").toAbsolutePath())
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
