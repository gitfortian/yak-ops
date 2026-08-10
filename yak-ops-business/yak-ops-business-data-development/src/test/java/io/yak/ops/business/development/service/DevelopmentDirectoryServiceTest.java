package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DevelopmentDirectoryServiceTest {

  @Test
  void createsNestedDirectoryPaths() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    DevelopmentDirectoryService service = new DevelopmentDirectoryService(repository);

    DevelopmentDirectory ods = service.create(1L, null, "ODS");
    DevelopmentDirectory user = service.create(1L, ods.id(), "用户域");

    assertEquals("/ODS", ods.path());
    assertEquals("/ODS/用户域", user.path());
    assertEquals(
        List.of("/ODS", "/ODS/用户域"),
        service.list(1L).stream().map(DevelopmentDirectory::path).toList());
  }

  @Test
  void rejectsParentDirectoryFromAnotherProject() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    DevelopmentDirectoryService service = new DevelopmentDirectoryService(repository);
    DevelopmentDirectory otherProject = service.create(2L, null, "ODS");

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(1L, otherProject.id(), "非法子目录"));
  }

  private static final class InMemoryDirectoryRepository
      implements DevelopmentDirectoryRepository {

    private final AtomicLong ids = new AtomicLong(1L);
    private final Map<Long, DevelopmentDirectory> values = new LinkedHashMap<>();

    @Override
    public DevelopmentDirectory insert(Long projectId, Long parentId, String name) {
      Long id = ids.getAndIncrement();
      Instant now = Instant.now();
      DevelopmentDirectory directory = new DevelopmentDirectory(
          id,
          projectId,
          parentId,
          name,
          null,
          now,
          now);
      values.put(id, directory);
      return directory;
    }

    @Override
    public Optional<DevelopmentDirectory> findById(Long id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<DevelopmentDirectory> listByProjectId(Long projectId) {
      List<DevelopmentDirectory> result = new ArrayList<>();
      values.values().stream()
          .filter(directory -> projectId.equals(directory.projectId()))
          .forEach(result::add);
      return result;
    }

    @Override
    public boolean existsByName(Long projectId, Long parentId, String name) {
      return values.values().stream().anyMatch(directory ->
          projectId.equals(directory.projectId())
              && java.util.Objects.equals(parentId, directory.parentId())
              && name.equals(directory.name()));
    }
  }
}
