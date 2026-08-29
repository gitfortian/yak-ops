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
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * MyBatis DataSource DAO. Stage 7.2 removes the former Offline/Realtime no-Project compatibility
 * corridor: every business read/write now requires the trusted CurrentProject.
 */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceDaoImpl implements DataSourceDao {

  private final DataSourceMapper dataSourceMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public DataSourceDaoImpl(DataSourceMapper dataSourceMapper, CurrentProject currentProject) {
    this.dataSourceMapper = dataSourceMapper;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor; operations remain fail-closed without CurrentProject. */
  public DataSourceDaoImpl(DataSourceMapper dataSourceMapper) {
    this(dataSourceMapper, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public int addDataSource(DataSourcePO dataSourcePO) {
    long projectId = currentProjectId();
    if (dataSourcePO.getProjectId() != null
        && !Objects.equals(projectId, dataSourcePO.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    dataSourcePO.setProjectId(projectId);
    return dataSourceMapper.insert(dataSourcePO);
  }

  @Override
  public int editDataSource(DataSourcePO dataSourcePO) {
    long projectId = currentProjectId();
    if (dataSourcePO.getProjectId() != null
        && !Objects.equals(projectId, dataSourcePO.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    dataSourcePO.setProjectId(projectId);
    return dataSourceMapper.update(
        dataSourcePO,
        Wrappers.<DataSourcePO>lambdaUpdate()
            .eq(DataSourcePO::getProjectId, projectId)
            .eq(DataSourcePO::getId, dataSourcePO.getId()));
  }

  @Override
  public DataSourcePO selectById(Long id) {
    return selectById(currentProjectId(), id);
  }

  @Override
  public DataSourcePO selectById(Long projectId, Long id) {
    long trustedProjectId = requireCurrentProject(projectId);
    if (id == null) return null;
    return dataSourceMapper.selectOne(
        Wrappers.<DataSourcePO>lambdaQuery()
            .eq(DataSourcePO::getProjectId, trustedProjectId)
            .eq(DataSourcePO::getId, id));
  }

  @Override
  public List<DataSourcePO> selectByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    List<Long> normalizedIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (normalizedIds.isEmpty()) return List.of();
    long projectId = currentProjectId();
    return dataSourceMapper.selectList(
        Wrappers.<DataSourcePO>lambdaQuery()
            .eq(DataSourcePO::getProjectId, projectId)
            .in(DataSourcePO::getId, normalizedIds));
  }

  @Override
  public IPage<DataSourcePO> selectPage(PageQuery query) {
    long projectId = currentProjectId();
    PageQuery source = query == null
        ? new PageQuery(projectId, 1, 10, null, null, null, null, null)
        : query;
    long trustedProjectId =
        source.projectId() == null ? projectId : requireCurrentProject(source.projectId());
    PageQuery condition =
        new PageQuery(
            trustedProjectId,
            source.pageNo(),
            source.pageSize(),
            source.name(),
            source.keyword(),
            source.dbType(),
            source.environment(),
            source.connStatus());
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
    return selectSummary(currentProjectId());
  }

  @Override
  public DataSourceSummaryRow selectSummary(Long projectId) {
    return dataSourceMapper.selectSummaryByProject(requireCurrentProject(projectId));
  }

  @Override
  public List<DataSourcePO> selectAll(DataSourceDbType dbType) {
    return selectAll(currentProjectId(), dbType);
  }

  @Override
  public List<DataSourcePO> selectAll(Long projectId, DataSourceDbType dbType) {
    long trustedProjectId = requireCurrentProject(projectId);
    return dataSourceMapper.selectList(
        Wrappers.<DataSourcePO>lambdaQuery()
            .eq(DataSourcePO::getProjectId, trustedProjectId)
            .eq(dbType != null, DataSourcePO::getDbType, dbType)
            .orderByAsc(DataSourcePO::getName)
            .orderByAsc(DataSourcePO::getId));
  }

  @Override
  public boolean existsByName(String name, Long excludeId) {
    return existsByName(currentProjectId(), name, excludeId);
  }

  @Override
  public boolean existsByName(Long projectId, String name, Long excludeId) {
    if (!StringUtils.hasText(name)) return false;
    long trustedProjectId = requireCurrentProject(projectId);
    Long count =
        dataSourceMapper.selectCount(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getName, name)
                .ne(excludeId != null, DataSourcePO::getId, excludeId));
    return count != null && count > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    return deleteById(currentProjectId(), id);
  }

  @Override
  public boolean deleteById(Long projectId, Long id) {
    if (id == null) return false;
    long trustedProjectId = requireCurrentProject(projectId);
    return dataSourceMapper.delete(
            Wrappers.<DataSourcePO>lambdaQuery()
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getId, id))
        > 0;
  }

  @Override
  public boolean updateConnectionStatus(Long id, DataSourceConnStatus connStatus) {
    return updateConnectionStatus(currentProjectId(), id, connStatus);
  }

  @Override
  public boolean updateConnectionStatus(
      Long projectId, Long id, DataSourceConnStatus connStatus) {
    if (id == null || connStatus == null) return false;
    long trustedProjectId = requireCurrentProject(projectId);
    return dataSourceMapper.update(
            null,
            Wrappers.<DataSourcePO>lambdaUpdate()
                .set(DataSourcePO::getConnStatus, connStatus)
                .eq(DataSourcePO::getProjectId, trustedProjectId)
                .eq(DataSourcePO::getId, id))
        > 0;
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private long requireCurrentProject(Long requestedProjectId) {
    long projectId = currentProjectId();
    if (requestedProjectId == null || requestedProjectId <= 0L
        || !Objects.equals(projectId, requestedProjectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return projectId;
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
        .eq(DataSourcePO::getProjectId, query.projectId())
        .like(StringUtils.hasText(query.name()), DataSourcePO::getName, query.name())
        .eq(query.dbType() != null, DataSourcePO::getDbType, query.dbType())
        .eq(query.environment() != null, DataSourcePO::getEnvironment, query.environment())
        .eq(query.connStatus() != null, DataSourcePO::getConnStatus, query.connStatus());
  }
}
