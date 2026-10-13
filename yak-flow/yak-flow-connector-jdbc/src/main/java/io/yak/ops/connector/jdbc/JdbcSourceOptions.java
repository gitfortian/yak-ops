package io.yak.ops.connector.jdbc;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Internal JDBC source planning and fetch options, not product-level runtime controls. */
public final class JdbcSourceOptions {

    public static final Option<Integer> TARGET_ROWS_PER_SPLIT =
            option("connector.jdbc.source.target-rows-per-split", 50_000);

    public static final Option<Integer> MAX_SPLITS_PER_TABLE =
            option("connector.jdbc.source.max-splits-per-table", 16);

    public static final Option<Integer> READER_FETCH_BATCH_SIZE =
            option("connector.jdbc.source.reader-fetch-batch-size", 500);

    public static final Option<Integer> RESULT_SET_FETCH_SIZE =
            option("connector.jdbc.source.result-set-fetch-size", 500);

    public static final Option<Integer> QUERY_TIMEOUT_SECONDS =
            option("connector.jdbc.source.query-timeout-seconds", 60);

    public static final Option<Integer> CONNECTION_ATTEMPTS =
            option("connector.jdbc.source.connection-attempts", 3);

    private JdbcSourceOptions() {}

    private static Option<Integer> option(String name, int value) {
        return Options.key(name).intType().defaultValue(value);
    }
}
