package io.yak.ops.business.resource.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.util.List;

/** 资源数据访问接口，只暴露持久化模型和 DAO 查询条件。 */
public interface ResourceDao {

  int insert(ResourcePO resourcePO);

  boolean update(ResourcePO resourcePO);

  ResourcePO selectById(Long id);

  ResourcePO selectByFullPath(String fullPath);

  boolean existsByParentAndName(Long parentId, String name, Long excludeId);

  List<ResourcePO> selectChildren(Long parentId, String keyword);

  List<ResourcePO> selectAll();

  List<ResourcePO> selectDescendants(String fullPath);

  IPage<ResourcePO> selectPage(PageQuery query);

  boolean updateBatch(List<ResourcePO> resources);

  boolean deleteBatch(List<Long> ids);

  /** DAO 自有分页条件，不依赖 HTTP DTO。 */
  record PageQuery(
      int pageNo,
      int pageSize,
      Long parentId,
      String keyword,
      ResourceNodeType nodeType) {}
}
