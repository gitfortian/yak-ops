package io.yak.ops.business.resource.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.dao.ResourceDao.PageQuery;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Explicit persistence adapter between Resource domain metadata and MyBatis persistence models. */
@Repository
@ConditionalOnResourceEnabled
public class ResourceRepositoryAdapter implements ResourceRepository {

  private final ResourceDao resourceDao;
  private final CurrentProject currentProject;

  @Autowired
  public ResourceRepositoryAdapter(ResourceDao resourceDao, CurrentProject currentProject) {
    this.resourceDao = resourceDao;
    this.currentProject = currentProject;
  }

  public ResourceRepositoryAdapter(ResourceDao resourceDao) {
    this(resourceDao, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public Optional<ResourceNode> findById(Long id) {
    Long projectId = currentProjectId();
    ResourcePO row =
        projectId == null ? resourceDao.selectById(id) : resourceDao.selectById(projectId, id);
    return Optional.ofNullable(row).map(this::toDomain);
  }

  @Override
  public Optional<ResourceNode> findByFullPath(String fullPath) {
    Long projectId = currentProjectId();
    ResourcePO row =
        projectId == null
            ? resourceDao.selectByFullPath(fullPath)
            : resourceDao.selectByFullPath(projectId, fullPath);
    return Optional.ofNullable(row).map(this::toDomain);
  }

  @Override
  public boolean existsByParentAndName(Long parentId, String name, Long excludeId) {
    Long projectId = currentProjectId();
    return projectId == null
        ? resourceDao.existsByParentAndName(parentId, name, excludeId)
        : resourceDao.existsByParentAndName(projectId, parentId, name, excludeId);
  }

  @Override
  public boolean insert(ResourceNode resource) {
    if (resource == null) return false;
    currentProject.current().ifPresent(
        context -> {
          ensureCurrentProject(resource.getProjectId());
          resource.setProjectId(context.projectId());
        });
    ResourcePO po = toPersistence(resource);
    boolean inserted = resourceDao.insert(po) > 0;
    if (inserted) resource.setId(po.getId());
    return inserted;
  }

  @Override
  public boolean update(ResourceNode resource) {
    if (resource == null) return false;
    ensureCurrentProject(resource.getProjectId());
    return resourceDao.update(toPersistence(resource));
  }

  @Override
  public boolean updateBatch(List<ResourceNode> resources) {
    if (resources == null || resources.isEmpty()) return true;
    resources.forEach(resource -> ensureCurrentProject(resource.getProjectId()));
    return resourceDao.updateBatch(resources.stream().map(this::toPersistence).toList());
  }

  @Override
  public boolean deleteBatch(List<Long> ids) {
    Long projectId = currentProjectId();
    return projectId == null
        ? resourceDao.deleteBatch(ids)
        : resourceDao.deleteBatch(projectId, ids);
  }

  @Override
  public List<ResourceNode> findChildren(Long parentId, String keyword) {
    Long projectId = currentProjectId();
    List<ResourcePO> rows =
        projectId == null
            ? resourceDao.selectChildren(parentId, keyword)
            : resourceDao.selectChildren(projectId, parentId, keyword);
    return rows.stream().map(this::toDomain).toList();
  }

  @Override
  public List<ResourceNode> findAll() {
    Long projectId = currentProjectId();
    List<ResourcePO> rows =
        projectId == null ? resourceDao.selectAll() : resourceDao.selectAll(projectId);
    return rows.stream().map(this::toDomain).toList();
  }

  @Override
  public List<ResourceNode> findDescendants(String fullPath) {
    Long projectId = currentProjectId();
    List<ResourcePO> rows =
        projectId == null
            ? resourceDao.selectDescendants(fullPath)
            : resourceDao.selectDescendants(projectId, fullPath);
    return rows.stream().map(this::toDomain).toList();
  }

  @Override
  public PageData<ResourceNode> page(ResourceQuery query) {
    ResourceQuery condition = query == null
        ? new ResourceQuery(1, 20, null, null, null)
        : query;
    IPage<ResourcePO> page = resourceDao.selectPage(
        new PageQuery(
            currentProjectId(),
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

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private void ensureCurrentProject(Long ownerProjectId) {
    currentProject.current().ifPresent(
        context -> {
          if (ownerProjectId != null && !Objects.equals(context.projectId(), ownerProjectId)) {
            throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
          }
        });
  }

  ResourceNode toDomain(ResourcePO po) {
    if (po == null) return null;
    ResourceNode domain = new ResourceNode();
    domain.setId(po.getId());
    domain.setProjectId(po.getProjectId());
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
    po.setProjectId(domain.getProjectId());
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
