package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import java.util.List;

/** Supported JDBC Sink write modes supplied by a persisted Connector definition. */
public final class JdbcSinkOptions {

    public static final Option<JdbcWriteMode> WRITE_MODE = Options.key("writeMode")
            .singleChoice(JdbcWriteMode.class, List.of(JdbcWriteMode.APPEND, JdbcWriteMode.UPSERT))
            .noDefaultValue();

    private JdbcSinkOptions() {}
}
