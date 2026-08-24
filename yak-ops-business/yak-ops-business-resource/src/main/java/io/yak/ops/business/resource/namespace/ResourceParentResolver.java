package io.yak.ops.business.resource.namespace;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.ResourceStorageGateway;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the logical parent and the storage binding inherited by new or moved resources. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceParentResolver {

  private final ResourceRepository repository;
  private final ResourceStorageGateway storage;

  public Parent resolve(Long parentId) {
    long normalized = normalize(parentId);
    if (normalized == 0L) {
      return new Parent(0L, "/", storage.defaultType());
    }
    ResourceNode parent =
        repository.findById(normalized)
            .orElseThrow(() -> new ResourceException(ResourceErrorCode.PARENT_NOT_FOUND));
    if (parent.getNodeType() != ResourceNodeType.DIRECTORY) {
      throw new ResourceException(ResourceErrorCode.PARENT_NOT_DIRECTORY);
    }
    return new Parent(parent.getId(), parent.getFullPath(), parent.getStorageType());
  }

  public long normalize(Long parentId) {
    return parentId == null || parentId <= 0L ? 0L : parentId;
  }

  public record Parent(Long id, String fullPath, ResourceStorageType storageType) {
  }
}
