package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.rule.OptionRuleMetadata;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.util.DataSourceCredentialUtils;
import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceBatchConnectTestResultVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceConnectionPropertyKeysVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionRulesVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.dao.repository.datasource.DataSourcePageQuery;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages Workspace-owned datasource definitions, search and connection status.
 *
 * <p>Datasource plugins resolve physical connections; credentials are masked before exposing configuration.
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceServiceImpl.class);

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourceProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDataSource(DataSourceDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String name = dto.getName().trim();
        DataSourcePlugin provider = plugin(dto.getDbType());
        String dbType = provider.descriptor().type();
        ensureNameAvailable(workspaceId, name, null);
        String connectionJson = JSONUtils.toJson(dto.getConnectionParams());
        DataSourceConnection connection = parseConnection(provider, connectionJson);

        DataSourceEntity entity = new DataSourceEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setDbType(dbType);
        entity.setJdbcUrl(connection.jdbcUrl());
        entity.setEnvironment(parseEnvironment(dto.getEnvironment()));
        entity.setConnStatus(DataSourceConnStatus.UNKNOWN);
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
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
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSourceEntity existing = requireEntity(workspaceId, id);
        String name = dto.getName().trim();
        DataSourcePlugin provider = plugin(dto.getDbType());
        String dbType = provider.descriptor().type();
        ensureNameAvailable(workspaceId, name, id);
        if (!existing.getDbType().equals(dbType)) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "编辑数据源时不允许修改数据源类型");
        }

        String submitted = JSONUtils.toJson(dto.getConnectionParams());
        String json = DataSourceCredentialUtils.retainOnUpdate(
                provider.descriptor(), submitted, existing.getConnectionParams());
        DataSourceConnection connection = parseConnection(provider, json);
        existing.setName(name);
        existing.setJdbcUrl(connection.jdbcUrl());
        existing.setEnvironment(parseEnvironment(dto.getEnvironment()));
        existing.setConnStatus(DataSourceConnStatus.UNKNOWN);
        existing.setRemark(StringUtils.trimToNull(dto.getRemark()));
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
        return toDataSourceVO(requireEntity(WorkspaceContext.requireWorkspaceId(), id), true);
    }

    @Override
    public DataSourceConnectionPropertyKeysVO queryConnectionPropertyKeys(String dbType) {
        DataSourceConnectionPropertyKeysVO result = new DataSourceConnectionPropertyKeysVO();
        result.setAcceptedPropertyKeys(plugin(dbType).connectionPropertyKeys());
        return result;
    }

    @Override
    public DataSourceOptionRulesVO queryOptionRules(String dbType) {
        DataSourcePlugin provider = plugin(dbType);
        var options = OptionRuleMetadata.describe(provider.optionRule()).stream()
                .map(option -> new DataSourceOptionRulesVO.OptionDefinitionVO(
                        option.key(),
                        option.type(),
                        option.required(),
                        option.defaultValue(),
                        option.allowedValues(),
                        option.description()))
                .toList();
        return new DataSourceOptionRulesVO(provider.descriptor().type(), options);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDataSource(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
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
        String workspaceId = WorkspaceContext.requireWorkspaceId();
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
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        if (!dto.getSorts().isEmpty()) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源分页暂不支持自定义排序");
        }
        String dbType = null;
        if (StringUtils.isNotBlank(dto.getDbType())) {
            dbType = plugin(dto.getDbType()).descriptor().type();
        }
        DataSourceEnvironment environment = null;
        if (StringUtils.isNotBlank(dto.getEnvironment())) {
            environment = parseEnvironment(dto.getEnvironment());
        }
        DataSourceConnStatus status = null;
        if (StringUtils.isNotBlank(dto.getConnStatus())) {
            status = parseConnectionStatus(dto.getConnStatus());
        }
        DataSourcePageQuery query = new DataSourcePageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getName()),
                StringUtils.trimToNull(dto.getKeyword()),
                dbType,
                environment,
                status);
        return PagingData.from(repository.queryPage(workspaceId, query).map(value -> toDataSourceVO(value, false)));
    }

    @Override
    public boolean testConnection(String id) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return testSavedConnection(workspaceId, requireEntity(workspaceId, id));
    }

    @Override
    public List<DataSourceBatchConnectTestResultVO> batchTestConnections(DataSourceBatchIdsDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
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
            DataSourceBatchConnectTestResultVO result = new DataSourceBatchConnectTestResultVO();
            result.setDataSourceId(entity.getId());
            result.setConnected(connected);
            results.add(result);
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
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSourceEntity existing = null;
        if (dto.getDataSourceId() != null) {
            existing = requireEntity(workspaceId, dto.getDataSourceId());
        }
        DataSourcePlugin provider = plugin(dto.getDbType());
        String dbType = provider.descriptor().type();
        String connectionJson = JSONUtils.toJson(dto.getConnectionParams());

        if (existing != null) {
            if (!dbType.equals(existing.getDbType())) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接测试的数据源类型与已保存数据源不一致");
            }
            connectionJson = DataSourceCredentialUtils.retainOnUpdate(
                    provider.descriptor(), connectionJson, existing.getConnectionParams());
        }

        try {
            testPluginConnection(provider, connectionJson, Math.max(1, properties.getConnectionTestTimeoutSeconds()));
            return true;
        } catch (RuntimeException exception) {
            throw connectException(exception);
        }
    }

    private DataSourceEntity requireEntity(String workspaceId, String id) {
        return repository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
    }

    private List<DataSourceEntity> requireBatchEntities(String workspaceId, DataSourceBatchIdsDTO dto) {
        return dto.getIds().stream()
                .map(String::trim)
                .distinct()
                .map(id -> requireEntity(workspaceId, id))
                .toList();
    }

    private boolean testSavedConnection(String workspaceId, DataSourceEntity entity) {
        try {
            testPluginConnection(
                    plugin(entity.getDbType()),
                    entity.getConnectionParams(),
                    Math.max(1, properties.getConnectionTestTimeoutSeconds()));
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
            return DataSourcePluginRegistry.get(type);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new DataSourceException(DataSourceErrorCode.PLUGIN_NOT_FOUND, "未找到插件：" + type, exception);
        }
    }

    private DataSourceConnection parseConnection(DataSourcePlugin provider, String json) {
        try {
            return provider.parseConnection(ConfigParser.parseJson(json));
        } catch (RuntimeException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, null, exception);
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
            throw new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, null, exception);
        }
    }

    private DataSourceException connectException(RuntimeException exception) {
        if (exception instanceof DataSourceException dataSourceException) {
            return dataSourceException;
        }
        return new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, null, exception);
    }

    private DataSourceVO toDataSourceVO(DataSourceEntity source, boolean includeOriginalJson) {
        DataSourceVO target =
                BeanCopyUtils.copy(source, DataSourceVO.class, "jdbcUrl", "environment", "connStatus", "originalJson");
        DataSourcePlugin provider = plugin(source.getDbType());
        target.setJdbcUrl(DataSourceCredentialUtils.maskText(provider.descriptor(), source.getJdbcUrl()));
        target.setDisplayJdbcUrl(target.getJdbcUrl());
        if (StringUtils.isNotBlank(source.getConnectionParams())) {
            DataSourceConnection connection = parseConnection(provider, source.getConnectionParams());
            target.setDatabase(connection.database());
            target.setSchema(connection.schema());
            String displayUrl = provider.displayJdbcUrl(connection);
            target.setDisplayJdbcUrl(DataSourceCredentialUtils.maskText(provider.descriptor(), displayUrl));
        }
        if (source.getEnvironment() != null) {
            target.setEnvironment(source.getEnvironment().name());
            target.setEnvironmentName(source.getEnvironment().getDisplayName());
        }
        if (source.getConnStatus() != null) {
            target.setConnStatus(source.getConnStatus().name());
        }
        if (includeOriginalJson) {
            target.setOriginalJson(
                    DataSourceCredentialUtils.maskForDisplay(provider.descriptor(), source.getOriginalJson()));
        }
        return target;
    }
}
