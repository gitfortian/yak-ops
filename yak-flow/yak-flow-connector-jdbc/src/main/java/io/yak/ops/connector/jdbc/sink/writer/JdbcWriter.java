package io.yak.ops.connector.jdbc.sink.writer;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.base.sink.writer.BatchingSinkWriterBase;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.event.SchemaChangeRequest;
import java.sql.SQLException;
import io.yak.ops.core.data.TableRecord;

/**
 * Bridges the shared mailbox-owned batch lifecycle to JDBC output and cancellation.
 *
 * <p>The Writer contains no independent record queue, scheduler or retry loop.
 * {@link JdbcOutputFormat} owns the connection and the only buffered-record owner; successful
 * checkpoint/end-of-input flushes commit synchronously, while failure, cancel and close
 * never implicitly commit pending records.
 */
public final class JdbcWriter extends BatchingSinkWriterBase<TableRecord>
        implements SchemaChangeAwareSinkWriter<TableRecord> {

    private final JdbcOutputFormat output;

    public JdbcWriter(JdbcOutputFormat output, BatchFlushPolicy policy, WriterInitContext context) {
        super(output, policy, context);
        this.output = output;
    }

    @Override
    public void validateSchemaChange(SchemaChangeRequest request) {
        output.validateSchemaChange(request);
    }

    @Override
    public void onSchemaChange(SchemaChangeRequest request, long schemaVersion) throws SQLException {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        output.updateSchema(request);
    }
}
