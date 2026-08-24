package io.yak.ops.business.resource.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.dao.ResourceDao.PageQuery;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Explicit persistence adapter between Resource domain metadata and MyBatis persistence models. */
@Repository
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceRepositoryAdapter implements ResourceRepository {

  private final ResourceDao resourceDao;

  @Override
  public Optional<ResourceNode> findById(Long id) {
    return Optional.ofNullable(resourceDao.selectById(id)).map(this::toDomain);
  }

  @Override
  public Optional<ResourceNode> findByFullPath(String fullPath) {
    return Optional.ofNullable(resourceDao.selectByFullPath(fullPath)).map(this::toDomain);
  }

  @Override
  public boolean existsByParentAndName(Long parentId, String name, Long excludeId) {
    return resourceDao.existsByParentAndName(parentId, name, excludeId);
  }

  @Override
  public boolean insert(ResourceNode resource) {
    if (resource == null) {
      return false;
    }
    ResourcePO po = toPersistence(resource);
    boolean inserted = resourceDao.insert(po) > 0;
    if (inserted) {
      resource.setId(po.getId());
    }
    return inserted;
  }

  @Override
  public boolean update(ResourceNode resource) {
    return resource != null && resourceDao.update(toPersistence(resource));
  }

  @Override
  public boolean updateBatch(List<ResourceNode> resources) {
    if (resources == null || resources.isEmpty()) {
      return true;
    }
    return resourceDao.updateBatch(resources.stream().map(this::toPersistence).toList());
  }

  @Override
  public boolean deleteBatch(List<Long> ids) {
    return resourceDao.deleteBatch(ids);
  }

  @Override
  public List<ResourceNode> findChildren(Long parentId, String keyword) {
    return resourceDao.selectChildren(parentId, keyword).stream().map(this::toDomain).toList();
  }

  @Override
  public List<ResourceNode> findAll() {
    return resourceDao.selectAll().stream().map(this::toDomain).toList();
  }

  @Override
  public List<ResourceNode> findDescendants(String fullPath) {
    return resourceDao.selectDescendants(fullPath).stream().map(this::toDomain).toList();
  }

  @Override
  public PageData<ResourceNode> page(ResourceQuery query) {
    ResourceQuery condition = query == null
        ? new ResourceQuery(1, 20, null, null, null)
        : query;
    IPage<ResourcePO> page = resourceDao.selectPage(
        new PageQuery(
            condition.pageNo(),
            condition.pageSize(),
            condition.parentId(),
            condition.keyword(),
            condition.nodeType()));
    return new PageData<>(
        page.getRecords().stream().map(this::toDomain).toList(),
        page.getTotal(),
        page.getPages(),
        page.getCurrent(),
        page.getSize());
  }

  ResourceNode toDomain(ResourcePO po) {
    if (po == null) {
      return null;
    }
    ResourceNode domain = new ResourceNode();
    domain.setId(po.getId());
    domain.setParentId(po.getParentId());
    domain.setName(po.getName());
    domain.setFullPath(po.getFullPath());
    domain.setNodeType(po.getNodeType());
    domain.setStorageType(po.getStorageType());
    domain.setStoragePath(po.getStoragePath());
    domain.setContentType(po.getContentType());
    domain.setSuffix(po.getSuffix());
    domain.setFileSize(po.getFileSize());
    domain.setChecksum(po.getChecksum());
    domain.setDescription(po.getDescription());
    domain.setVersion(po.getVersion());
    domain.setGitSyncStatus(po.getGitSyncStatus());
    domain.setCreateTime(po.getCreateTime());
    domain.setUpdateTime(po.getUpdateTime());
    return domain;
  }

  ResourcePO toPersistence(ResourceNode domain) {
    ResourcePO po = new ResourcePO();
    po.setId(domain.getId());
    po.setParentId(domain.getParentId());
    po.setName(domain.getName());
    po.setFullPath(domain.getFullPath());
    po.setNodeType(domain.getNodeType());
    po.setStorageType(domain.getStorageType());
    po.setStoragePath(domain.getStoragePath());
    po.setContentType(domain.getContentType());
    po.setSuffix(domain.getSuffix());
    po.setFileSize(domain.getFileSize());
    po.setChecksum(domain.getChecksum());
    po.setDescription(domain.getDescription());
    po.setVersion(domain.getVersion());
    po.setGitSyncStatus(domain.getGitSyncStatus());
    po.setCreateTime(domain.getCreateTime());
    po.setUpdateTime(domain.getUpdateTime());
    return po;
  }
}
