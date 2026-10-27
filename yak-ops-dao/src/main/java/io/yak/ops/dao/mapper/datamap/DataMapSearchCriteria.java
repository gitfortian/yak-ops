package io.yak.ops.dao.mapper.datamap;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Bound parameters of the catalog search.
 *
 * <p>The keyword is already LIKE-escaped by the service, so nothing here is interpolated into SQL.
 */
@Getter
@Setter
public class DataMapSearchCriteria {
    /** Workspace whose assets are queried. */
    private String workspaceId;
    /** LIKE pattern including surrounding wildcards, or null when no keyword was given. */
    private String keywordPattern;
    /** Optional datasource filter. */
    private String datasourceId;
    /** Optional catalog-slot filter. */
    private String databaseName;
    /** Optional schema-slot filter. */
    private String schemaName;
    /** Optional data domain filter. */
    private String domainId;
    /** When true, only assets without a data domain are returned. */
    private boolean unclassified;
    /** Optional tag filter. */
    private String tagId;
    /** When true, MISSING assets are included. */
    private boolean includeMissing;
    /** Optional closed set of asset identities; used by the hot list. */
    private List<String> ids;
    /** Maximum rows to return. */
    private int limit;
    /** Zero-based row offset. */
    private int offset;
}
