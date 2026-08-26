package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Lightweight source conventions for the Lineage module. */
class LineageCodeStyleConventionTest {

  private static final Pattern PUBLIC_TOP_LEVEL_TYPE =
      Pattern.compile(
          "(?m)^public\\s+(?:(?:abstract|final|sealed|non-sealed)\\s+)*"
              + "(?:@interface|class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

  @Test
  void javaSourcesUseStableTextConventions() throws IOException {
    for (Path source : moduleJavaFiles()) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      String relative = normalize(moduleRoot().relativize(source));

      assertThat(text).as(relative).doesNotContain("\t");
      assertThat(text.endsWith("\n")).as("%s must end with a newline", relative).isTrue();

      String[] lines = text.split("\\R", -1);
      for (int index = 0; index < lines.length; index++) {
        assertThat(lines[index])
            .as("%s:%s has trailing whitespace", relative, index + 1)
            .isEqualTo(lines[index].stripTrailing());
      }

      boolean wildcardImport =
          text.lines()
              .map(String::trim)
              .anyMatch(
                  line ->
                      (line.startsWith("import ") || line.startsWith("import static "))
                          && line.endsWith(".*;"));
      assertThat(wildcardImport).as("%s must not use wildcard imports", relative).isFalse();
    }
  }

  @Test
  void packageDeclarationsMatchSourcePaths() throws IOException {
    assertPackages(
        mainJavaRoot(),
        "io.yak.ops.business.lineage");
    assertPackages(
        testJavaRoot(),
        "io.yak.ops.business.lineage");
  }

  @Test
  void publicProductionTypeMatchesItsFileName() throws IOException {
    for (Path source : javaFiles(mainJavaRoot())) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      Matcher matcher = PUBLIC_TOP_LEVEL_TYPE.matcher(text);
      List<String> publicTypes = new ArrayList<>();
      while (matcher.find()) publicTypes.add(matcher.group(1));

      String filename = source.getFileName().toString();
      String expected = filename.substring(0, filename.length() - ".java".length());
      assertThat(publicTypes)
          .as("One public top-level type must match %s", normalize(source))
          .containsExactly(expected);
    }
  }

  @Test
  void roleNamesStayInTheirOwningPackages() throws IOException {
    Path root = mainJavaRoot();
    for (Path source : javaFiles(root)) {
      String relative = normalize(root.relativize(source));
      String filename = source.getFileName().toString();

      if (filename.endsWith("Controller.java")) {
        assertThat(relative).startsWith("controller/");
      }
      if (filename.endsWith("Service.java")) {
        assertThat(relative).startsWith("service/");
      }
      if (filename.endsWith("Analyzer.java")) {
        assertThat(relative).startsWith("analysis/");
      }
      if (filename.endsWith("Repository.java")
          || filename.endsWith("RepositoryAdapter.java")
          || filename.endsWith("Codec.java")) {
        assertThat(relative).startsWith("repository/");
      }
      if (filename.endsWith("Dao.java") || filename.endsWith("DaoImpl.java")) {
        assertThat(relative).startsWith("dao/");
      }
      if (filename.endsWith("Mapper.java")) {
        assertThat(
                relative.startsWith("dao/mapper/")
                    || relative.startsWith("controller/v1/converter/"))
            .as("Mapper role is misplaced: %s", relative)
            .isTrue();
      }
      if (filename.endsWith("Configuration.java") || filename.startsWith("ConditionalOn")) {
        assertThat(relative).startsWith("config/");
      }
    }
  }

  private void assertPackages(Path root, String basePackage) throws IOException {
    for (Path source : javaFiles(root)) {
      Path relative = root.relativize(source);
      Path parent = relative.getParent();
      String suffix =
          parent == null ? "" : "." + normalize(parent).replace('/', '.');
      String expected = "package " + basePackage + suffix + ";";
      String text = Files.readString(source, StandardCharsets.UTF_8);
      String firstNonBlank =
          text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");

      assertThat(firstNonBlank)
          .as("Package declaration must match %s", normalize(relative))
          .isEqualTo(expected);
    }
  }

  private List<Path> moduleJavaFiles() throws IOException {
    List<Path> result = new ArrayList<>();
    result.addAll(javaFiles(mainJavaRoot()));
    result.addAll(javaFiles(testJavaRoot()));
    return result;
  }

  private List<Path> javaFiles(Path root) throws IOException {
    if (!Files.isDirectory(root)) return List.of();
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
    }
  }

  private Path moduleRoot() {
    Path current = Paths.get(".").toAbsolutePath().normalize();
    if (Files.isDirectory(current.resolve("src/main/java"))) return current;

    Path repositoryRelative =
        current.resolve("yak-ops-business/yak-ops-business-lineage").normalize();
    assertThat(Files.isDirectory(repositoryRelative.resolve("src/main/java")))
        .as("Unable to locate Lineage module from %s", current)
        .isTrue();
    return repositoryRelative;
  }

  private Path mainJavaRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/lineage");
  }

  private Path testJavaRoot() {
    return moduleRoot().resolve("src/test/java/io/yak/ops/business/lineage");
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
