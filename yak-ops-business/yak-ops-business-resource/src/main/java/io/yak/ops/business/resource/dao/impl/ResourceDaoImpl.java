package io.yak.ops.business.resource.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.dao.mapper.ResourceMapper;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的资源数据访问实现。 */
@Repository
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceDaoImpl implements ResourceDao {

  private final ResourceMapper resourceMapper;

  @Override
  public int insert(ResourcePO resourcePO) {
    return resourceMapper.insert(resourcePO);
  }

  @Override
  public boolean update(ResourcePO resourcePO) {
    return resourcePO != null && resourceMapper.updateById(resourcePO) > 0;
  }

  @Override
  public ResourcePO selectById(Long id) {
    return id == null ? null : resourceMapper.selectById(id);
  }

  @Override
  public ResourcePO selectByFullPath(String fullPath) {
    if (!StringUtils.hasText(fullPath)) {
      return null;
    }
    return resourceMapper.selectOne(
        Wrappers.<ResourcePO>lambdaQuery().eq(ResourcePO::getFullPath, fullPath));
  }

  @Override
  public boolean existsByParentAndName(Long parentId, String name, Long excludeId) {
    Long count =
        resourceMapper.selectCount(
            Wrappers.<ResourcePO>lambdaQuery()
                .eq(ResourcePO::getParentId, parentId == null ? 0L : parentId)
                .eq(ResourcePO::getName, name)
                .ne(excludeId != null, ResourcePO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public List<ResourcePO> selectChildren(Long parentId, String keyword) {
    return resourceMapper.selectList(
        Wrappers.<ResourcePO>lambdaQuery()
            .eq(ResourcePO::getParentId, parentId == null ? 0L : parentId)
            .and(
                StringUtils.hasText(keyword),
                nested ->
                    nested.like(ResourcePO::getName, keyword)
                        .or()
                        .like(ResourcePO::getFullPath, keyword))
            .orderByAsc(ResourcePO::getNodeType)
            .orderByAsc(ResourcePO::getName)
            .orderByAsc(ResourcePO::getId));
  }

  @Override
  public List<ResourcePO> selectAll() {
    return resourceMapper.selectList(
        Wrappers.<ResourcePO>lambdaQuery()
            .orderByAsc(ResourcePO::getFullPath)
            .orderByAsc(ResourcePO::getId));
  }

  @Override
  public List<ResourcePO> selectDescendants(String fullPath) {
    if (!StringUtils.hasText(fullPath)) {
      return Collections.emptyList();
    }
    return resourceMapper.selectList(
        Wrappers.<ResourcePO>lambdaQuery()
            .likeRight(ResourcePO::getFullPath, fullPath + "/")
            .orderByAsc(ResourcePO::getFullPath));
  }

  @Override
  public IPage<ResourcePO> selectPage(PageQuery query) {
    PageQuery condition = query == null ? new PageQuery(1, 20, null, null, null) : query;
    Page<ResourcePO> page =
        Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
    LambdaQueryWrapper<ResourcePO> wrapper = Wrappers.lambdaQuery();
    wrapper
        .eq(condition.parentId() != null, ResourcePO::getParentId, condition.parentId())
        .and(
            StringUtils.hasText(condition.keyword()),
            nested ->
                nested.like(ResourcePO::getName, condition.keyword())
                    .or()
                    .like(ResourcePO::getFullPath, condition.keyword()))
        .eq(condition.nodeType() != null, ResourcePO::getNodeType, condition.nodeType())
        .orderByAsc(ResourcePO::getNodeType)
        .orderByAsc(ResourcePO::getName)
        .orderByAsc(ResourcePO::getId);
    return resourceMapper.selectPage(page, wrapper);
  }

  @Override
  public boolean updateBatch(List<ResourcePO> resources) {
    if (resources == null || resources.isEmpty()) {
      return true;
    }
    for (ResourcePO resource : resources) {
      if (resourceMapper.updateById(resource) <= 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean deleteBatch(List<Long> ids) {
    return ids != null && !ids.isEmpty() && resourceMapper.deleteByIds(ids) == ids.size();
  }
}
