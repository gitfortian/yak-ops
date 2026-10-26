package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.types.TableSchema;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Maps JDBC source metadata to canonical YakFlow logical types.
 *
 * <p>Implementations must not read row values or generate target DDL. Vendor-specific
 * SQL types are interpreted by the source dialect; runtime conversion remains a separate concern.
 */
@FunctionalInterface
public interface JdbcTypeConverter {

    TableSchema convert(ResultSetMetaData metadata) throws SQLException;
}
