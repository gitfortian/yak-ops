package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import java.util.Objects;

/**
 * Owns MySQL reader-local schema acknowledgement alongside the Core reader context.
 *
 * <p>Both the mailbox reader and record emitter must observe the same pending schema change.
 */
public final class MySqlSourceReaderContext {
    private final SourceReaderContext context;
    private final SchemaChangeHandler schemaChanges;

    public MySqlSourceReaderContext(SourceReaderContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.schemaChanges = new SchemaChangeHandler(context);
    }

    public SourceReaderContext getSourceReaderContext() {
        return context;
    }

    public SchemaChangeHandler schemaChanges() {
        return schemaChanges;
    }
}
