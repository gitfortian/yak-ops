package io.yak.ops.connector.jdbc.database.internal.convert;

import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/** Oracle DATE semantics and exact NUMBER metadata. */
public final class OracleTypeConverter extends StandardJdbcTypeConverter {

    @Override
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        if (metadata.getColumnType(index) == Types.DATE) {
            return LogicalTypes.timestamp(0);
        }
        return super.resolveType(metadata, index);
    }
}
