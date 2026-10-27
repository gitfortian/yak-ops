package io.yak.ops.business.datamap.crawl;

import io.yak.ops.business.datamap.config.DataMapProperties;
import io.yak.ops.business.datamap.support.DataMapScope;
import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.enums.datamap.DataMapTableChangeType;
import io.yak.ops.common.enums.datamap.DataMapTableStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.datamap.DataMapColumnEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlFailureEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableChangeEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.mapper.datamap.DataMapColumnMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableChangeMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes one claimable crawl run.
 *
 * <p>The run is a row, not a thread: the worker claims it with a conditional update, binds the
 * run's Workspace for the Catalog seam, and writes each table in its own transaction so a partial
 * run is visible and a re-run converges. Observed columns are written only through the crawler's
 * statements, which cannot reach a curated field.
 */
@Component
public class DataMapCrawlWorker {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapCrawlWorker.class);

    private static final int TABLE_CEILING = 2_000;
    private static final int ERROR_LENGTH = 1_000;

    private final DataMapCrawlMapper crawlMapper;

    private final DataMapTableMapper tableMapper;

    private final DataMapColumnMapper columnMapper;

    private final DataMapTableChangeMapper changeMapper;

    private final DataSourceCatalogService catalogService;

    private final TransactionTemplate transactions;

    private final DataMapProperties properties;

    public DataMapCrawlWorker(
            DataMapCrawlMapper crawlMapper,
            DataMapTableMapper tableMapper,
            DataMapColumnMapper columnMapper,
            DataMapTableChangeMapper changeMapper,
            DataSourceCatalogService catalogService,
            PlatformTransactionManager transactionManager,
            DataMapProperties properties) {
        this.crawlMapper = crawlMapper;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.changeMapper = changeMapper;
        this.catalogService = catalogService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    /**
     * Claims and executes one run.
     *
     * <p>A zero-row claim means another worker owns the row, and this call returns without work.
     *
     * @param crawlId run identity
     */
    public void run(String crawlId) {
        DataMapCrawlEntity run = crawlMapper.findById(crawlId);
        if (run == null) {
            LOG.warn("data-map crawl run disappeared before it was claimed crawl={}", crawlId);
            return;
        }
        WorkspaceContext.bind(run.getWorkspaceId());
        try {
            LocalDateTime started = DateUtils.now();
            if (crawlMapper.claim(crawlId, started) == 0) return;
            run.setStartedTime(started);
            execute(run);
        } catch (RuntimeException failure) {
            LOG.error("data-map crawl run failed crawl={}", crawlId, failure);
            crawlMapper.finishFailed(crawlId, message(failure), 0, 0, 0, 0, 0, DateUtils.now());
        } finally {
            WorkspaceContext.clear();
        }
    }

    private void execute(DataMapCrawlEntity run) {
        DataMapScope scope = new DataMapScope(run.getDatabaseName(), run.getSchemaName());
        List<DataSourceCatalogTableVO> tables;
        try {
            tables = catalogService.listAllTables(
                    run.getDatasourceId(), emptyToNull(scope.databaseName()), emptyToNull(scope.schemaName()));
        } catch (RuntimeException failure) {
            failRun(run, failure);
            return;
        }
        if (tables.size() >= TABLE_CEILING) {
            crawlMapper.finishFailed(
                    run.getId(),
                    DataMapErrorCode.CRAWL_TOO_LARGE.getMessage(),
                    tables.size(),
                    0,
                    0,
                    0,
                    0,
                    DateUtils.now());
            return;
        }

        LocalDateTime deadline = run.getStartedTime().plus(properties.getCrawl().getBudget());
        int columnCount = 0;
        int added = 0;
        int updated = 0;
        int failed = 0;
        for (DataSourceCatalogTableVO table : tables) {
            if (DateUtils.now().isAfter(deadline)) {
                crawlMapper.finishFailed(
                        run.getId(),
                        "数据采集超过 30 分钟预算，已终止",
                        tables.size(),
                        columnCount,
                        added,
                        updated,
                        failed,
                        DateUtils.now());
                return;
            }
            try {
                List<DataSourceCatalogColumnVO> columns =
                        catalogService.listColumns(run.getDatasourceId(), path(scope, table));
                boolean created = persist(run, scope, table, columns);
                columnCount += columns.size();
                if (created) added++;
                else updated++;
            } catch (RuntimeException failure) {
                failed++;
                recordFailure(run, table.getName(), failure);
            }
        }
        int removed = failed == 0 ? markMissing(run, scope) : 0;
        crawlMapper.finishSucceeded(
                run.getId(), tables.size(), columnCount, added, updated, removed, failed, DateUtils.now());
    }

    private boolean persist(
            DataMapCrawlEntity run,
            DataMapScope scope,
            DataSourceCatalogTableVO table,
            List<DataSourceCatalogColumnVO> columns) {
        Boolean created = transactions.execute(status -> {
            DataMapTableEntity existing = tableMapper.findByIdentity(
                    run.getWorkspaceId(),
                    run.getDatasourceId(),
                    scope.databaseName(),
                    scope.schemaName(),
                    table.getName());
            LocalDateTime now = DateUtils.now();
            String tableId;
            boolean inserted = existing == null;
            if (inserted) {
                tableId = IdUtils.nextId();
                DataMapTableEntity row = new DataMapTableEntity();
                row.setId(tableId);
                row.setWorkspaceId(run.getWorkspaceId());
                row.setDatasourceId(run.getDatasourceId());
                row.setDatabaseName(scope.databaseName());
                row.setSchemaName(scope.schemaName());
                row.setName(table.getName());
                row.setTableType(table.getType());
                row.setRemarks(table.getRemarks());
                row.setStatus(DataMapTableStatus.ACTIVE.name());
                row.setFirstSeenTime(run.getStartedTime());
                row.setLastSeenTime(run.getStartedTime());
                row.setCreateTime(now);
                row.setUpdateTime(now);
                row.setCreateBy(run.getCreateBy());
                row.setUpdateBy(run.getCreateBy());
                tableMapper.insert(row);
            } else {
                tableId = existing.getId();
                tableMapper.updateObserved(
                        run.getWorkspaceId(),
                        tableId,
                        table.getType(),
                        table.getRemarks(),
                        DataMapTableStatus.ACTIVE.name(),
                        run.getStartedTime(),
                        now);
            }

            List<DataMapColumnEntity> baseline = columnMapper.listByTable(run.getWorkspaceId(), tableId);
            if (!inserted) {
                for (DataMapChangeDetector.DetectedChange change :
                        DataMapChangeDetector.detect(existing, baseline, table, columns)) {
                    record(existing, change.type(), change.detail(), now);
                }
            }
            columnMapper.deleteByTable(run.getWorkspaceId(), tableId);
            for (DataSourceCatalogColumnVO column : columns) columnMapper.insert(toEntity(run, tableId, column));
            return inserted;
        });
        return Boolean.TRUE.equals(created);
    }

    private void record(DataMapTableEntity table, DataMapTableChangeType type, String detail, LocalDateTime now) {
        DataMapTableChangeEntity change = new DataMapTableChangeEntity();
        change.setId(IdUtils.nextId());
        change.setWorkspaceId(table.getWorkspaceId());
        change.setTableId(table.getId());
        change.setChangeType(type.name());
        change.setDetail(truncate(detail, ERROR_LENGTH));
        change.setCreatedTime(now);
        changeMapper.insert(change);
    }

    private int markMissing(DataMapCrawlEntity run, DataMapScope scope) {
        Integer removed = transactions.execute(status -> tableMapper.markMissing(
                run.getWorkspaceId(),
                run.getDatasourceId(),
                scope.databaseName(),
                scope.schemaName(),
                run.getStartedTime(),
                DateUtils.now()));
        return removed == null ? 0 : removed;
    }

    private void recordFailure(DataMapCrawlEntity run, String tableName, RuntimeException failure) {
        DataMapCrawlFailureEntity row = new DataMapCrawlFailureEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(run.getWorkspaceId());
        row.setCrawlId(run.getId());
        row.setTableName(truncate(tableName, 128));
        row.setError(message(failure));
        row.setCreatedTime(DateUtils.now());
        crawlMapper.insertFailure(row);
        LOG.warn(
                "data-map crawl table failed crawl={} datasource={} table={}",
                run.getId(),
                run.getDatasourceId(),
                tableName,
                failure);
    }

    private void failRun(DataMapCrawlEntity run, RuntimeException failure) {
        String error = message(failure);
        LOG.warn(
                "data-map crawl enumeration failed crawl={} datasource={}",
                run.getId(),
                run.getDatasourceId(),
                failure);
        crawlMapper.finishFailed(run.getId(), error, 0, 0, 0, 0, 0, DateUtils.now());
    }

    private static DataSourceTablePathDTO path(DataMapScope scope, DataSourceCatalogTableVO table) {
        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(firstNonEmpty(table.getDatabase(), scope.databaseName()));
        path.setSchema(firstNonEmpty(table.getSchema(), scope.schemaName()));
        path.setTable(table.getName());
        return path;
    }

    private static DataMapColumnEntity toEntity(
            DataMapCrawlEntity run, String tableId, DataSourceCatalogColumnVO column) {
        DataMapColumnEntity row = new DataMapColumnEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(run.getWorkspaceId());
        row.setTableId(tableId);
        row.setOrdinal(column.getOrdinalPosition());
        row.setName(column.getName());
        row.setTypeName(column.getTypeName());
        row.setJdbcType(column.getJdbcType());
        row.setSize(column.getSize());
        row.setScale(column.getScale());
        row.setNullable(column.getNullable());
        row.setPrimaryKey(column.getPrimaryKey());
        row.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
        row.setRemarks(column.getRemarks());
        return row;
    }

    private static String message(RuntimeException failure) {
        String message = failure.getMessage();
        return truncate(
                message == null || message.isBlank() ? failure.getClass().getSimpleName() : message, ERROR_LENGTH);
    }

    private static String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static String firstNonEmpty(String first, String fallback) {
        return first == null || first.isEmpty() ? fallback : first;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
