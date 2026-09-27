package io.yak.ops.flow.connector.jdbc.sink;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.Sink;
import io.yak.ops.flow.api.sink.SinkWriter;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialects;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import java.util.Objects;

/**
 * YakFlow JDBC batch Sink；目标表必须已经存在。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcSink implements Sink {

    private final JdbcSinkConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;

    public JdbcSink(JdbcSinkConfig config) {
        this(config, JdbcConnectionRuntime.getInstance());
    }

    public JdbcSink(JdbcSinkConfig config, JdbcConnectionProvider connectionProvider) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
        this.dialect = JdbcDialects.forType(config.connection().type());
    }

    @Override
    public SinkWriter createWriter(YakTableSchema schema) {
        return new JdbcSinkWriter(config, schema, connectionProvider, dialect);
    }
}
