package io.yak.ops.business.datasource.service.impl;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.datasource.service.DataSourceService;
import io.yak.ops.business.datasource.service.support.DataSourceViewMapper;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 数据源管理应用服务；只负责编排 Domain、Repository 和 Business Gateway。 */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

  private final DataSourceRepository repository;
  private final DataSourcePluginGateway pluginGateway;
  private final DataSourceProperties properties;
  private final DataSourceViewMapper viewMapper;

  @Override
  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean createDataSource(DataSourceDTO dataSourceDTO) {
    String name = normalizeName(dataSourceDTO.getName());
    ensureNameAvailable(name, null);

    DataSourceDbType dbType = parseDbType(dataSourceDTO.getDbType());
    DataSourceEnvironment environment = parseEnvironment(dataSourceDTO.getEnvironment());
    ConnectionProfile connectionProfile =
        pluginGateway.normalizeConnection(dbType, dataSourceDTO.getConnectionParams());
    DataSourceDefinition definition =
        DataSourceDefinition.create(
            name,
            dbType,
            connectionProfile,
            environment,
            normalizeNullable(dataSourceDTO.getRemark()));

    if (!repository.insert(definition)) {
      throw new DataSourceException(DataSourceErrorCode.CREATE_FAILED);
    }
    return true;
  }

  @Override
  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean updateDataSource(Long id, DataSourceDTO dataSourceDTO) {
    DataSourceDefinition existing = getDataSourceOrThrow(id);
    String name = normalizeName(dataSourceDTO.getName());
    ensureNameAvailable(name, id);

    DataSourceDbType requestedType = parseDbType(dataSourceDTO.getDbType());
    try {
      existing.assertTypeUnchanged(requestedType);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_DB_TYPE,
          exception.getMessage(),
          exception);
    }

    String mergedConnectionJson =
        pluginGateway.mergeStoredSecrets(
            requestedType,
            dataSourceDTO.getConnectionParams(),
            existing.getConnectionParams());
    ConnectionProfile connectionProfile =
        pluginGateway.normalizeConnection(requestedType, mergedConnectionJson);
    DataSourceEnvironment environment = parseEnvironment(dataSourceDTO.getEnvironment());
    existing.updateConfiguration(
        name,
        requestedType,
        connectionProfile,
        environment,
        normalizeNullable(dataSourceDTO.getRemark()));

    if (!repository.update(existing)) {
      throw new DataSourceException(DataSourceErrorCode.UPDATE_FAILED);
    }
    return true;
  }

  @Override
  public DataSourceVO getDataSource(Long id) {
    return viewMapper.definition(getDataSourceOrThrow(id), true);
  }

  @Override
  public PagingData<DataSourceVO> getDataSourcePage(DataSourceQueryDTO queryDTO) {
    PageData<DataSourceDefinition> page = repository.page(toQuery(queryDTO));
    return PagingData.from(page.map(value -> viewMapper.definition(value, false)));
  }

  @Override
  public DataSourceSummaryVO getSummary() {
    return viewMapper.summary(repository.summary());
  }

  @Override
  public PagingData<DataSourceVO> getAllDataSources() {
    List<DataSourceVO> records =
        repository.findAll(null).stream()
            .map(value -> viewMapper.definition(value, false))
            .toList();
    long pages = records.isEmpty() ? 0L : 1L;
    long pageSize = Math.max(1, records.size());
    return PagingData.from(new PageData<>(records, records.size(), pages, 1L, pageSize));
  }

  @Override
  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean deleteDataSource(Long id) {
    getDataSourceOrThrow(id);
    if (!repository.delete(id)) {
      throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
    }
    return true;
  }

  @Override
  public boolean testConnection(Long id) {
    DataSourceDefinition definition = getDataSourceOrThrow(id);
    try {
      pluginGateway.testConnection(
          definition.getDbType(),
          definition.connectionProfile(),
          connectionTimeoutSeconds());
      definition.markConnected();
      repository.updateConnectionStatus(id, definition.getConnStatus());
      return true;
    } catch (RuntimeException exception) {
      DataSourceException mapped = connectException(exception);
      if (DataSourceErrorCode.CONNECT_FAILED.equals(mapped.getErrorCode())) {
        definition.markDisconnected();
        repository.updateConnectionStatus(id, definition.getConnStatus());
      }
      throw mapped;
    }
  }

  @Override
  public boolean testConnection(DataSourceConnectTestDTO connectTestDTO) {
    if (connectTestDTO == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "连接测试参数不能为空");
    }

    String connectionJson = connectTestDTO.getConnJson();
    DataSourceDbType dbType;
    if (connectTestDTO.getDataSourceId() != null) {
      DataSourceDefinition existing = getDataSourceOrThrow(connectTestDTO.getDataSourceId());
      dbType = existing.getDbType();
      if (StringUtils.hasText(connectTestDTO.getDbType())
          && parseDbType(connectTestDTO.getDbType()) != dbType) {
        throw new DataSourceException(
            DataSourceErrorCode.INVALID_DB_TYPE,
            "连接测试的数据源类型与已保存数据源不一致");
      }
      connectionJson =
          pluginGateway.mergeStoredSecrets(
              dbType,
              connectionJson,
              existing.getConnectionParams());
    } else {
      dbType =
          StringUtils.hasText(connectTestDTO.getDbType())
              ? parseDbType(connectTestDTO.getDbType())
              : pluginGateway.resolveConnectionType(connectionJson);
    }

    ConnectionProfile connectionProfile =
        pluginGateway.normalizeConnection(dbType, connectionJson);
    try {
      pluginGateway.testConnection(dbType, connectionProfile, connectionTimeoutSeconds());
      return true;
    } catch (RuntimeException exception) {
      throw connectException(exception);
    }
  }

  @Override
  public List<DataSourceOptionVO> getOptions(String dbType) {
    DataSourceDbType normalizedType = StringUtils.hasText(dbType) ? parseDbType(dbType) : null;
    return repository.findAll(normalizedType).stream().map(viewMapper::option).toList();
  }

  private DataSourceException connectException(RuntimeException exception) {
    if (exception instanceof DataSourceException dataSourceException) {
      return dataSourceException;
    }
    return new DataSourceException(
        DataSourceErrorCode.CONNECT_FAILED,
        exception.getMessage(),
        exception);
  }

  private int connectionTimeoutSeconds() {
    return Math.max(1, properties.getConnectionTest().getTimeoutSeconds());
  }

  private DataSourceDefinition getDataSourceOrThrow(Long id) {
    if (id == null || id <= 0L) {
      throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
    }
    return repository.findById(id)
        .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
  }

  private void ensureNameAvailable(String name, Long excludeId) {
    if (repository.existsByName(name, excludeId)) {
      throw new DataSourceException(DataSourceErrorCode.DUPLICATE_NAME);
    }
  }

  private DataSourceQuery toQuery(DataSourceQueryDTO queryDTO) {
    if (queryDTO == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "分页查询参数不能为空");
    }
    return new DataSourceQuery(
        queryDTO.getPageNo(),
        queryDTO.getPageSize(),
        normalizeNullable(queryDTO.getName()),
        normalizeNullable(queryDTO.getKeyword()),
        StringUtils.hasText(queryDTO.getDbType()) ? parseDbType(queryDTO.getDbType()) : null,
        StringUtils.hasText(queryDTO.getEnvironment())
            ? parseEnvironment(queryDTO.getEnvironment())
            : null,
        StringUtils.hasText(queryDTO.getConnStatus())
            ? parseConnectionStatus(queryDTO.getConnStatus())
            : null);
  }

  private String normalizeName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "数据源名称不能为空");
    }
    return name.trim();
  }

  private String normalizeNullable(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private DataSourceDbType parseDbType(String value) {
    try {
      return DataSourceDbType.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_DB_TYPE,
          exception.getMessage(),
          exception);
    }
  }

  private DataSourceEnvironment parseEnvironment(String value) {
    try {
      return DataSourceEnvironment.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_ENVIRONMENT,
          exception.getMessage(),
          exception);
    }
  }

  private DataSourceConnStatus parseConnectionStatus(String value) {
    try {
      return DataSourceConnStatus.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_STATUS,
          "不支持的连接状态：" + value,
          exception);
    }
  }
}
