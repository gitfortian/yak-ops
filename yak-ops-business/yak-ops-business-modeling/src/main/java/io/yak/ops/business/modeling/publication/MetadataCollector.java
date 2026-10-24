package io.yak.ops.business.modeling.publication;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.modeling.MetadataFieldVO;
import io.yak.ops.common.bean.vo.modeling.MetadataSnapshotVO;
import io.yak.ops.common.bean.vo.modeling.MetadataTableVO;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.MetadataCollectionEntity;
import io.yak.ops.dao.entity.modeling.MetadataFieldEntity;
import io.yak.ops.dao.entity.modeling.MetadataTableEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.repository.modeling.MetadataRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and persists the physical snapshot of one published table.
 *
 * <p>Collection is one table, never a crawl: the catalog table listing is bounded and has no stable
 * cursor, so a whole-source crawler would need pagination, run identity and failure semantics of its
 * own. That is deliberately a later proposal.
 *
 * <p>A successful run replaces the previous snapshot in the same transaction, so a reader always sees
 * either the old complete snapshot or the new one. A failed run is recorded separately and leaves the
 * previous snapshot intact.
 */
@Component
public class MetadataCollector {

    /** Marks a run that finished with a snapshot. */
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** Marks a run that produced no snapshot. */
    private static final String STATUS_FAILED = "FAILED";

    /** Same bound the catalog listing accepts; the published table is matched exactly after it. */
    private static final int TABLE_LOOKUP_LIMIT = 200;

    @Resource
    private MetadataRepository snapshots;

    @Resource
    private DataSourceCatalogService catalog;

    /**
     * Collects the published table and replaces the snapshot of its publication.
     *
     * @param workspaceId authorized Workspace
     * @param actor authenticated user
     * @param publication publication whose table is collected
     * @return the persisted snapshot
     * @throws IllegalStateException when the table or its columns cannot be read back
     */
    @Transactional
    public MetadataSnapshotVO collect(String workspaceId, String actor, ModelPublicationEntity publication) {
        var columns = readColumns(publication);
        var tableInfo = readTable(publication);
        snapshots.deleteByPublication(workspaceId, publication.getId());
        var now = DateUtils.now();
        var collection = new MetadataCollectionEntity();
        collection.setId(IdUtils.nextId());
        collection.setWorkspaceId(workspaceId);
        collection.setPublicationId(publication.getId());
        collection.setStatus(STATUS_SUCCEEDED);
        collection.setTableCount(1);
        collection.setFieldCount(columns.size());
        collection.setCreatedTime(now);
        collection.setFinishedTime(now);
        collection.setCreateBy(actor);
        snapshots.addCollection(collection);
        var table = new MetadataTableEntity();
        table.setId(IdUtils.nextId());
        table.setWorkspaceId(workspaceId);
        table.setCollectionId(collection.getId());
        table.setDatasourceId(publication.getDatasourceId());
        table.setDatabaseName(publication.getPhysicalDatabase());
        table.setSchemaName(publication.getPhysicalSchema());
        table.setName(publication.getPhysicalTable());
        table.setType(tableInfo == null ? null : tableInfo.getType());
        table.setRemarks(tableInfo == null ? null : tableInfo.getRemarks());
        snapshots.addTable(table);
        var fields = new ArrayList<MetadataFieldVO>(columns.size());
        for (var column : columns) {
            var row = new MetadataFieldEntity();
            row.setId(IdUtils.nextId());
            row.setWorkspaceId(workspaceId);
            row.setTableId(table.getId());
            row.setOrdinal(column.getOrdinalPosition() == null ? fields.size() + 1 : column.getOrdinalPosition());
            row.setName(column.getName());
            row.setTypeName(column.getTypeName());
            row.setJdbcType(column.getJdbcType() == null ? 0 : column.getJdbcType());
            row.setSize(column.getSize());
            row.setScale(column.getScale());
            row.setNullable(column.getNullable());
            row.setPrimaryKey(column.getPrimaryKey());
            row.setPrimaryKeyPosition(column.getPrimaryKeyPosition());
            row.setRemarks(column.getRemarks());
            snapshots.addField(row);
            fields.add(new MetadataFieldVO(
                    row.getOrdinal(),
                    row.getName(),
                    row.getTypeName(),
                    row.getJdbcType(),
                    row.getSize(),
                    row.getScale(),
                    Boolean.TRUE.equals(row.getNullable()),
                    Boolean.TRUE.equals(row.getPrimaryKey()),
                    row.getPrimaryKeyPosition(),
                    row.getRemarks()));
        }
        return new MetadataSnapshotVO(
                collection.getId(),
                publication.getId(),
                collection.getStatus(),
                1,
                fields.size(),
                null,
                now,
                now,
                List.of(new MetadataTableVO(
                        table.getDatabaseName(),
                        table.getSchemaName(),
                        table.getName(),
                        table.getType(),
                        table.getRemarks(),
                        List.copyOf(fields))));
    }

    /**
     * Records a failed collection without touching the previous snapshot.
     *
     * <p>Called outside the collection transaction, so the failure is visible even though the
     * attempted run rolled back.
     *
     * @param workspaceId authorized Workspace
     * @param actor authenticated user
     * @param publicationId publication whose collection failed
     * @param error bounded failure detail
     */
    @Transactional
    public void recordFailure(String workspaceId, String actor, String publicationId, String error) {
        var now = DateUtils.now();
        var collection = new MetadataCollectionEntity();
        collection.setId(IdUtils.nextId());
        collection.setWorkspaceId(workspaceId);
        collection.setPublicationId(publicationId);
        collection.setStatus(STATUS_FAILED);
        collection.setTableCount(0);
        collection.setFieldCount(0);
        collection.setError(error);
        collection.setCreatedTime(now);
        collection.setFinishedTime(now);
        collection.setCreateBy(actor);
        snapshots.addCollection(collection);
    }

    /**
     * Reads the newest snapshot of one publication.
     *
     * @param workspaceId authorized Workspace
     * @param publicationId publication identity
     * @return the snapshot, or empty when the publication has no collection run
     */
    public Optional<MetadataSnapshotVO> snapshot(String workspaceId, String publicationId) {
        return snapshots.latestCollection(workspaceId, publicationId).map(run -> {
            var tables = new ArrayList<MetadataTableVO>();
            for (var table : snapshots.tables(workspaceId, run.getId())) {
                var columns = snapshots.fields(workspaceId, table.getId()).stream()
                        .map(row -> new MetadataFieldVO(
                                row.getOrdinal(),
                                row.getName(),
                                row.getTypeName(),
                                row.getJdbcType(),
                                row.getSize(),
                                row.getScale(),
                                Boolean.TRUE.equals(row.getNullable()),
                                Boolean.TRUE.equals(row.getPrimaryKey()),
                                row.getPrimaryKeyPosition(),
                                row.getRemarks()))
                        .toList();
                tables.add(new MetadataTableVO(
                        table.getDatabaseName(),
                        table.getSchemaName(),
                        table.getName(),
                        table.getType(),
                        table.getRemarks(),
                        columns));
            }
            return new MetadataSnapshotVO(
                    run.getId(),
                    run.getPublicationId(),
                    run.getStatus(),
                    run.getTableCount(),
                    run.getFieldCount(),
                    run.getError(),
                    run.getCreatedTime(),
                    run.getFinishedTime(),
                    List.copyOf(tables));
        });
    }

    private List<DataSourceCatalogColumnVO> readColumns(ModelPublicationEntity publication) {
        var path = new DataSourceTablePathDTO();
        path.setDatabase(publication.getPhysicalDatabase());
        path.setSchema(publication.getPhysicalSchema());
        path.setTable(publication.getPhysicalTable());
        var columns = catalog.listColumns(publication.getDatasourceId(), path);
        if (columns.isEmpty()) {
            throw new IllegalStateException("物理表未返回任何字段，无法生成元数据快照");
        }
        return columns;
    }

    private DataSourceCatalogTableVO readTable(ModelPublicationEntity publication) {
        var query = new DataSourceCatalogQueryDTO();
        query.setDatabase(publication.getPhysicalDatabase());
        query.setSchema(publication.getPhysicalSchema());
        query.setKeyword(publication.getPhysicalTable());
        query.setLimit(TABLE_LOOKUP_LIMIT);
        var wanted = publication.getPhysicalTable().trim().toLowerCase(Locale.ROOT);
        return catalog.listTables(publication.getDatasourceId(), query).stream()
                .filter(table -> table.getName() != null
                        && table.getName().trim().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst()
                .orElse(null);
    }
}
