package io.yak.ops.business.sync.offline.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import java.time.LocalDateTime;
import java.util.List;

/** 离线同步任务定义数据访问接口，只暴露持久化模型和 DAO 查询条件。 */
public interface OfflineJobDefinitionDao {

  OfflineJobDefinitionPO selectById(Long id);

  boolean insert(OfflineJobDefinitionPO definitionPO);

  boolean updateById(OfflineJobDefinitionPO definitionPO);

  boolean deleteById(Long id);

  boolean existsByName(String jobName, Long excludeId);

  IPage<OfflineJobDefinitionPO> selectPage(PageQuery query);

  List<OfflineJobDefinitionPO> selectWithCron();

  Long lockById(Long id);

  record PageQuery(
      int current,
      int pageSize,
      Long id,
      String jobName,
      String status,
      String sourceType,
      String sinkType,
      String sourceTable,
      String sinkTable,
      LocalDateTime createTimeStart,
      LocalDateTime createTimeEnd) {}
}
