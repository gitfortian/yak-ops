package io.yak.ops.business.datamap.preview.impl;

import io.yak.ops.business.datamap.config.DataMapProperties;
import io.yak.ops.business.datamap.preview.DataMapPreviewService;
import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.vo.datamap.DataMapPreviewVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.enums.datamap.DataMapPreviewTruncation;
import io.yak.ops.common.enums.datamap.DataMapTableStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default bounded preview reader.
 *
 * <p>Three bounds apply: the statement never returns more than one hundred rows, each text cell is
 * read from its character stream up to a fixed character count, and the response stops at one
 * megabyte of accumulated text. Binary and large-object columns are reported with a placeholder
 * and never materialised. A fourth bound is admission: at most four queries run at once, and the
 * fifth caller is refused immediately rather than queued behind a five-second statement.
 */
@Service
public class DataMapPreviewServiceImpl implements DataMapPreviewService {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapPreviewServiceImpl.class);

    private static final int ROW_LIMIT = 100;
    private static final int FETCH_SIZE = 100;
    private static final int BYTE_BUDGET = 1_048_576;
    private static final String LARGE_VALUE_PLACEHOLDER = "（大字段已省略）";
    private static final String NOTICE = "预览数据可能包含个人信息，请仅在授权范围内查看与使用";

    @Resource
    private DataMapTableMapper tableMapper;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private DataSourceLookupService dataSourceLookup;

    private final DataMapProperties properties;

    private final Semaphore permits;

    public DataMapPreviewServiceImpl(DataMapProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(properties.getPreview().getMaxConcurrentQueries());
    }

    @Override
    public DataMapPreviewVO preview(String workspaceId, String actor, String tableId) {
        DataMapTableEntity asset = tableMapper.findById(workspaceId, tableId);
        if (asset == null || DataMapTableStatus.MISSING.name().equals(asset.getStatus())) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        DataSourceVO datasource = dataSourceService.queryDataSource(asset.getDatasourceId());
        if (DataSourceEnvironment.PROD.name().equals(datasource.getEnvironment())) {
            throw new BusinessException(DataMapErrorCode.PREVIEW_FORBIDDEN);
        }
        if (!permits.tryAcquire()) throw new BusinessException(DataMapErrorCode.PREVIEW_BUSY);

        long startedAt = System.nanoTime();
        String outcome = "FAILED";
        try {
            DataMapPreviewVO preview = sample(workspaceId, asset);
            outcome = "SUCCEEDED";
            return preview;
        } catch (SQLTimeoutException timeout) {
            outcome = "TIMEOUT";
            throw new BusinessException(DataMapErrorCode.PREVIEW_TIMEOUT);
        } catch (SQLException failure) {
            throw new BusinessException("数据预览查询失败", failure);
        } finally {
            permits.release();
            LOG.info(
                    "data-map preview workspace={} user={} asset={} datasource={} outcome={} durationMs={}",
                    workspaceId,
                    actor,
                    asset.getId(),
                    asset.getDatasourceId(),
                    outcome,
                    (System.nanoTime() - startedAt) / 1_000_000L);
        }
    }

    private DataMapPreviewVO sample(String workspaceId, DataMapTableEntity asset) throws SQLException {
        JdbcConnectionOptions connection =
                new JdbcConnectionOptions(dataSourceLookup.connectionOptions(workspaceId, asset.getDatasourceId()));
        TableId tableId =
                new TableId(emptyToNull(asset.getDatabaseName()), emptyToNull(asset.getSchemaName()), asset.getName());
        JdbcDialect dialect = JdbcDialects.forUrl(connection.url());
        try (Connection physical = new DefaultJdbcConnectionProvider(connection).getConnection()) {
            dialect.configureReadConnection(physical);
            try (Statement statement = physical.createStatement()) {
                statement.setMaxRows(ROW_LIMIT);
                statement.setQueryTimeout(properties.getPreview().getTimeoutSeconds());
                statement.setFetchSize(FETCH_SIZE);
                dialect.configurePreviewStatement(statement);
                try (ResultSet resultSet = statement.executeQuery("SELECT * FROM " + dialect.qualifiedTable(tableId))) {
                    return read(resultSet);
                }
            }
        }
    }

    private DataMapPreviewVO read(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        DataMapPreviewVO preview = new DataMapPreviewVO();
        for (int index = 1; index <= columnCount; index++) {
            preview.getColumns()
                    .add(new DataMapPreviewVO.PreviewColumn(
                            metadata.getColumnLabel(index), metadata.getColumnTypeName(index)));
        }
        preview.setNotice(NOTICE);
        preview.setTruncated(DataMapPreviewTruncation.ROWS.name());

        int[] budget = {BYTE_BUDGET};
        while (resultSet.next()) {
            if (preview.getRows().size() == ROW_LIMIT) break;
            List<String> row = new ArrayList<>(columnCount);
            boolean budgetSpent = false;
            for (int index = 1; index <= columnCount && !budgetSpent; index++) {
                String value = cell(resultSet, index, metadata.getColumnType(index));
                if (value == null) {
                    row.add(null);
                    continue;
                }
                if (value.length() > budget[0]) {
                    row.add(value.substring(0, budget[0]));
                    budget[0] = 0;
                    budgetSpent = true;
                } else {
                    row.add(value);
                    budget[0] -= value.length();
                }
            }
            preview.getRows().add(row);
            if (budgetSpent) {
                preview.setTruncated(DataMapPreviewTruncation.SIZE.name());
                break;
            }
        }
        return preview;
    }

    private String cell(ResultSet resultSet, int index, int jdbcType) throws SQLException {
        if (isLargeValue(jdbcType)) return LARGE_VALUE_PLACEHOLDER;
        if (isText(jdbcType))
            return text(resultSet, index, properties.getPreview().getCellCharacters());
        return resultSet.getString(index);
    }

    private static String text(ResultSet resultSet, int index, int limit) throws SQLException {
        Reader reader = resultSet.getCharacterStream(index);
        if (reader == null) return null;
        char[] buffer = new char[limit];
        int read = 0;
        try (Reader source = reader) {
            while (read < limit) {
                int count = source.read(buffer, read, limit - read);
                if (count < 0) break;
                read += count;
            }
        } catch (IOException failure) {
            throw new SQLException("Unable to read preview text", failure);
        }
        return new String(buffer, 0, read);
    }

    private static boolean isText(int jdbcType) {
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> true;
            default -> false;
        };
    }

    private static boolean isLargeValue(int jdbcType) {
        return switch (jdbcType) {
            case Types.BINARY,
                    Types.VARBINARY,
                    Types.LONGVARBINARY,
                    Types.BLOB,
                    Types.CLOB,
                    Types.NCLOB,
                    Types.SQLXML,
                    Types.ARRAY,
                    Types.STRUCT,
                    Types.REF,
                    Types.JAVA_OBJECT,
                    Types.OTHER,
                    Types.DATALINK -> true;
            default -> false;
        };
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
