package io.yak.ops.business.datasource.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.mapper.DataSourceMapper;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的数据源数据访问实现。 */
@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceDaoImpl implements DataSourceDao {

  private final DataSourceMapper dataSourceMapper;

  @Override
  public int addDataSource(DataSourcePO dataSourcePO) {
    return dataSourceMapper.insert(dataSourcePO);
  }

  @Override
  public int editDataSource(DataSourcePO dataSourcePO) {
    return dataSourceMapper.updateById(dataSourcePO);
  }

  @Override
  public DataSourcePO selectById(Long id) {
    return selectById(null, id);
  }

  @Override
  public DataSourcePO selectById(Long projectId, Long id) {
    if (id == null) return null;
    if (projectId == null) return dataSourceMapper.selectById(id);
    return dataSourceMapper.selectOne(
        Wrappers.<DataSourcePO>lambdaQuery()
            .eq(DataSourcePO::getProjectId, projectId)
            .eq(DataSourcePO::getId, id));
  }

  @Override
  public IPage<DataSourcePO> selectPage(PageQuery query) {
    PageQuery condition =
        query == null ? new PageQuery(null, 1, 10, null, null, null, null, null) : query;
    Page<DataSourcePO> page =
        Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
    return dataSourceMapper.selectPage(
        page,
        queryWrapper(condition)
            .orderByDesc(DataSourcePO::getUpdateTime)
            .orderByDesc(DataSourcePO::getId));
  }

  @Override
  public DataSourceSummaryRow selectSummary() {
    return dataSourceMapper.selectSummary();
  }

  @Override
  public DataSourceSummaryRow selectSummary(Long projectId) {
    return projectId == null
        ? dataSourceMapper.selectSummary()
        : dataSourceMapper.selectSummaryByProject(projectId);
  }

  @Override
  public List<DataSourcePO> selectAll(DataSourceDbType dbType) {
    return selectAll(null, dbType);
  }

  @Override
  public List<DataSourcePO> selectAll(Long projectId, DataSourceDbType dbType) {
    return dataSourceMapper.selectList(
        Wrappers.<DataSourcePO>lambdaQuery()
            .eq(projectId != null, DataSourcePO::getProjectId, projectId)
            .eq(dbType != null, DataSourcePO::getDbType, dbType)
            .orderByAsc(DataSourcePO::getName)
            .orderByAsc(DataSourcePO::getId));
  }

  @Override
  public boolean existsByName(String name, Long excludeId) {
    return existsByName(null, name, excludeId);
  }

  @Override
  public boolean existsByName(Long projectId, String name, Long excludeId) {
    if (!StringUtils.hasText(name)) return false;
    Long count =
        dataSourceMapper.selectCount(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(projectId != null, DataSourcePO::getProjectId, projectId)
                .eq(DataSourcePO::getName, name)
                .ne(excludeId != null, DataSourcePO::getId, excludeId));
    return count != null && count > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    return deleteById(null, id);
  }

  @Override
  public boolean deleteById(Long projectId, Long id) {
    if (id == null) return false;
    if (projectId == null) return dataSourceMapper.deleteById(id) > 0;
    return dataSourceMapper.delete(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, projectId)
                .eq(DataSourcePO::getId, id))
        > 0;
  }

  @Override
  public boolean updateConnectionStatus(Long id, DataSourceConnStatus connStatus) {
    return updateConnectionStatus(null, id, connStatus);
  }

  @Override
  public boolean updateConnectionStatus(
      Long projectId, Long id, DataSourceConnStatus connStatus) {
    return id != null
        && connStatus != null
        && dataSourceMapper.update(
                null,
                Wrappers.<DataSourcePO>lambdaUpdate()
                    .set(DataSourcePO::getConnStatus, connStatus)
                    .eq(projectId != null, DataSourcePO::getProjectId, projectId)
                    .eq(DataSourcePO::getId, id))
            > 0;
  }

  private LambdaQueryWrapper<DataSourcePO> queryWrapper(PageQuery query) {
    LambdaQueryWrapper<DataSourcePO> wrapper = Wrappers.lambdaQuery();
    if (StringUtils.hasText(query.keyword())) {
      wrapper.and(
          nested ->
              nested
                  .like(DataSourcePO::getName, query.keyword())
                  .or()
                  .like(DataSourcePO::getJdbcUrl, query.keyword()));
    }
    return wrapper
        .eq(query.projectId() != null, DataSourcePO::getProjectId, query.projectId())
        .like(StringUtils.hasText(query.name()), DataSourcePO::getName, query.name())
        .eq(query.dbType() != null, DataSourcePO::getDbType, query.dbType())
        .eq(query.environment() != null, DataSourcePO::getEnvironment, query.environment())
        .eq(query.connStatus() != null, DataSourcePO::getConnStatus, query.connStatus());
  }
}
