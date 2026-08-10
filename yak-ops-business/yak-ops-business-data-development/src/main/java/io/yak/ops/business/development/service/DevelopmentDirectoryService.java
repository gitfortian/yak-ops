package io.yak.ops.business.development.service;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for project-scoped hierarchical data-development directories. */
@Service
public class DevelopmentDirectoryService {

  private final DevelopmentDirectoryRepository repository;

  public DevelopmentDirectoryService(DevelopmentDirectoryRepository repository) {
    this.repository = repository;
  }

  public List<DevelopmentDirectory> list(Long projectId) {
    long normalizedProjectId = requirePositive(projectId, "项目 ID");
    List<DevelopmentDirectory> directories = repository.listByProjectId(normalizedProjectId);
    Map<Long, DevelopmentDirectory> byId = new HashMap<>();
    directories.forEach(directory -> byId.put(directory.id(), directory));
    Map<Long, String> pathCache = new HashMap<>();

    return directories.stream()
        .map(directory -> withPath(
            directory,
            resolvePath(directory, byId, pathCache, new HashSet<>())))
        .sorted(Comparator.comparing(DevelopmentDirectory::path, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentDirectory create(Long projectId, Long parentId, String name) {
    long normalizedProjectId = requirePositive(projectId, "项目 ID");
    Long normalizedParentId = normalizeParentId(parentId);
    String normalizedName = normalizeName(name);

    if (normalizedParentId != null) {
      DevelopmentDirectory parent = repository.findById(normalizedParentId)
          .orElseThrow(() -> new IllegalArgumentException("父目录不存在：" + normalizedParentId));
      if (!normalizedProjectIdEquals(parent.projectId(), normalizedProjectId)) {
        throw new IllegalArgumentException("父目录不属于当前项目：" + normalizedParentId);
      }
    }

    if (repository.existsByName(normalizedProjectId, normalizedParentId, normalizedName)) {
      throw new IllegalStateException("当前路径下已存在同名目录：" + normalizedName);
    }

    DevelopmentDirectory created = repository.insert(
        normalizedProjectId,
        normalizedParentId,
        normalizedName);
    return list(normalizedProjectId).stream()
        .filter(directory -> directory.id().equals(created.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("目录创建成功但无法重新读取：" + created.id()));
  }

  private DevelopmentDirectory withPath(DevelopmentDirectory directory, String path) {
    return new DevelopmentDirectory(
        directory.id(),
        directory.projectId(),
        directory.parentId(),
        directory.name(),
        path,
        directory.createTime(),
        directory.updateTime());
  }

  private String resolvePath(
      DevelopmentDirectory directory,
      Map<Long, DevelopmentDirectory> byId,
      Map<Long, String> pathCache,
      Set<Long> visiting) {
    String cached = pathCache.get(directory.id());
    if (cached != null) return cached;
    if (!visiting.add(directory.id())) {
      throw new IllegalStateException("数据开发目录存在循环引用：" + directory.id());
    }

    String path;
    if (directory.parentId() == null) {
      path = "/" + directory.name();
    } else {
      DevelopmentDirectory parent = byId.get(directory.parentId());
      if (parent == null) {
        throw new IllegalStateException(
            "数据开发目录父节点不存在：" + directory.id() + " -> " + directory.parentId());
      }
      path = resolvePath(parent, byId, pathCache, visiting) + "/" + directory.name();
    }

    visiting.remove(directory.id());
    pathCache.put(directory.id(), path);
    return path;
  }

  private Long normalizeParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? null : parentId;
  }

  private String normalizeName(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("目录名称不能为空");
    String normalized = name.trim();
    if (normalized.length() > 128) throw new IllegalArgumentException("目录名称不能超过 128 个字符");
    if (".".equals(normalized) || "..".equals(normalized)
        || normalized.contains("/") || normalized.contains("\\")) {
      throw new IllegalArgumentException("目录名称不能包含 /、\\，也不能使用 . 或 ..");
    }
    return normalized;
  }

  private long requirePositive(Long value, String name) {
    if (value == null || value <= 0L) throw new IllegalArgumentException(name + "不合法：" + value);
    return value;
  }

  private boolean normalizedProjectIdEquals(Long value, long expected) {
    return value != null && value == expected;
  }
}
