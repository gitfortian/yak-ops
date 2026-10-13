package io.yak.ops.common.bean.vo.datasource;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Exposes a datasource in management lists and detail views.
 */
@Data
public class DataSourceVO {

    /** Stable datasource identifier. */
    private String id;

    /** Datasource display name. */
    private String name;

    /** Registered database provider type. */
    private String dbType;

    /** Default database bound to the connection; null when unbound. */
    private String database;

    /** Default schema bound to the connection; null when unbound. */
    private String schema;

    /** Base JDBC URL with credential-bearing values masked. */
    private String jdbcUrl;

    /** Display-only JDBC URL with permitted advanced properties and masked credentials. */
    private String displayJdbcUrl;

    /** Environment code. */
    private String environment;

    /** Human-readable environment label. */
    private String environmentName;

    /** Most recent connection status. */
    private String connStatus;

    /** Optional user-maintained datasource description. */
    private String remark;

    /** Masked JSON connection parameters used to populate the edit form. */
    private String originalJson;

    /** Creation timestamp. */
    private LocalDateTime createTime;

    /** Most recent update timestamp. */
    private LocalDateTime updateTime;
}
