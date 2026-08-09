package io.yak.ops.business.resource.service.impl;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.config.ResourceProperties;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.StorageOperatorRegistry;
import io.yak.ops.business.resource.sync.ResourceFileSyncDispatcher;
import io.yak.ops.business.resource.util.ResourcePathUtils;
import io.yak.ops.common.bean.dto.resource.ResourceQueryDTO;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import io.yak.ops.spi.resource.ResourceFileSyncAction;
import io.yak.ops.spi.resource.ResourceFileSyncContext;
import io.yak.ops.spi.storage.StorageOperator;
import io.yak.ops.spi.storage.StoragePluginException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** 资源服务共享的领域元数据、路径、存储异常与同步事件支持。 */
@Slf4j
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
class ResourceServiceSupport {

  private final ResourceRepository repository;
  private final StorageOperatorRegistry storageRegistry;
  private final ResourceFileSyncDispatcher syncDispatcher;
  private final ResourceProperties properties;

  ParentContext parent(Long parentId) {
    Long normalized = normalizeParentId(parentId);
    if (normalized == 0L) {
      ResourceStorageType type = properties.getStorage().getType();
      storageRegistry.require(type);
      return new ParentContext(0L, "/", type);
    }
    ResourceNode parent =
        repository.findById(normalized)
            .orElseThrow(() -> new ResourceException(ResourceErrorCode.PARENT_NOT_FOUND));
    if (parent.getNodeType() != ResourceNodeType.DIRECTORY) {
      throw new ResourceException(ResourceErrorCode.PARENT_NOT_DIRECTORY);
    }
    return new ParentContext(parent.getId(), parent.getFullPath(), parent.getStorageType());
  }

  ResourceNode require(Long id) {
    if (id == null || id <= 0L) {
      throw new ResourceException(ResourceErrorCode.NOT_FOUND);
    }
    return repository.findById(id)
        .orElseThrow(() -> new ResourceException(ResourceErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  ResourceNode requireFile(Long id) {
    ResourceNode resource = require(id);
    if (resource.getNodeType() != ResourceNodeType.FILE) {
      throw new ResourceException(ResourceErrorCode.DIRECTORY_CONTENT_UNSUPPORTED);
    }
    return resource;
  }

  void ensureNameAvailable(Long parentId, String name, Long excludeId) {
    if (repository.existsByParentAndName(normalizeParentId(parentId), name, excludeId)) {
      throw new ResourceException(ResourceErrorCode.DUPLICATE_NAME, name);
    }
  }

  ResourceQuery normalizeQuery(ResourceQueryDTO queryDTO) {
    ResourceQueryDTO source = queryDTO == null ? new ResourceQueryDTO() : queryDTO;
    ResourceNodeType nodeType = null;
    if (StringUtils.hasText(source.getNodeType())) {
      String normalized = source.getNodeType().trim().toUpperCase(Locale.ROOT);
      try {
        nodeType = ResourceNodeType.valueOf(normalized);
      } catch (IllegalArgumentException exception) {
        throw new ResourceException(ResourceErrorCode.INVALID_NODE_TYPE, normalized);
      }
    }
    return new ResourceQuery(
        source.getPageNo(),
        source.getPageSize(),
        source.getParentId(),
        trimToNull(source.getKeyword()),
        nodeType);
  }

  ResourceNode newResource(
      Long parentId,
      String name,
      String fullPath,
      ResourceNodeType nodeType,
      ResourceStorageType storageType,
      String storagePath,
      String contentType,
      String suffix,
      Long fileSize,
      String checksum,
      String description) {
    LocalDateTime now = LocalDateTime.now();
    ResourceNode resource = new ResourceNode();
    resource.setParentId(normalizeParentId(parentId));
    resource.setName(name);
    resource.setFullPath(fullPath);
    resource.setNodeType(nodeType);
    resource.setStorageType(storageType);
    resource.setStoragePath(storagePath);
    resource.setContentType(contentType);
    resource.setSuffix(suffix);
    resource.setFileSize(fileSize == null ? 0L : fileSize);
    resource.setChecksum(checksum);
    resource.setDescription(trimToNull(description));
    resource.setVersion(1);
    resource.setGitSyncStatus("NONE");
    resource.setCreateTime(now);
    resource.setUpdateTime(now);
    return resource;
  }

  void insert(ResourceNode resource) {
    if (!repository.insert(resource)) {
      throw new ResourceException(ResourceErrorCode.CREATE_FAILED);
    }
  }

  void relocate(ResourceNode resource, ParentContext targetParent, String targetName) {
    if (resource.getId().equals(targetParent.id)) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET);
    }
    if (targetParent.storageType != resource.getStorageType()) {
      throw new ResourceException(ResourceErrorCode.CROSS_STORAGE_MOVE_UNSUPPORTED);
    }
    String oldFullPath = resource.getFullPath();
    String newFullPath = ResourcePathUtils.childPath(targetParent.fullPath, targetName);
    if (newFullPath.equals(oldFullPath)) {
      resource.setName(targetName);
      resource.setParentId(targetParent.id);
      return;
    }
    if (newFullPath.startsWith(oldFullPath + "/")) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET);
    }
    String oldStoragePath = resource.getStoragePath();
    String newStoragePath = ResourcePathUtils.storagePath(newFullPath);
    StorageOperator operator = storageRegistry.require(resource.getStorageType());
    storageRun(() -> operator.move(oldStoragePath, newStoragePath, false));

    List<ResourceNode> updates = new ArrayList<>();
    resource.setParentId(targetParent.id);
    resource.setName(targetName);
    resource.setFullPath(newFullPath);
    resource.setStoragePath(newStoragePath);
    resource.setVersion(nextVersion(resource));
    resource.setUpdateTime(LocalDateTime.now());
    updates.add(resource);

    if (resource.getNodeType() == ResourceNodeType.DIRECTORY) {
      for (ResourceNode descendant : repository.findDescendants(oldFullPath)) {
        String suffix = descendant.getFullPath().substring(oldFullPath.length());
        descendant.setFullPath(newFullPath + suffix);
        descendant.setStoragePath(ResourcePathUtils.storagePath(descendant.getFullPath()));
        descendant.setVersion(nextVersion(descendant));
        descendant.setUpdateTime(LocalDateTime.now());
        updates.add(descendant);
      }
    }

    if (!repository.updateBatch(updates)) {
      try {
        operator.move(newStoragePath, oldStoragePath, false);
      } catch (RuntimeException rollbackException) {
        log.error(
            "Failed to rollback storage move: {} -> {}",
            newStoragePath,
            oldStoragePath,
            rollbackException);
      }
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
  }

  void dispatch(ResourceNode resource, ResourceFileSyncAction action, String oldFullPath) {
    syncDispatcher.dispatchAfterCommit(
        ResourceFileSyncContext.builder()
            .resourceId(resource.getId())
            .action(action)
            .nodeType(resource.getNodeType())
            .storageType(resource.getStorageType())
            .oldFullPath(oldFullPath)
            .fullPath(resource.getFullPath())
            .storagePath(resource.getStoragePath())
            .version(resource.getVersion())
            .build());
  }

  void storageRun(Runnable operation) {
    try {
      operation.run();
    } catch (ResourceException exception) {
      throw exception;
    } catch (StoragePluginException exception) {
      throw storageException(exception);
    } catch (RuntimeException exception) {
      throw storageException(exception);
    }
  }

  <T> T storageGet(StorageSupplier<T> operation) {
    try {
      return operation.get();
    } catch (ResourceException exception) {
      throw exception;
    } catch (StoragePluginException exception) {
      throw storageException(exception);
    } catch (RuntimeException exception) {
      throw storageException(exception);
    }
  }

  void cleanupCreatedObject(StorageOperator operator, String storagePath, boolean recursive) {
    try {
      operator.delete(storagePath, recursive);
    } catch (RuntimeException cleanupException) {
      log.warn(
          "Failed to cleanup storage object after persistence failure: {}",
          storagePath,
          cleanupException);
    }
  }

  void runAfterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  Long normalizeParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? 0L : parentId;
  }

  int nextVersion(ResourceNode resource) {
    return resource.getVersion() == null ? 1 : resource.getVersion() + 1;
  }

  String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private ResourceException storageException(RuntimeException exception) {
    return new ResourceException(
        ResourceErrorCode.STORAGE_OPERATION_FAILED,
        exception.getMessage(),
        exception);
  }

  @FunctionalInterface
  interface StorageSupplier<T> {
    T get();
  }

  static final class ParentContext {

    final Long id;
    final String fullPath;
    final ResourceStorageType storageType;

    ParentContext(Long id, String fullPath, ResourceStorageType storageType) {
      this.id = Objects.requireNonNull(id, "parent id");
      this.fullPath = Objects.requireNonNull(fullPath, "parent full path");
      this.storageType = Objects.requireNonNull(storageType, "parent storage type");
    }
  }
}
