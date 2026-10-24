package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.MetadataCollectionEntity;
import io.yak.ops.dao.entity.modeling.MetadataFieldEntity;
import io.yak.ops.dao.entity.modeling.MetadataTableEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of one-table metadata snapshots. */
public interface MetadataRepository {
    /**
     * Inserts one collection run.
     * @param row fully populated run, already carrying its generated identity
     */
    void addCollection(MetadataCollectionEntity row);

    /**
     * Inserts one observed table.
     * @param row fully populated row, already carrying its generated identity
     */
    void addTable(MetadataTableEntity row);

    /**
     * Inserts one observed column.
     * @param row fully populated row, already carrying its generated identity
     */
    void addField(MetadataFieldEntity row);

    /**
     * Reads the newest run of one publication.
     * @param workspaceId authorized Workspace
     * @param publicationId publication identity
     * @return the newest run, or empty when the publication has no snapshot
     */
    Optional<MetadataCollectionEntity> latestCollection(String workspaceId, String publicationId);

    /**
     * Lists the observed tables of one run.
     * @param workspaceId authorized Workspace
     * @param collectionId run identity
     * @return observed tables ordered by name
     */
    List<MetadataTableEntity> tables(String workspaceId, String collectionId);

    /**
     * Lists the observed columns of one table.
     * @param workspaceId authorized Workspace
     * @param tableId observed table identity
     * @return observed columns in ordinal order
     */
    List<MetadataFieldEntity> fields(String workspaceId, String tableId);

    /**
     * Removes every run of one publication and its children.
     * @param workspaceId authorized Workspace
     * @param publicationId publication identity
     */
    void deleteByPublication(String workspaceId, String publicationId);
}
