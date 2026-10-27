package io.yak.ops.business.datamap.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.enums.datamap.DataMapTableChangeType;
import io.yak.ops.dao.entity.datamap.DataMapColumnEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies change detection against the stored baseline rather than the post-replacement rows. */
class DataMapChangeDetectorTest {

    @Test
    void identicalObservationProducesNoChange() {
        assertEquals(
                List.of(),
                DataMapChangeDetector.detect(
                        asset("orders"),
                        List.of(column("amount", "VARCHAR", 50, true, false)),
                        catalogTable("orders"),
                        List.of(observed("amount", "VARCHAR", 50, true, false))));
    }

    @Test
    void widthChangeIsOneTypeChangeRatherThanAnAddedRemovedPair() {
        List<DataMapChangeDetector.DetectedChange> changes = DataMapChangeDetector.detect(
                asset("orders"),
                List.of(column("amount", "VARCHAR", 50, true, false)),
                catalogTable("orders"),
                List.of(observed("amount", "VARCHAR", 100, true, false)));
        assertEquals(1, changes.size());
        assertEquals(DataMapTableChangeType.COLUMN_TYPE_CHANGED, changes.get(0).type());
        assertTrue(changes.get(0).detail().contains("VARCHAR(50)"));
        assertTrue(changes.get(0).detail().contains("VARCHAR(100)"));
    }

    @Test
    void addedAndRemovedColumnsAreReportedSeparately() {
        List<DataMapChangeDetector.DetectedChange> changes = DataMapChangeDetector.detect(
                asset("orders"),
                List.of(column("amount", "INT", null, true, false), column("legacy", "INT", null, true, false)),
                catalogTable("orders"),
                List.of(observed("amount", "INT", null, true, false), observed("currency", "VARCHAR", 3, true, false)));
        assertEquals(
                List.of(DataMapTableChangeType.COLUMN_ADDED, DataMapTableChangeType.COLUMN_REMOVED),
                changes.stream().map(DataMapChangeDetector.DetectedChange::type).toList());
    }

    @Test
    void nullabilityAndPrimaryKeyChangesAreDetected() {
        List<DataMapChangeDetector.DetectedChange> changes = DataMapChangeDetector.detect(
                asset("orders"), List.of(column("id", "BIGINT", null, false, false)), catalogTable("orders"),
                List.of(observed("id", "BIGINT", null, true, true)));
        assertEquals(
                List.of(
                        DataMapTableChangeType.COLUMN_NULLABILITY_CHANGED,
                        DataMapTableChangeType.PRIMARY_KEY_CHANGED),
                changes.stream().map(DataMapChangeDetector.DetectedChange::type).toList());
    }

    @Test
    void tableCommentChangeIsDetected() {
        List<DataMapChangeDetector.DetectedChange> changes =
                DataMapChangeDetector.detect(asset("old comment"), List.of(), catalogTable("new comment"), List.of());
        assertEquals(1, changes.size());
        assertEquals(DataMapTableChangeType.TABLE_COMMENT_CHANGED, changes.get(0).type());
    }

    private static DataMapTableEntity asset(String remarks) {
        DataMapTableEntity row = new DataMapTableEntity();
        row.setId("t1");
        row.setWorkspaceId("ws");
        row.setRemarks(remarks);
        return row;
    }

    private static DataMapColumnEntity column(
            String name, String type, Integer size, boolean nullable, boolean primaryKey) {
        DataMapColumnEntity row = new DataMapColumnEntity();
        row.setName(name);
        row.setTypeName(type);
        row.setSize(size);
        row.setNullable(nullable);
        row.setPrimaryKey(primaryKey);
        row.setPrimaryKeyPosition(primaryKey ? 1 : null);
        return row;
    }

    private static DataSourceCatalogColumnVO observed(
            String name, String type, Integer size, boolean nullable, boolean primaryKey) {
        DataSourceCatalogColumnVO row = new DataSourceCatalogColumnVO();
        row.setName(name);
        row.setTypeName(type);
        row.setSize(size);
        row.setNullable(nullable);
        row.setPrimaryKey(primaryKey);
        row.setPrimaryKeyPosition(primaryKey ? 1 : null);
        row.setOrdinalPosition(1);
        return row;
    }

    private static DataSourceCatalogTableVO catalogTable(String remarks) {
        DataSourceCatalogTableVO row = new DataSourceCatalogTableVO();
        row.setName("orders");
        row.setType("TABLE");
        row.setRemarks(remarks);
        return row;
    }
}
