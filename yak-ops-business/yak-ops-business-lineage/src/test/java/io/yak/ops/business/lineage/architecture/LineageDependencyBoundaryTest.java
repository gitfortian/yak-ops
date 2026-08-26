package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for the permanent Lineage package roles and dependency boundaries. */
class LineageDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.lineage";

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry("controller", Set.of("domain", "service")),
          Map.entry("service", Set.of("analysis", "collector", "domain", "repository")),
          Map.entry("analysis", Set.of("domain")),
          Map.entry("collector", Set.of("analysis", "domain")),
          Map.entry("repository", Set.of("dao", "domain")),
          Map.entry("dao", Set.of("config")),
          Map.entry("domain", Set.of()),
          Map.entry("config", Set.of()));

  private static final Set<String> DECLARED_TOP_LEVEL_PACKAGES =
      ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet();

  private static final Set<String> STABLE_SERVICE_TYPES =
      Set.of(
          "LineageMaintenanceService.java",
          "LineageQueryService.java",
          "LineageWriteService.java");

  private static final Set<String> ANALYSIS_ROLE_TYPES =
      Set.of("sql/SqlProjectionLineageAnalyzer.java");

  private static final Map<String, Set<String>> EXTERNAL_BUSINESS_CORRIDORS =
      Map.of(
          "config/ConditionalOnLineagePersistence.java",
          Set.of(
              "io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled"),
          "config/LineagePersistenceConfiguration.java",
          Set.of(
              "io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration",
              "io.yak.ops.business.datasource.config.DataSourceProperties"));

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
  void topLevelImportsFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : packageDependencies()) {
      assertThat(ALLOWED_TOP_LEVEL_DEPENDENCIES.getOrDefault(
              dependency.sourcePackage(), Set.of()))
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredAndActualPackageGraphsRemainAcyclic() throws IOException {
    assertAcyclic(ALLOWED_TOP_LEVEL_DEPENDENCIES, "declared Lineage dependency graph");

    Map<String, Set<String>> actual = new HashMap<>();
    DECLARED_TOP_LEVEL_PACKAGES.forEach(key -> actual.put(key, new HashSet<>()));
    for (Dependency dependency : packageDependencies()) {
      actual.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
          .add(dependency.targetPackage());
    }
    assertAcyclic(actual, "actual Lineage import graph");
  }

  @Test
  void externalBusinessImportsStayInPersistenceConfigurationCorridor() throws IOException {
    assertThat(externalBusinessImports())
        .as("Lineage may reach Datasource only through its persistence configuration corridor")
        .containsExactlyInAnyOrderEntriesOf(EXTERNAL_BUSINESS_CORRIDORS);
  }

  @Test
  void persistenceImplementationUsesLineageOwnedCondition() throws IOException {
    String configuration =
        Files.readString(
            productionRoot().resolve("config/LineagePersistenceConfiguration.java"),
            StandardCharsets.UTF_8);
    String dao =
        Files.readString(
            productionRoot().resolve("dao/impl/LineageDaoImpl.java"),
            StandardCharsets.UTF_8);

    assertThat(configuration)
        .contains("@ConditionalOnLineagePersistence")
        .doesNotContain("@ConditionalOnDataSourceEnabled");
    assertThat(dao)
        .contains("import " + BASE + ".config.ConditionalOnLineagePersistence;")
        .contains("@ConditionalOnLineagePersistence")
        .doesNotContain("io.yak.ops.business.datasource.")
        .doesNotContain("@ConditionalOnDataSourceEnabled");
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

  private List<Dependency> packageDependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    for (Path source : javaFiles(root)) {
      Path relative = root.relativize(source);
      if (relative.getNameCount() < 2) continue;
      String sourcePackage = relative.getName(0).toString();
      for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
        String imported = importedType(line);
        if (imported == null || !imported.startsWith(BASE + ".")) continue;
        String suffix = imported.substring((BASE + ".").length());
        int separator = suffix.indexOf('.');
        String targetPackage = separator < 0 ? "root" : suffix.substring(0, separator);
        if (!sourcePackage.equals(targetPackage)) {
          result.add(
              new Dependency(
                  sourcePackage,
                  targetPackage,
                  imported,
                  normalize(relative)));
        }
      }
    }
    return result;
  }

  private Map<String, Set<String>> externalBusinessImports() throws IOException {
    Path root = productionRoot();
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (Path source : javaFiles(root)) {
      for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
        String imported = importedType(line);
        if (imported == null
            || !imported.startsWith("io.yak.ops.business.")
            || imported.startsWith(BASE + ".")) {
          continue;
        }
        result.computeIfAbsent(normalize(root.relativize(source)), ignored -> new LinkedHashSet<>())
            .add(imported);
      }
    }
    return result;
  }

  private String importedType(String line) {
    String value = line.trim();
    String prefix;
    if (value.startsWith("import static ")) {
      prefix = "import static ";
    } else if (value.startsWith("import ")) {
      prefix = "import ";
    } else {
      return null;
    }
    if (!value.endsWith(";")) return null;
    return value.substring(prefix.length(), value.length() - 1);
  }

  private void assertAcyclic(Map<String, Set<String>> graph, String label) {
    Set<String> nodes = new HashSet<>(graph.keySet());
    graph.values().forEach(nodes::addAll);
    Map<String, Integer> outgoing = new HashMap<>();
    Map<String, Set<String>> reverse = new HashMap<>();
    nodes.forEach(node -> outgoing.put(node, 0));

    for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
      for (String target : entry.getValue()) {
        outgoing.compute(entry.getKey(), (ignored, value) -> value + 1);
        reverse.computeIfAbsent(target, ignored -> new HashSet<>()).add(entry.getKey());
      }
    }

    ArrayDeque<String> ready = new ArrayDeque<>();
    outgoing.forEach(
        (node, degree) -> {
          if (degree == 0) ready.add(node);
        });

    int visited = 0;
    while (!ready.isEmpty()) {
      String node = ready.removeFirst();
      visited++;
      for (String dependent : reverse.getOrDefault(node, Set.of())) {
        int degree = outgoing.compute(dependent, (ignored, value) -> value - 1);
        if (degree == 0) ready.add(dependent);
      }
    }

    assertThat(visited).as("%s must remain acyclic", label).isEqualTo(nodes.size());
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
    Set<Path> result = new LinkedHashSet<>();
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

  private record Dependency(
      String sourcePackage,
      String targetPackage,
      String importedType,
      String relativePath) {
  }
}
