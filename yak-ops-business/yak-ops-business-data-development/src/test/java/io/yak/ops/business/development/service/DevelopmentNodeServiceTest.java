package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DevelopmentNodeServiceTest {

  @Test
  void createsUnconfiguredSqlAndShellNodes() {
    InMemoryDirectoryRepository directories = new InMemoryDirectoryRepository();
    DevelopmentDirectory folder = directories.insert(null, "ODS");
    DevelopmentNodeService service = new DevelopmentNodeService(
        new InMemoryNodeRepository(),
        directories);

    DevelopmentNode sql = service.create("用户清洗", "sql", null, folder.id());
    DevelopmentNode shell = service.create("清理临时文件", "shell", 7L, null);

    assertEquals("SQL", sql.type());
    assertEquals(folder.id(), sql.directoryId());
    assertFalse(sql.configured());
    assertEquals("SHELL", shell.type());
    assertEquals(7L, shell.projectId());
    assertFalse(shell.configured());
  }

  @Test
  void rejectsUnknownDirectoryAndDuplicateSiblingName() {
    InMemoryDirectoryRepository directories = new InMemoryDirectoryRepository();
    InMemoryNodeRepository nodes = new InMemoryNodeRepository();
    DevelopmentNodeService service = new DevelopmentNodeService(nodes, directories);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("任务", "SQL", null, 999L));

    service.create("任务", "SQL", null, null);
    assertThrows(
        IllegalStateException.class,
        () -> service.create("任务", "SHELL", null, null));
  }

  private static final class InMemoryNodeRepository implements DevelopmentNodeRepository {

    private final AtomicLong ids = new AtomicLong(1L);
    private final Map<Long, DevelopmentNode> values = new LinkedHashMap<>();

    @Override
    public DevelopmentNode insert(
        String name,
        String type,
        Long projectId,
        Long directoryId,
        boolean configured) {
      Long id = ids.getAndIncrement();
      Instant now = Instant.now();
      DevelopmentNode node = new DevelopmentNode(
          id,
          name,
          type,
          projectId,
          directoryId,
          configured,
          now,
          now);
      values.put(id, node);
      return node;
    }

    @Override
    public Optional<DevelopmentNode> findById(Long id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<DevelopmentNode> list() {
      return new ArrayList<>(values.values());
    }

    @Override
    public boolean existsByName(Long directoryId, String name) {
      return values.values().stream().anyMatch(node ->
          java.util.Objects.equals(directoryId, node.directoryId())
              && name.equals(node.name()));
    }
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
