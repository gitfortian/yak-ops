package io.yak.ops.business.development.service;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for lightweight data-development resource nodes. */
@Service
public class DevelopmentNodeService {

  private static final Set<String> SUPPORTED_TYPES = Set.of("SQL", "SHELL", "HTTP", "PYTHON");

  private final DevelopmentNodeRepository repository;
  private final DevelopmentDirectoryRepository directoryRepository;

  public DevelopmentNodeService(
      DevelopmentNodeRepository repository,
      DevelopmentDirectoryRepository directoryRepository) {
    this.repository = repository;
    this.directoryRepository = directoryRepository;
  }

  public List<DevelopmentNode> list() {
    return repository.list();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentNode create(
      String name,
      String type,
      Long projectId,
      Long directoryId) {
    String normalizedName = normalizeName(name);
    String normalizedType = normalizeType(type);
    Long normalizedProjectId = normalizeProjectId(projectId);
    Long normalizedDirectoryId = normalizeDirectoryId(directoryId);

    if (normalizedDirectoryId != null
        && directoryRepository.findById(normalizedDirectoryId).isEmpty()) {
      throw new IllegalArgumentException("数据开发目录不存在：" + normalizedDirectoryId);
    }
    if (repository.existsByName(normalizedDirectoryId, normalizedName)) {
      throw new IllegalStateException("当前目录下已存在同名节点：" + normalizedName);
    }

    return repository.insert(
        normalizedName,
        normalizedType,
        normalizedProjectId,
        normalizedDirectoryId,
        false);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentNode rename(Long id, String name) {
    DevelopmentNode current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + id));
    String normalizedName = normalizeName(name);
    if (current.name().equals(normalizedName)) return current;
    if (repository.existsByName(current.directoryId(), normalizedName)) {
      throw new IllegalStateException("当前目录下已存在同名节点：" + normalizedName);
    }
    if (!repository.updateName(id, normalizedName)) {
      throw new IllegalStateException("节点重命名失败：" + id);
    }
    return repository.findById(id)
        .orElseThrow(() -> new IllegalStateException("节点重命名成功但无法重新读取：" + id));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + id));
    if (!repository.deleteById(id)) {
      throw new IllegalStateException("节点删除失败：" + id);
    }
  }

  private String normalizeName(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("节点名称不能为空");
    String normalized = name.trim();
    if (normalized.length() > 200) {
      throw new IllegalArgumentException("节点名称不能超过 200 个字符");
    }
    if (normalized.contains("/") || normalized.contains("\\")) {
      throw new IllegalArgumentException("节点名称不能包含路径分隔符");
    }
    return normalized;
  }

  private String normalizeType(String type) {
    if (type == null || type.isBlank()) throw new IllegalArgumentException("节点类型不能为空");
    String normalized = type.trim().toUpperCase(Locale.ROOT);
    if (!SUPPORTED_TYPES.contains(normalized)) {
      throw new IllegalArgumentException("不支持的数据开发节点类型：" + normalized);
    }
    return normalized;
  }

  private Long normalizeProjectId(Long projectId) {
    return projectId == null || projectId <= 0L ? null : projectId;
  }

  private Long normalizeDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? null : directoryId;
  }
}
