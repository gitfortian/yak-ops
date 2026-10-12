package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogService;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.security.DataSourceCredentials;
import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectionDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceBatchConnectTestResultVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceConnectionPropertyKeysVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.dao.repository.datasource.DataSourcePageQuery;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Implements Workspace-scoped datasource CRUD, search, connectivity tests and Entity-to-VO projection.
 *
 * <p>Owns Workspace validation and persistence. Plugins resolve connections;
 * the Catalog and credential services own their respective operations.
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceServiceImpl.class);

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private DataSourceCatalogService catalogService;

    @Resource
    private DataSourceCredentials credentials;

    @Resource
    private DataSourceProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDataSource(DataSourceDTO dto) {
        requireDataSourceDto(dto);
        String workspaceId = requireWorkspaceId();
        String name = normalizeName(dto.getName());
        DataSourcePlugin provider = plugin(dto.getDbType());
        String dbType = provider.descriptor().type();
        ensureNameAvailable(workspaceId, name, null);
        String connectionJson = serializeConnectionParams(dto.getConnectionParams());
        DataSourceConnection connection = parseConnection(provider, connectionJson);

        DataSourceEntity entity = new DataSourceEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setDbType(dbType);
        entity.setJdbcUrl(connection.jdbcUrl());
        entity.setEnvironment(parseEnvironment(dto.getEnvironment()));
        entity.setConnStatus(DataSourceConnStatus.UNKNOWN);
        entity.setRemark(normalizeNullable(dto.getRemark()));
        entity.setConnectionParams(connection.normalizedJson());
        entity.setOriginalJson(connection.normalizedJson());
        entity.initCreate();
        if (repository.add(entity) == null) {
            throw new DataSourceException(DataSourceErrorCode.CREATE_FAILED);
        }
        LOG.info(
                "Datasource created, workspaceId={}, dataSourceId={}, type={}",
                workspaceId,
                entity.getId(),
                entity.getDbType());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDataSource(String id, DataSourceDTO dto) {
        requireDataSourceDto(dto);
        String workspaceId = requireWorkspaceId();
        DataSourceEntity existing = requireEntity(workspaceId, id);
        String name = normalizeName(dto.getName());
        DataSourcePlugin provider = plugin(dto.getDbType());
        String dbType = provider.descriptor().type();
        ensureNameAvailable(workspaceId, name, id);
        if (!existing.getDbType().equals(dbType)) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "编辑数据源时不允许修改数据源类型");
        }

        String submitted = serializeConnectionParams(dto.getConnectionParams());
        String json = credentials.retainOnUpdate(provider.descriptor(), submitted, existing.getConnectionParams());
        DataSourceConnection connection = parseConnection(provider, json);
        existing.setName(name);
        existing.setJdbcUrl(connection.jdbcUrl());
        existing.setEnvironment(parseEnvironment(dto.getEnvironment()));
        existing.setConnStatus(DataSourceConnStatus.UNKNOWN);
        existing.setRemark(normalizeNullable(dto.getRemark()));
        existing.setConnectionParams(connection.normalizedJson());
        existing.setOriginalJson(connection.normalizedJson());
        existing.initUpdate();

        if (repository.update(workspaceId, existing) == null) {
            throw new DataSourceException(DataSourceErrorCode.UPDATE_FAILED);
        }
        LOG.info(
                "Datasource updated, workspaceId={}, dataSourceId={}, type={}",
                workspaceId,
                existing.getId(),
                existing.getDbType());
        return true;
    }

    @Override
    public DataSourceVO queryDataSource(String id) {
        String workspaceId = requireWorkspaceId();
        return toDataSourceVO(requireEntity(workspaceId, id), true);
    }

    @Override
    public DataSourceConnectionPropertyKeysVO queryConnectionPropertyKeys(String dbType) {
        DataSourceConnectionPropertyKeysVO result = new DataSourceConnectionPropertyKeysVO();
        result.setAcceptedPropertyKeys(plugin(dbType).connectionPropertyKeys());
        return result;
    }

    @Override
    public List<String> queryCatalogDatabases(String id) {
        DataSourceEntity entity = requireEntity(requireWorkspaceId(), id);
        DataSourcePlugin provider = plugin(entity.getDbType());
        return catalogService.databases(
                provider, parseConnection(provider, entity.getConnectionParams()), connectionTestTimeoutSeconds());
    }

    @Override
    public List<String> queryCatalogSchemas(String id, String database) {
        DataSourceEntity entity = requireEntity(requireWorkspaceId(), id);
        DataSourcePlugin provider = plugin(entity.getDbType());
        return catalogService.schemas(
                provider,
                parseConnection(provider, entity.getConnectionParams()),
                connectionTestTimeoutSeconds(),
                normalizeNullable(database));
    }

    @Override
    public List<DataSourceCatalogTableVO> queryCatalogTables(String id, DataSourceCatalogQueryDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_QUERY_FAILED, "表查询参数不能为空");
        }
        DataSourceEntity entity = requireEntity(requireWorkspaceId(), id);
        DataSourcePlugin provider = plugin(entity.getDbType());
        return catalogService
                .tables(
                        provider,
                        parseConnection(provider, entity.getConnectionParams()),
                        connectionTestTimeoutSeconds(),
                        normalizeNullable(dto.getDatabase()),
                        normalizeNullable(dto.getSchema()),
                        normalizeNullable(dto.getKeyword()),
                        dto.getLimit())
                .stream()
                .map(this::toCatalogTableVO)
                .toList();
    }

    @Override
    public List<DataSourceCatalogColumnVO> queryCatalogColumns(String id, DataSourceTablePathDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTable())) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_QUERY_FAILED, "表定位信息不能为空");
        }
        DataSourceEntity entity = requireEntity(requireWorkspaceId(), id);
        DataSourcePlugin provider = plugin(entity.getDbType());
        TableId tableId = new TableId(
                normalizeNullable(dto.getDatabase()),
                normalizeNullable(dto.getSchema()),
                dto.getTable().trim());
        return catalogService
                .columns(
                        provider,
                        parseConnection(provider, entity.getConnectionParams()),
                        connectionTestTimeoutSeconds(),
                        tableId)
                .stream()
                .map(this::toCatalogColumnVO)
                .toList();
    }

    @Override
    public TableSchema queryTableSchema(String id, DataSourceTablePathDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTable())) {
            throw new DataSourceException(DataSourceErrorCode.CATALOG_QUERY_FAILED, "表定位信息不能为空");
        }
        DataSourceEntity entity = requireEntity(requireWorkspaceId(), id);
        DataSourcePlugin provider = plugin(entity.getDbType());
        TableId tableId = new TableId(
                normalizeNullable(dto.getDatabase()),
                normalizeNullable(dto.getSchema()),
                dto.getTable().trim());
        return catalogService.tableSchema(
                provider,
                parseConnection(provider, entity.getConnectionParams()),
                connectionTestTimeoutSeconds(),
                tableId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDataSource(String id) {
        String workspaceId = requireWorkspaceId();
        DataSourceEntity existing = requireEntity(workspaceId, id);
        if (repository.deleteById(workspaceId, existing.getId()) <= 0) {
            throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
        }
        LOG.info(
                "Datasource deleted, workspaceId={}, dataSourceId={}, type={}",
                workspaceId,
                existing.getId(),
                existing.getDbType());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteDataSources(DataSourceBatchIdsDTO dto) {
        String workspaceId = requireWorkspaceId();
        List<DataSourceEntity> entities = requireBatchEntities(workspaceId, dto);
        for (DataSourceEntity entity : entities) {
            if (repository.deleteById(workspaceId, entity.getId()) <= 0) {
                throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
            }
        }
        LOG.info("Datasource batch deletion completed, workspaceId={}, count={}", workspaceId, entities.size());
        return true;
    }

    @Override
    public PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "分页查询参数不能为空");
        }
        String workspaceId = requireWorkspaceId();
        if (dto.getSorts() != null && !dto.getSorts().isEmpty()) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源分页暂不支持自定义排序");
        }
        String dbType = null;
        if (StringUtils.hasText(dto.getDbType())) {
            dbType = plugin(dto.getDbType()).descriptor().type();
        }
        DataSourcePageQuery query = new DataSourcePageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                normalizeNullable(dto.getName()),
                normalizeNullable(dto.getKeyword()),
                dbType,
                StringUtils.hasText(dto.getEnvironment()) ? parseEnvironment(dto.getEnvironment()) : null,
                StringUtils.hasText(dto.getConnStatus()) ? parseConnectionStatus(dto.getConnStatus()) : null);
        return PagingData.from(repository.queryPage(workspaceId, query).map(value -> toDataSourceVO(value, false)));
    }

    @Override
    public boolean testConnection(String id) {
        String workspaceId = requireWorkspaceId();
        return testSavedConnection(workspaceId, requireEntity(workspaceId, id));
    }

    @Override
    public List<DataSourceBatchConnectTestResultVO> batchTestConnections(DataSourceBatchIdsDTO dto) {
        String workspaceId = requireWorkspaceId();
        List<DataSourceEntity> entities = requireBatchEntities(workspaceId, dto);
        List<DataSourceBatchConnectTestResultVO> results = new ArrayList<>(entities.size());
        int successCount = 0;
        for (DataSourceEntity entity : entities) {
            boolean connected;
            try {
                connected = testSavedConnection(workspaceId, entity);
                successCount++;
            } catch (DataSourceException exception) {
                connected = false;
            }
            results.add(toBatchConnectTestResult(entity.getId(), connected));
        }
        LOG.info(
                "Datasource batch connection tests completed, workspaceId={}, total={}, success={}, failed={}",
                workspaceId,
                entities.size(),
                successCount,
                entities.size() - successCount);
        return results;
    }

    @Override
    public boolean testConnection(DataSourceConnectTestDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接测试参数不能为空");
        }

        String workspaceId = requireWorkspaceId();
        DataSourceEntity existing =
                dto.getDataSourceId() == null ? null : requireEntity(workspaceId, dto.getDataSourceId());
        DataSourcePlugin provider = plugin(dto.getDbType());
        String dbType = provider.descriptor().type();
        String connectionJson = serializeConnectionParams(dto.getConnectionParams());

        if (existing != null) {
            if (!dbType.equals(existing.getDbType())) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接测试的数据源类型与已保存数据源不一致");
            }
            connectionJson =
                    credentials.retainOnUpdate(provider.descriptor(), connectionJson, existing.getConnectionParams());
        }

        try {
            testPluginConnection(provider, connectionJson, connectionTestTimeoutSeconds());
            return true;
        } catch (RuntimeException exception) {
            throw connectException(exception);
        }
    }

    private DataSourceEntity requireEntity(String workspaceId, String id) {
        if (!StringUtils.hasText(id)) {
            throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
        }
        return repository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
    }

    private List<DataSourceEntity> requireBatchEntities(String workspaceId, DataSourceBatchIdsDTO dto) {
        List<String> ids = normalizeBatchIds(dto);
        List<DataSourceEntity> entities = new ArrayList<>(ids.size());
        for (String id : ids) {
            entities.add(requireEntity(workspaceId, id));
        }
        return entities;
    }

    private List<String> normalizeBatchIds(DataSourceBatchIdsDTO dto) {
        if (dto == null
                || dto.getIds() == null
                || dto.getIds().isEmpty()
                || dto.getIds().size() > DataSourceBatchIdsDTO.MAX_IDS) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_BATCH_OPERATION);
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : dto.getIds()) {
            if (!StringUtils.hasText(id)) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_BATCH_OPERATION);
            }
            ids.add(id.trim());
        }
        return new ArrayList<>(ids);
    }

    private boolean testSavedConnection(String workspaceId, DataSourceEntity entity) {
        try {
            testPluginConnection(
                    plugin(entity.getDbType()), entity.getConnectionParams(), connectionTestTimeoutSeconds());
            entity.setConnStatus(DataSourceConnStatus.CONNECTED);
            entity.initUpdate();
            repository.update(workspaceId, entity);
            return true;
        } catch (RuntimeException exception) {
            DataSourceException mapped = connectException(exception);
            if (DataSourceErrorCode.CONNECT_FAILED.equals(mapped.getErrorCode())) {
                entity.setConnStatus(DataSourceConnStatus.DISCONNECTED);
                entity.initUpdate();
                repository.update(workspaceId, entity);
            }
            throw mapped;
        }
    }

    private void ensureNameAvailable(String workspaceId, String name, String excludeId) {
        if (repository.existsByName(workspaceId, name, excludeId)) {
            throw new DataSourceException(DataSourceErrorCode.DUPLICATE_NAME);
        }
    }

    private String requireWorkspaceId() {
        return WorkspaceContext.requireWorkspaceId();
    }

    private void requireDataSourceDto(DataSourceDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源参数不能为空");
        }
    }

    private String serializeConnectionParams(DataSourceConnectionDTO connectionParams) {
        if (connectionParams == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源连接参数不能为空");
        }
        return JSONUtils.toJson(connectionParams);
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源名称不能为空");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DataSourceEnvironment parseEnvironment(String value) {
        try {
            return DataSourceEnvironment.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_ENVIRONMENT, exception.getMessage(), exception);
        }
    }

    private DataSourceConnStatus parseConnectionStatus(String value) {
        try {
            return DataSourceConnStatus.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_STATUS, "不支持的连接状态：" + value, exception);
        }
    }

    private DataSourcePlugin plugin(String type) {
        try {
            return pluginRegistry.get(type);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new DataSourceException(DataSourceErrorCode.PLUGIN_NOT_FOUND, "未找到插件：" + type, exception);
        }
    }

    private DataSourceConnection parseConnection(DataSourcePlugin provider, String json) {
        try {
            return provider.parseConnection(json);
        } catch (RuntimeException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
                    credentials.maskText(provider.descriptor(), exception.getMessage()),
                    exception);
        }
    }

    private void testPluginConnection(DataSourcePlugin provider, String json, int timeoutSeconds) {
        if (!provider.supports(DataSourceCapability.CONNECTION_TEST)) {
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, "数据源插件未声明连接测试能力");
        }
        DataSourceConnection connection = parseConnection(provider, json);
        try {
            provider.testConnection(connection, timeoutSeconds);
        } catch (RuntimeException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.CONNECT_FAILED,
                    credentials.maskText(provider.descriptor(), exception.getMessage()),
                    exception);
        }
    }

    private DataSourceException connectException(RuntimeException exception) {
        if (exception instanceof DataSourceException dataSourceException) {
            return dataSourceException;
        }
        return new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
    }

    private int connectionTestTimeoutSeconds() {
        return Math.max(1, properties.getConnectionTestTimeoutSeconds());
    }

    private DataSourceCatalogTableVO toCatalogTableVO(JdbcTableInfo source) {
        DataSourceCatalogTableVO target = new DataSourceCatalogTableVO();
        target.setDatabase(source.tableId().catalog());
        target.setSchema(source.tableId().schema());
        target.setName(source.tableId().table());
        target.setType(source.type());
        target.setRemarks(source.remarks());
        return target;
    }

    private DataSourceCatalogColumnVO toCatalogColumnVO(JdbcColumnInfo source) {
        DataSourceCatalogColumnVO target = new DataSourceCatalogColumnVO();
        target.setName(source.name());
        target.setTypeName(source.typeName());
        target.setJdbcType(source.jdbcType());
        target.setSize(source.size());
        target.setScale(source.scale());
        target.setNullable(source.nullable());
        target.setOrdinalPosition(source.ordinalPosition());
        target.setPrimaryKey(source.primaryKey());
        target.setPrimaryKeyPosition(source.primaryKeyPosition());
        target.setRemarks(source.remarks());
        return target;
    }

    private DataSourceBatchConnectTestResultVO toBatchConnectTestResult(String dataSourceId, boolean connected) {
        DataSourceBatchConnectTestResultVO result = new DataSourceBatchConnectTestResultVO();
        result.setDataSourceId(dataSourceId);
        result.setConnected(connected);
        return result;
    }

    private DataSourceVO toDataSourceVO(DataSourceEntity source, boolean includeOriginalJson) {
        DataSourceVO target =
                BeanCopyUtils.copy(source, DataSourceVO.class, "jdbcUrl", "environment", "connStatus", "originalJson");
        if (target == null) {
            return null;
        }
        DataSourcePlugin provider = plugin(source.getDbType());
        target.setJdbcUrl(credentials.maskText(provider.descriptor(), source.getJdbcUrl()));
        target.setDisplayJdbcUrl(target.getJdbcUrl());
        if (StringUtils.hasText(source.getConnectionParams())) {
            DataSourceConnection connection = parseConnection(provider, source.getConnectionParams());
            target.setDatabase(connection.database());
            target.setSchema(connection.schema());
            target.setDisplayJdbcUrl(credentials.maskText(provider.descriptor(), provider.displayJdbcUrl(connection)));
        }
        target.setEnvironment(
                source.getEnvironment() == null ? null : source.getEnvironment().name());
        target.setEnvironmentName(
                source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
        target.setConnStatus(
                source.getConnStatus() == null ? null : source.getConnStatus().name());
        if (includeOriginalJson) {
            target.setOriginalJson(credentials.maskForDisplay(provider.descriptor(), source.getOriginalJson()));
        }
        return target;
    }
}
