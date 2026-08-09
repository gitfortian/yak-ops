package io.yak.ops.business.resource.service.impl;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceDownload;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourcePage;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.service.ResourceService;
import io.yak.ops.business.resource.service.support.ResourceViewMapper;
import io.yak.ops.business.resource.storage.StorageOperatorRegistry;
import io.yak.ops.business.resource.util.ResourcePathUtils;
import io.yak.ops.common.bean.dto.resource.ResourceContentUpdateDTO;
import io.yak.ops.common.bean.dto.resource.ResourceCreateContentDTO;
import io.yak.ops.common.bean.dto.resource.ResourceCreateDirectoryDTO;
import io.yak.ops.common.bean.dto.resource.ResourceMoveDTO;
import io.yak.ops.common.bean.dto.resource.ResourceQueryDTO;
import io.yak.ops.common.bean.dto.resource.ResourceUpdateDTO;
import io.yak.ops.common.bean.vo.resource.ResourceContentVO;
import io.yak.ops.common.bean.vo.resource.ResourceStoragePluginVO;
import io.yak.ops.common.bean.vo.resource.ResourceVO;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.spi.resource.ResourceFileSyncAction;
import io.yak.ops.spi.storage.StorageOperator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 资源管理服务实现。目录元数据由 Repository 管理，文件内容由存储插件负责。 */
@Slf4j
@Service
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

  private final ResourceRepository repository;
  private final StorageOperatorRegistry storageRegistry;
  private final ResourceServiceSupport support;
  private final ResourceFileOperations fileOperations;
  private final ResourceViewMapper viewMapper;

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceVO createDirectory(ResourceCreateDirectoryDTO requestDTO) {
    if (requestDTO == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "创建目录参数不能为空");
    }
    ResourceServiceSupport.ParentContext parent = support.parent(requestDTO.getParentId());
    String name = ResourcePathUtils.normalizeName(requestDTO.getName());
    support.ensureNameAvailable(parent.id, name, null);
    String fullPath = ResourcePathUtils.childPath(parent.fullPath, name);
    String storagePath = ResourcePathUtils.storagePath(fullPath);
    StorageOperator operator = storageRegistry.require(parent.storageType);

    support.storageRun(() -> operator.createDirectory(storagePath));
    try {
      ResourceNode resource =
          support.newResource(
              parent.id,
              name,
              fullPath,
              ResourceNodeType.DIRECTORY,
              parent.storageType,
              storagePath,
              null,
              null,
              0L,
              null,
              requestDTO.getDescription());
      support.insert(resource);
      support.dispatch(resource, ResourceFileSyncAction.CREATED, null);
      return viewMapper.node(resource);
    } catch (RuntimeException exception) {
      support.cleanupCreatedObject(operator, storagePath, true);
      throw exception;
    }
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceVO upload(
      Long parentId,
      String name,
      String description,
      MultipartFile file) {
    return viewMapper.node(fileOperations.upload(parentId, name, description, file));
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceVO createContent(ResourceCreateContentDTO requestDTO) {
    if (requestDTO == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "在线创建参数不能为空");
    }
    return viewMapper.node(
        fileOperations.createContent(
            requestDTO.getParentId(),
            requestDTO.getName(),
            requestDTO.getDescription(),
            requestDTO.getContentType(),
            requestDTO.getContent()));
  }

  @Override
  public ResourceVO get(Long id) {
    return viewMapper.node(support.require(id));
  }

  @Override
  public List<ResourceVO> list(Long parentId, String keyword) {
    return repository
        .findChildren(support.normalizeParentId(parentId), support.trimToNull(keyword))
        .stream()
        .map(viewMapper::node)
        .toList();
  }

  @Override
  public PagingData<ResourceVO> page(ResourceQueryDTO queryDTO) {
    ResourcePage<ResourceNode> page = repository.page(support.normalizeQuery(queryDTO));
    List<ResourceVO> records = page.records().stream().map(viewMapper::node).toList();
    return pagingData(records, page);
  }

  @Override
  public List<ResourceVO> tree() {
    List<ResourceNode> resources = repository.findAll();
    Map<Long, ResourceVO> mapped = new HashMap<>();
    for (ResourceNode resource : resources) {
      mapped.put(resource.getId(), viewMapper.node(resource));
    }
    List<ResourceVO> roots = new ArrayList<>();
    for (ResourceNode resource : resources) {
      ResourceVO current = mapped.get(resource.getId());
      if (resource.getParentId() == null || resource.getParentId() == 0L) {
        roots.add(current);
        continue;
      }
      ResourceVO parent = mapped.get(resource.getParentId());
      if (parent == null) {
        roots.add(current);
      } else {
        parent.getChildren().add(current);
      }
    }
    return roots;
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceVO update(Long id, ResourceUpdateDTO requestDTO) {
    if (requestDTO == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "更新资源参数不能为空");
    }
    ResourceNode resource = support.require(id);
    String name = ResourcePathUtils.normalizeName(requestDTO.getName());
    support.ensureNameAvailable(resource.getParentId(), name, resource.getId());
    String oldFullPath = resource.getFullPath();
    if (!name.equals(resource.getName())) {
      support.relocate(resource, support.parent(resource.getParentId()), name);
    }
    resource.setDescription(support.trimToNull(requestDTO.getDescription()));
    resource.setUpdateTime(LocalDateTime.now());
    if (!repository.update(resource)) {
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
    support.dispatch(resource, ResourceFileSyncAction.UPDATED, oldFullPath);
    return viewMapper.node(resource);
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceVO replaceFile(Long id, MultipartFile file) {
    return viewMapper.node(fileOperations.replaceFile(id, file));
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceContentVO updateContent(Long id, ResourceContentUpdateDTO requestDTO) {
    if (requestDTO == null) {
      throw new ResourceException(ResourceErrorCode.CONTENT_NOT_EDITABLE, "文件内容不能为空");
    }
    return viewMapper.content(fileOperations.updateContent(id, requestDTO.getContent()));
  }

  @Override
  public ResourceContentVO getContent(Long id, int skipLineNum, int limit) {
    return viewMapper.content(fileOperations.getContent(id, skipLineNum, limit));
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public ResourceVO move(Long id, ResourceMoveDTO requestDTO) {
    if (requestDTO == null || requestDTO.getTargetParentId() == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_MOVE_TARGET, "目标目录不能为空");
    }
    ResourceNode resource = support.require(id);
    ResourceServiceSupport.ParentContext targetParent = support.parent(requestDTO.getTargetParentId());
    support.ensureNameAvailable(targetParent.id, resource.getName(), resource.getId());
    String oldFullPath = resource.getFullPath();
    support.relocate(resource, targetParent, resource.getName());
    support.dispatch(resource, ResourceFileSyncAction.MOVED, oldFullPath);
    return viewMapper.node(resource);
  }

  @Override
  @Transactional(transactionManager = "opsResourceTransactionManager", rollbackFor = Exception.class)
  public boolean delete(Long id) {
    ResourceNode resource = support.require(id);
    List<ResourceNode> descendants = repository.findDescendants(resource.getFullPath());
    List<Long> ids = new ArrayList<>(descendants.size() + 1);
    ids.add(resource.getId());
    for (ResourceNode descendant : descendants) {
      ids.add(descendant.getId());
    }
    if (!repository.deleteBatch(ids)) {
      throw new ResourceException(ResourceErrorCode.DELETE_FAILED);
    }
    StorageOperator operator = storageRegistry.require(resource.getStorageType());
    support.runAfterCommit(
        () -> {
          try {
            operator.delete(
                resource.getStoragePath(),
                resource.getNodeType() == ResourceNodeType.DIRECTORY);
          } catch (RuntimeException exception) {
            log.error(
                "Failed to remove resource object after metadata deletion: {}",
                resource.getStoragePath(),
                exception);
          }
        });
    support.dispatch(resource, ResourceFileSyncAction.DELETED, resource.getFullPath());
    return true;
  }

  @Override
  public ResourceDownload download(Long id) {
    return fileOperations.download(id);
  }

  @Override
  public List<ResourceStoragePluginVO> storagePlugins() {
    return storageRegistry.list().stream().map(viewMapper::storagePlugin).toList();
  }

  private PagingData<ResourceVO> pagingData(
      List<ResourceVO> records,
      ResourcePage<ResourceNode> page) {
    PagingData<ResourceVO> result = new PagingData<>();
    result.setBizData(records);
    result.setPagination(
        PagingData.Pagination.builder()
            .total(page.total())
            .pages(page.pages())
            .pageNo(page.pageNo())
            .pageSize(page.pageSize())
            .build());
    return result;
  }
}
