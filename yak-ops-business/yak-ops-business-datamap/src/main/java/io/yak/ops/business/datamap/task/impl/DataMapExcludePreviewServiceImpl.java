package io.yak.ops.business.datamap.task.impl;

import io.yak.ops.business.datamap.config.DataMapProperties;
import io.yak.ops.business.datamap.support.DataMapScope;
import io.yak.ops.business.datamap.task.DataMapExcludePreviewService;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlExcludePreviewDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlExcludePreviewVO;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default exclude preview.
 *
 * <p>Each supported engine receives a parameter-bound metadata query whose regular expression is
 * evaluated server-side, so a namespace of ten thousand tables costs one indexed metadata scan
 * instead of ten thousand application-side matches. The Java compilation is only a syntax gate: the
 * engine's own dialect decides what the expression means.
 */
@Service
public class DataMapExcludePreviewServiceImpl implements DataMapExcludePreviewService {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapExcludePreviewServiceImpl.class);

    private static final int PREVIEW_LIMIT = 200;
    private static final String MYSQL = "MYSQL";
    private static final String POSTGRESQL = "POSTGRE_SQL";
    private static final String ORACLE = "ORACLE";

    private final DataSourceLookupService dataSourceLookup;

    private final DataMapProperties properties;

    public DataMapExcludePreviewServiceImpl(DataSourceLookupService dataSourceLookup, DataMapProperties properties) {
        this.dataSourceLookup = dataSourceLookup;
        this.properties = properties;
    }

    @Override
    public DataMapCrawlExcludePreviewVO preview(String workspaceId, DataMapCrawlExcludePreviewDTO input) {
        String regex = StringUtils.trimToNull(input.getRegex());
        if (regex == null) throw new BusinessException(DataMapErrorCode.CRAWL_PATTERN_INVALID);
        try {
            Pattern.compile(regex);
        } catch (RuntimeException invalid) {
            throw new BusinessException(DataMapErrorCode.CRAWL_PATTERN_INVALID);
        }
        String datasourceId = StringUtils.trimToNull(input.getDatasourceId());
        DataSourceInfo datasource;
        try {
            datasource = dataSourceLookup.dataSource(workspaceId, datasourceId);
        } catch (RuntimeException failure) {
            throw new BusinessException(DataMapErrorCode.CRAWL_SCOPE_INVALID);
        }
        String statement = statement(datasource.type());
        if (statement == null) throw new BusinessException(DataMapErrorCode.CRAWL_PREVIEW_UNSUPPORTED);
        DataMapScope scope = DataMapScope.resolve(input.getDatabase(), input.getSchema(), datasource);

        List<String> tables = new ArrayList<>();
        boolean truncated = false;
        try {
            JdbcConnectionOptions connection =
                    new JdbcConnectionOptions(dataSourceLookup.connectionOptions(workspaceId, datasourceId));
            try (Connection physical = new DefaultJdbcConnectionProvider(connection).getConnection();
                    PreparedStatement query = physical.prepareStatement(statement)) {
                query.setQueryTimeout(properties.getPreview().getTimeoutSeconds());
                query.setMaxRows(PREVIEW_LIMIT + 1);
                bind(query, datasource.type(), scope, regex);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        if (tables.size() == PREVIEW_LIMIT) {
                            truncated = true;
                            break;
                        }
                        tables.add(rows.getString(1));
                    }
                }
            }
        } catch (SQLException failure) {
            LOG.warn("data-map exclude preview failed datasource={}", datasourceId, failure);
            throw new BusinessException(DataMapErrorCode.CRAWL_PREVIEW_FAILED, failure);
        }
        DataMapCrawlExcludePreviewVO preview = new DataMapCrawlExcludePreviewVO();
        preview.setMatchedCount(tables.size());
        preview.setTables(tables);
        preview.setTruncated(truncated);
        return preview;
    }

    private static void bind(PreparedStatement query, String type, DataMapScope scope, String regex)
            throws SQLException {
        switch (type) {
            case MYSQL -> {
                query.setString(1, scope.databaseName());
                query.setString(2, regex);
            }
            case POSTGRESQL -> {
                query.setString(1, scope.databaseName());
                query.setString(2, emptyToNull(scope.schemaName()));
                query.setString(3, regex);
            }
            case ORACLE -> {
                query.setString(1, emptyToNull(scope.schemaName()));
                query.setString(2, regex);
            }
            default -> throw new BusinessException(DataMapErrorCode.CRAWL_PREVIEW_UNSUPPORTED);
        }
    }

    private static String statement(String type) {
        return switch (type) {
            case MYSQL ->
                "SELECT TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA=? AND TABLE_TYPE IN ('BASE TABLE','VIEW') AND TABLE_NAME REGEXP ? "
                        + "ORDER BY TABLE_NAME LIMIT " + (PREVIEW_LIMIT + 1);
            case POSTGRESQL ->
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_catalog=? AND table_schema=COALESCE(?, current_schema()) "
                        + "AND table_type IN ('BASE TABLE','VIEW') AND table_name ~ ? "
                        + "ORDER BY table_name LIMIT " + (PREVIEW_LIMIT + 1);
            case ORACLE ->
                "SELECT object_name FROM all_objects "
                        + "WHERE owner=COALESCE(?, SYS_CONTEXT('USERENV','CURRENT_SCHEMA')) "
                        + "AND object_type IN ('TABLE','VIEW') AND REGEXP_LIKE(object_name, ?) "
                        + "ORDER BY object_name FETCH FIRST " + (PREVIEW_LIMIT + 1) + " ROWS ONLY";
            default -> null;
        };
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
