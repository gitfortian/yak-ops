package io.yak.ops.business.datasource.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.DataSourceDao.PageQuery;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** MyBatis persistence adapter; persistence rehydration is explicit and cannot mutate aggregates via setters. */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceRepositoryAdapter implements DataSourceRepository {

  private final DataSourceDao dao;
  private final CurrentProject currentProject;

  @Autowired
  public DataSourceRepositoryAdapter(DataSourceDao dao, CurrentProject currentProject) {
    this.dao = dao;
    this.currentProject = currentProject;
  }

  public DataSourceRepositoryAdapter(DataSourceDao dao) {
    this(dao, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public Optional<DataSourceDefinition> findById(Long id) {
    Long projectId = currentProjectId();
    DataSourcePO row = projectId == null ? dao.selectById(id) : dao.selectById(projectId, id);
    return Optional.ofNullable(toDomain(row));
  }

  @Override
  public boolean insert(DataSourceDefinition definition) {
    currentProject.current().ifPresent(context -> definition.assignProject(context.projectId()));
    return dao.addDataSource(toPO(definition)) > 0;
  }

  @Override
  public boolean update(DataSourceDefinition definition) {
    ensureCurrentProject(definition.getProjectId());
    return dao.editDataSource(toPO(definition)) > 0;
  }

  @Override
  public boolean delete(Long id) {
    Long projectId = currentProjectId();
    return projectId == null ? dao.deleteById(id) : dao.deleteById(projectId, id);
  }

  @Override
  public boolean existsByName(String name, Long excludeId) {
    Long projectId = currentProjectId();
    return projectId == null
        ? dao.existsByName(name, excludeId)
        : dao.existsByName(projectId, name, excludeId);
  }

  @Override
  public PageData<DataSourceDefinition> page(DataSourceQuery query) {
    DataSourceQuery condition =
        query == null ? new DataSourceQuery(1, 10, null, null, null, null, null) : query;
    IPage<DataSourcePO> page =
        dao.selectPage(
            new PageQuery(
                currentProjectId(),
                condition.pageNo(),
                condition.pageSize(),
                condition.name(),
                condition.keyword(),
                condition.dbType(),
                condition.environment(),
                condition.connStatus()));
    List<DataSourceDefinition> records = page.getRecords().stream().map(this::toDomain).toList();
    return new PageData<>(
        records,
        page.getTotal(),
        page.getPages(),
        page.getCurrent(),
        page.getSize());
  }

  @Override
  public List<DataSourceDefinition> findAll(DataSourceDbType dbType) {
    Long projectId = currentProjectId();
    List<DataSourcePO> rows =
        projectId == null ? dao.selectAll(dbType) : dao.selectAll(projectId, dbType);
    return rows.stream().map(this::toDomain).toList();
  }

  @Override
  public DataSourceSummary summary() {
    Long projectId = currentProjectId();
    DataSourceSummaryRow row =
        projectId == null ? dao.selectSummary() : dao.selectSummary(projectId);
    return row == null
        ? DataSourceSummary.empty()
        : new DataSourceSummary(
            row.getTotal(),
            row.getConnected(),
            row.getDisconnected(),
            row.getUnknown(),
            row.getEnvironmentCount());
  }

  @Override
  public boolean updateConnectionStatus(Long id, DataSourceConnStatus status) {
    Long projectId = currentProjectId();
    return projectId == null
        ? dao.updateConnectionStatus(id, status)
        : dao.updateConnectionStatus(projectId, id, status);
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private void ensureCurrentProject(Long ownerProjectId) {
    currentProject.current().ifPresent(
        context -> {
          if (!Objects.equals(context.projectId(), ownerProjectId)) {
            throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
          }
        });
  }

  private DataSourceDefinition toDomain(DataSourcePO po) {
    if (po == null) return null;
    return DataSourceDefinition.restore(
        po.getId(),
        po.getProjectId(),
        po.getName(),
        po.getDbType(),
        po.getJdbcUrl(),
        po.getEnvironment(),
        po.getConnStatus(),
        po.getRemark(),
        po.getConnectionParams(),
        po.getOriginalJson(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private DataSourcePO toPO(DataSourceDefinition definition) {
    DataSourcePO po = new DataSourcePO();
    po.setId(definition.getId());
    po.setProjectId(definition.getProjectId());
    po.setName(definition.getName());
    po.setDbType(definition.getDbType());
    po.setJdbcUrl(definition.getJdbcUrl());
    po.setEnvironment(definition.getEnvironment());
    po.setConnStatus(definition.getConnStatus());
    po.setRemark(definition.getRemark());
    po.setConnectionParams(definition.getConnectionParams());
    po.setOriginalJson(definition.getOriginalJson());
    po.setCreateTime(definition.getCreateTime());
    po.setUpdateTime(definition.getUpdateTime());
    return po;
  }
}
