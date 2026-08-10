package io.yak.ops.business.development.service;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for hierarchical data-development directories. */
@Service
public class DevelopmentDirectoryService {

  private final DevelopmentDirectoryRepository repository;
  private final DevelopmentNodeRepository nodeRepository;

  public DevelopmentDirectoryService(
      DevelopmentDirectoryRepository repository,
      DevelopmentNodeRepository nodeRepository) {
    this.repository = repository;
    this.nodeRepository = nodeRepository;
  }

  public List<DevelopmentDirectory> list() {
    List<DevelopmentDirectory> directories = repository.list();
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
  public DevelopmentDirectory create(Long parentId, String name) {
    Long normalizedParentId = normalizeParentId(parentId);
    String normalizedName = normalizeName(name);

    if (normalizedParentId != null) {
      repository.findById(normalizedParentId)
          .orElseThrow(() -> new IllegalArgumentException("父目录不存在：" + normalizedParentId));
    }

    if (repository.existsByName(normalizedParentId, normalizedName)) {
      throw new IllegalStateException("当前路径下已存在同名目录：" + normalizedName);
    }

    DevelopmentDirectory created = repository.insert(normalizedParentId, normalizedName);
    return requireFromList(created.id());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentDirectory rename(Long id, String name) {
    DevelopmentDirectory current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("目录不存在：" + id));
    String normalizedName = normalizeName(name);
    if (current.name().equals(normalizedName)) return requireFromList(id);
    if (repository.existsByName(current.parentId(), normalizedName)) {
      throw new IllegalStateException("当前路径下已存在同名目录：" + normalizedName);
    }
    if (!repository.updateName(id, normalizedName)) {
      throw new IllegalStateException("目录重命名失败：" + id);
    }
    return requireFromList(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("目录不存在：" + id));
    if (repository.hasChildren(id) || nodeRepository.existsInDirectory(id)) {
      throw new IllegalStateException("目录不为空，请先删除目录下的节点和子目录");
    }
    if (!repository.deleteById(id)) {
      throw new IllegalStateException("目录删除失败：" + id);
    }
  }

  private DevelopmentDirectory requireFromList(Long id) {
    return list().stream()
        .filter(directory -> directory.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("目录保存成功但无法重新读取：" + id));
  }

  private DevelopmentDirectory withPath(DevelopmentDirectory directory, String path) {
    return new DevelopmentDirectory(
        directory.id(),
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
}
