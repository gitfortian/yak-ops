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

    DevelopmentDirectory ods = service.create(null, "ODS");
    DevelopmentDirectory user = service.create(ods.id(), "用户域");

    assertEquals("/ODS", ods.path());
    assertEquals("/ODS/用户域", user.path());
    assertEquals(
        List.of("/ODS", "/ODS/用户域"),
        service.list().stream().map(DevelopmentDirectory::path).toList());
  }

  @Test
  void rejectsDuplicateSiblingDirectory() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    DevelopmentDirectoryService service = new DevelopmentDirectoryService(repository);
    service.create(null, "ODS");

    assertThrows(IllegalStateException.class, () -> service.create(null, "ODS"));
  }

  private static final class InMemoryDirectoryRepository
      implements DevelopmentDirectoryRepository {

    private final AtomicLong ids = new AtomicLong(1L);
    private final Map<Long, DevelopmentDirectory> values = new LinkedHashMap<>();

    @Override
    public DevelopmentDirectory insert(Long parentId, String name) {
      Long id = ids.getAndIncrement();
      Instant now = Instant.now();
      DevelopmentDirectory directory = new DevelopmentDirectory(
          id,
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
    public List<DevelopmentDirectory> list() {
      return new ArrayList<>(values.values());
    }

    @Override
    public boolean existsByName(Long parentId, String name) {
      return values.values().stream().anyMatch(directory ->
          java.util.Objects.equals(parentId, directory.parentId())
              && name.equals(directory.name()));
    }
  }
}
