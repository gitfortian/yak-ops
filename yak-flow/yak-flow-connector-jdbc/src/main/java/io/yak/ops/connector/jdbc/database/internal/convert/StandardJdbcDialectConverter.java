package io.yak.ops.connector.jdbc.database.internal.convert;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialectConverter;
import io.yak.ops.core.types.TableSchema;

/** Converts JDBC row values using an already resolved YakFlow schema. */
public final class StandardJdbcDialectConverter extends AbstractDialectConverter {

    public StandardJdbcDialectConverter(TableSchema schema) {
        super(schema);
    }
}
