package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * Prepares and verifies JDBC target tables without participating in row writes.
 *
 * <p>Each operation owns its DDL connection and statement. Existing tables are only checked;
 * no table is dropped, truncated or altered. DDL is executed in auto-commit mode and successful
 * statements are not rolled back when a later statement fails. The caller must hold the target
 * resource claim and wait for this operation to return before releasing it.
 */
public final class JdbcMetadataApplier implements MetadataApplier {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_STATEMENT_TIMEOUT_SECONDS = 30;

    private static final Set<SchemaChangeEventType> SUPPORTED = Set.of(SchemaChangeEventType.CREATE_TABLE);

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final JdbcCatalog catalog;
    private final int statementTimeoutSeconds;

    private Set<SchemaChangeEventType> accepted = SUPPORTED;
    private boolean canceled;
    private boolean closed;
    private transient boolean applying;
    private transient Statement activeStatement;

    /**
     * Creates a metadata applier without accessing the database.
     *
     * @param connections provider of caller-owned DDL connections
     * @param dialect target DDL and type renderer
     * @param catalog read-only target catalog owned by this applier
     * @param statementTimeoutSeconds positive timeout applied to every DDL statement
     */
    public JdbcMetadataApplier(
            JdbcConnectionProvider connections, JdbcDialect dialect, JdbcCatalog catalog, int statementTimeoutSeconds) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (statementTimeoutSeconds < 1) {
            throw new IllegalArgumentException("DDL statement timeout must be positive");
        }
        this.statementTimeoutSeconds = statementTimeoutSeconds;
    }

    /**
     * Renders a validated DDL plan using only the event and target dialect.
     *
     * <p>No connection is opened. This is not an existence, permission or execution check.
     *
     * @param event complete expected target structure and optional comments
     * @return ordered creation and comment statements
     */
    public JdbcDdlPlan plan(CreateTableEvent event) {
        Objects.requireNonNull(event, "event");
        TableSchema schema = event.getSchema();
        JdbcSinkSchemaValidator.validate(schema, schema, !schema.primaryKeys().isEmpty());
        for (Column column : schema.columns()) {
            JdbcNativeType nativeType = dialect.nativeType(column);
            if (nativeType.warning() != null) {
                throw new IllegalArgumentException("Target type requires explicit conversion: " + column.name());
            }
            if (schema.primaryKeys().contains(column.name())
                    && (column.nullable() || !nativeType.primaryKeySupported())) {
                throw new IllegalArgumentException("Target primary key is not safely representable: " + column.name());
            }
        }
        return dialect.createTablePlan(
                event.tableId(), schema, event.getTableComment(), event.getColumnComments());
    }

    @Override
    public void applySchemaChange(SchemaChangeEvent schemaChangeEvent) throws SchemaEvolveException {
        Objects.requireNonNull(schemaChangeEvent, "schemaChangeEvent");
        if (!(schemaChangeEvent instanceof CreateTableEvent event)
                || schemaChangeEvent.getType() != SchemaChangeEventType.CREATE_TABLE) {
            throw new SchemaEvolveException(schemaChangeEvent, "JDBC metadata applier supports CREATE_TABLE only");
        }
        begin(event);
        try {
            JdbcDdlPlan ddl = plan(event);
            checkCanceled(event);
            if (catalog.findTable(event.tableId()).isEmpty()) {
                createMissingTable(event, ddl);
            }
            checkCanceled(event);
            validateTable(event);
            checkCanceled(event);
        } catch (SchemaEvolveException failure) {
            throw failure;
        } catch (SQLException | RuntimeException failure) {
            throw new SchemaEvolveException(event, "JDBC target schema preparation could not be verified", failure);
        } finally {
            synchronized (this) {
                applying = false;
            }
        }
    }

    private void createMissingTable(CreateTableEvent event, JdbcDdlPlan ddl) throws SQLException {
        checkCanceled(event);
        try (Connection connection = connections.getConnection()) {
            checkCanceled(event);
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(statementTimeoutSeconds);
                synchronized (this) {
                    checkCanceled(event);
                    activeStatement = statement;
                }
                try {
                    for (int index = 0; index < ddl.statements().size(); index++) {
                        checkCanceled(event);
                        try {
                            statement.execute(ddl.statements().get(index));
                        } catch (SQLException failure) {
                            if (index == 0 && isTableAlreadyExists(failure)) {
                                // Another creator owns the existing object; do not write its comments.
                                return;
                            }
                            throw failure;
                        }
                    }
                } finally {
                    synchronized (this) {
                        activeStatement = null;
                    }
                }
            }
        }
    }

    private void validateTable(CreateTableEvent event) throws SQLException {
        JdbcTableInfo table = catalog.findTable(event.tableId())
                .orElseThrow(() -> new SQLException("Target table is absent after schema preparation"));
        if (!"TABLE".equalsIgnoreCase(table.type()) && !"BASE TABLE".equalsIgnoreCase(table.type())) {
            throw new IllegalArgumentException("Schema preparation requires a physical target table");
        }
        TableSchema expected = event.getSchema();
        TableSchema actual = catalog.getTable(event.tableId());
        Set<String> expectedNames = expected.columns().stream().map(Column::name).collect(Collectors.toSet());
        Set<String> actualNames = actual.columns().stream().map(Column::name).collect(Collectors.toSet());
        if (!expectedNames.equals(actualNames)) {
            throw new IllegalArgumentException("Target columns do not match the complete expected schema");
        }
        if (!expected.primaryKeys().equals(actual.primaryKeys())) {
            throw new IllegalArgumentException("Target primary keys do not match the expected key order");
        }
        JdbcSinkSchemaValidator.validate(expected, actual, !expected.primaryKeys().isEmpty());
        if (hasComment(event.getTableComment()) && !event.getTableComment().equals(table.remarks())) {
            throw new IllegalArgumentException("Target table comment does not match the requested metadata");
        }
        if (event.getColumnComments().values().stream().anyMatch(JdbcMetadataApplier::hasComment)) {
            Map<String, String> comments = new HashMap<>();
            for (JdbcColumnInfo column : catalog.getColumns(event.tableId())) {
                comments.put(column.name(), column.remarks());
            }
            for (var entry : event.getColumnComments().entrySet()) {
                if (hasComment(entry.getValue()) && !entry.getValue().equals(comments.get(entry.getKey()))) {
                    throw new IllegalArgumentException("Target column comment does not match: " + entry.getKey());
                }
            }
        }
    }

    private static boolean hasComment(String comment) {
        return comment != null && !comment.isBlank();
    }

    static boolean isTableAlreadyExists(SQLException failure) {
        return "42P07".equals(failure.getSQLState())
                || ("42S01".equals(failure.getSQLState()) && failure.getErrorCode() == 1050);
    }

    private synchronized void begin(CreateTableEvent event) {
        if (closed || applying) {
            throw new SchemaEvolveException(event, "Metadata applier is closed or already applying an operation");
        }
        if (!accepted.contains(SchemaChangeEventType.CREATE_TABLE)) {
            throw new SchemaEvolveException(event, "CREATE_TABLE is not enabled for this metadata applier");
        }
        checkCanceled(event);
        applying = true;
    }

    private synchronized void checkCanceled(CreateTableEvent event) {
        if (canceled || Thread.currentThread().isInterrupted()) {
            throw new SchemaEvolveException(
                    event, "Schema preparation was canceled", new CancellationException("Schema preparation canceled"));
        }
    }

    /**
     * Requests cancellation and attempts to cancel the registered DDL statement.
     *
     * <p>This is a request, not proof that the database operation stopped. A registered statement
     * can still complete and metadata calls must finish. The owner must await applySchemaChange's
     * return before closing this applier or releasing target claims.
     *
     * @throws SQLException if the driver rejects the cancellation request
     */
    public void cancel() throws SQLException {
        Statement statement;
        synchronized (this) {
            canceled = true;
            statement = activeStatement;
        }
        if (statement != null) {
            statement.cancel();
        }
    }

    @Override
    public synchronized JdbcMetadataApplier setAcceptedSchemaEvolutionTypes(Set<SchemaChangeEventType> types) {
        if (closed || applying) {
            throw new IllegalStateException("Cannot configure a closed or active metadata applier");
        }
        Set<SchemaChangeEventType> enabled = new HashSet<>(Set.copyOf(Objects.requireNonNull(types, "types")));
        enabled.retainAll(SUPPORTED);
        accepted = Set.copyOf(enabled);
        return this;
    }

    @Override
    public synchronized boolean acceptsSchemaEvolutionType(SchemaChangeEventType type) {
        return type != null && accepted.contains(type);
    }

    @Override
    public Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
        return SUPPORTED;
    }

    @Override
    public synchronized void close() {
        if (applying) {
            throw new IllegalStateException("Await metadata application completion before closing its resources");
        }
        if (!closed) {
            closed = true;
            catalog.close();
        }
    }

    private synchronized void writeObject(ObjectOutputStream output) throws IOException {
        if (applying) {
            throw new NotSerializableException("An active metadata applier cannot be serialized");
        }
        output.defaultWriteObject();
    }
}
