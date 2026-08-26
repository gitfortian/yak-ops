package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer;
import io.yak.ops.business.lineage.service.LineageMaintenanceService;
import io.yak.ops.business.lineage.service.LineageQueryService;
import io.yak.ops.business.lineage.service.LineageWriteService;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Locks the Lineage types that neighboring business modules may compile against. */
class LineagePublicApiBoundaryTest {

  private static final String BASE = "io.yak.ops.business.lineage";

  private static final Set<String> EXPORTED_TYPE_ROOTS =
      Set.of(
          BASE + ".analysis.sql.SqlProjectionLineageAnalyzer",
          BASE + ".domain.LineageAsset",
          BASE + ".domain.LineageAssetType",
          BASE + ".domain.LineageDirection",
          BASE + ".domain.LineageGraph",
          BASE + ".domain.LineageRelation",
          BASE + ".domain.LineageRelationType",
          BASE + ".service.LineageMaintenanceService",
          BASE + ".service.LineageQueryService",
          BASE + ".service.LineageWriteService");

  private static final Set<Class<?>> STABLE_ENTRY_TYPES =
      Set.of(
          SqlProjectionLineageAnalyzer.class,
          LineageMaintenanceService.class,
          LineageQueryService.class,
          LineageWriteService.class);

  private static final Set<String> FORBIDDEN_SIGNATURE_TOKENS =
      Set.of(
          BASE + ".config.",
          BASE + ".controller.",
          BASE + ".dao.",
          BASE + ".repository.",
          BASE + ".domain.LineageAssetDraft",
          BASE + ".domain.LineageRelationDraft");

  @Test
  void neighboringProductionModulesUseOnlyDeclaredPublicTypes() throws IOException {
    Map<String, Set<String>> consumers = externalLineageImports();

    for (Map.Entry<String, Set<String>> entry : consumers.entrySet()) {
      String imported = entry.getKey();
      assertThat(imported)
          .as("Wildcard imports cannot define a stable Lineage API: %s", entry.getValue())
          .doesNotEndWith(".*");
      assertThat(isDeclaredPublicType(imported))
          .as("Undeclared Lineage API import %s used by %s", imported, entry.getValue())
          .isTrue();
    }
  }

  @Test
  void declaredPublicTypeRootsExistAndArePublic() throws ClassNotFoundException {
    for (String typeName : EXPORTED_TYPE_ROOTS) {
      Class<?> type = Class.forName(typeName);
      assertThat(Modifier.isPublic(type.getModifiers()))
          .as("Declared Lineage API type must be public: %s", typeName)
          .isTrue();
    }
  }

  @Test
  void stableEntryMethodsDoNotExposeImplementationTypes() {
    for (Class<?> entryType : STABLE_ENTRY_TYPES) {
      for (Method method : entryType.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) continue;
        String signature = method.toGenericString();
        for (String forbidden : FORBIDDEN_SIGNATURE_TOKENS) {
          assertThat(signature)
              .as("Implementation type leaked from %s", signature)
              .doesNotContain(forbidden);
        }
      }
    }
  }

  @Test
  void draftModelsRemainModuleInternal() throws IOException {
    Map<String, Set<String>> consumers = externalLineageImports();
    assertThat(consumers)
        .doesNotContainKeys(
            BASE + ".domain.LineageAssetDraft",
            BASE + ".domain.LineageRelationDraft");
  }

  private boolean isDeclaredPublicType(String imported) {
    return EXPORTED_TYPE_ROOTS.stream()
        .anyMatch(root -> imported.equals(root) || imported.startsWith(root + "."));
  }

  private Map<String, Set<String>> externalLineageImports() throws IOException {
    Path repository = repositoryRoot();
    Path lineageModule =
        repository.resolve("yak-ops-business/yak-ops-business-lineage").normalize();
    Map<String, Set<String>> result = new LinkedHashMap<>();

    try (Stream<Path> files = Files.walk(repository)) {
      for (Path source : files.filter(this::isProductionJava).toList()) {
        if (source.normalize().startsWith(lineageModule)) continue;
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
          String imported = importedType(line);
          if (imported == null || !imported.startsWith(BASE + ".")) continue;
          result
              .computeIfAbsent(imported, ignored -> new LinkedHashSet<>())
              .add(normalize(repository.relativize(source)));
        }
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

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
