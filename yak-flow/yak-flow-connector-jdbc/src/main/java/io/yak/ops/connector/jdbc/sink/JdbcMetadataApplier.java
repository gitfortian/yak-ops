package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * Prepares and verifies JDBC target tables without participating in row writes.
 *
 * <p>Each operation owns its DDL connection and statement. CREATE_TABLE never modifies existing
 * tables; opt-in ADD_COLUMN and ALTER_COLUMN_TYPE support only safe, non-key changes on
 * MySQL/PostgreSQL. DDL is executed in auto-commit mode and successful statements are not rolled
 * back when a later statement fails. The caller must hold the target resource claim and wait for
 * this operation to return before releasing it.
 */
public final class JdbcMetadataApplier implements MetadataApplier {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_STATEMENT_TIMEOUT_SECONDS = 30;

    private static final Set<SchemaChangeEventType> CREATE_ONLY = Set.of(SchemaChangeEventType.CREATE_TABLE);
    private static final Set<SchemaChangeEventType> JDBC_EVOLUTION = Set.of(
            SchemaChangeEventType.CREATE_TABLE,
            SchemaChangeEventType.ADD_COLUMN,
            SchemaChangeEventType.ALTER_COLUMN_TYPE);

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final JdbcCatalog catalog;
    private final int statementTimeoutSeconds;

    private Set<SchemaChangeEventType> accepted = CREATE_ONLY;
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
        return plan(event, dialect);
    }

    /**
     * Renders the same validated statements used by execution without opening a connection.
     *
     * @param event complete expected structure and comments
     * @param dialect target dialect
     * @return ordered DDL statements
     */
    public static JdbcDdlPlan plan(CreateTableEvent event, JdbcDialect dialect) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(dialect, "dialect");
        TableSchema schema = event.getSchema();
        JdbcSinkSchemaValidator.validate(schema, schema, !schema.primaryKeys().isEmpty());
        for (Column column : schema.columns()) {
            JdbcNativeType nativeType = dialect.nativeType(column);
            ColumnProperties property = event.getColumnProperties().get(column.name());
            boolean vendorType =
                    dialect instanceof MySqlJdbcDialect && property != null && property.nativeType() != null;
            if (nativeType.warning() != null && !vendorType) {
                throw new IllegalArgumentException("Target type requires explicit conversion: " + column.name());
            }
            if (schema.primaryKeys().contains(column.name())
                    && (column.nullable() || !nativeType.primaryKeySupported())) {
                throw new IllegalArgumentException("Target primary key is not safely representable: " + column.name());
            }
        }
        return dialect.createTablePlan(
                event.tableId(),
                schema,
                event.getTableComment(),
                event.getColumnComments(),
                event.getColumnProperties());
    }

    @Override
    public void applySchemaChange(SchemaChangeEvent event) throws SchemaEvolveException {
        Objects.requireNonNull(event, "event");
        if (!getSupportedSchemaEvolutionTypes().contains(event.getType())) {
            throw new SchemaEvolveException(event, "JDBC dialect does not support this schema change");
        }
        begin(event);
        try {
            if (event instanceof CreateTableEvent create) {
                applyCreateTable(create);
            } else if (event instanceof AddColumnEvent add) {
                applyAddColumns(add);
            } else if (event instanceof AlterColumnTypeEvent alter) {
                applyAlterColumnTypes(alter);
            } else {
                throw new SchemaEvolveException(event, "Unsupported JDBC schema change event");
            }
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

    private void applyCreateTable(CreateTableEvent event) throws SQLException {
        checkCanceled(event);
        // An existing physical table does not require rendering the source's CREATE TABLE SQL.
        if (catalog.findTable(event.tableId()).isEmpty()) {
            JdbcDdlPlan ddl = plan(event);
            createMissingTable(event, ddl);
        }
        checkCanceled(event);
        validateTable(event);
    }

    /**
     * Adds ordinary nullable columns without mutating the existing key definition.
     * A complete preflight prevents a later invalid column from leaving a partial batch.
     */
    private void applyAddColumns(AddColumnEvent event) throws SQLException {
        requirePhysicalTable(event);
        TableSchema current = catalog.getTable(event.tableId());
        List<AddColumnEvent.ColumnWithPosition> missing = new ArrayList<>();
        List<List<String>> plans = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (AddColumnEvent.ColumnWithPosition addition : event.addedColumns()) {
            PhysicalColumn column = addition.column();
            if (addition.position() != AddColumnEvent.ColumnPosition.LAST) {
                throw new IllegalArgumentException("JDBC ADD_COLUMN supports LAST position only");
            }
            if (!names.add(column.name())
                    || !column.type().isNullable()
                    || !column.type().isResolved()) {
                throw new IllegalArgumentException("Invalid nullable column addition: " + column.name());
            }
            requirePlainColumn(column);
            Column actual = findColumn(current, column.name());
            if (actual != null) {
                verifyAddedColumn(event, column);
            } else {
                List<String> statements = dialect.addColumnStatements(event.tableId(), column);
                if (statements.isEmpty()) {
                    throw new IllegalArgumentException("JDBC column addition produced no DDL");
                }
                missing.add(addition);
                plans.add(statements);
            }
        }
        for (int index = 0; index < missing.size(); index++) {
            checkCanceled(event);
            PhysicalColumn column = missing.get(index).column();
            try {
                executeStatements(event, plans.get(index));
            } catch (SQLException failure) {
                if (!isColumnAlreadyExists(failure)) throw failure;
                // The duplicate-column error is only acceptable when the actual column matches.
            }
            verifyAddedColumn(event, column);
        }
    }

    /**
     * Requires complete old-type preconditions and a non-key, same-family safe widening.
     * Already-applied changes can be replayed after a successful DDL with a lost response.
     */
    private void applyAlterColumnTypes(AlterColumnTypeEvent event) throws SQLException {
        requirePhysicalTable(event);
        TableSchema schema = catalog.getTable(event.tableId());
        if (!event.oldTypeMapping().keySet().equals(event.typeMapping().keySet())) {
            throw new IllegalArgumentException("ALTER_COLUMN_TYPE requires every original column type");
        }
        List<String> columns = event.typeMapping().keySet().stream().sorted().toList();
        List<String> pending = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        for (String name : columns) {
            Column actual = findColumn(schema, name);
            if (actual == null || schema.primaryKeys().contains(name)) {
                throw new IllegalArgumentException("Cannot alter a missing or primary-key column: " + name);
            }
            LogicalType next = event.typeMapping().get(name);
            LogicalType previous = event.oldTypeMapping().get(name);
            if (!next.isResolved() || !previous.isResolved() || next.isNullable() != actual.nullable()) {
                throw new IllegalArgumentException("Invalid column type or nullability change: " + name);
            }
            JdbcColumnInfo metadata = findColumnInfo(event.tableId(), name);
            requirePlainColumn(metadata);
            if (sameType(actual.dataType(), next)) continue;
            if (!sameType(actual.dataType(), previous) || !safeWidening(previous, next)) {
                throw new IllegalArgumentException("Stale or unsafe column type change: " + name);
            }
            JdbcNativeType rendered = dialect.nativeType(new Column(name, next));
            if (rendered.warning() != null) {
                throw new IllegalArgumentException("Column widening requires explicit native type approval: " + name);
            }
            pending.add(name);
            statements.add(dialect.alterColumnTypeSql(
                    event.tableId(), actual, new Column(name, next), metadata.remarks()));
        }
        for (int index = 0; index < pending.size(); index++) {
            checkCanceled(event);
            executeStatements(event, List.of(statements.get(index)));
            String name = pending.get(index);
            Column column = findColumn(catalog.getTable(event.tableId()), name);
            if (column == null || !sameType(column.dataType(), event.typeMapping().get(name))) {
                throw new IllegalArgumentException("Target column type did not match the applied DDL: " + name);
            }
            requirePlainColumn(findColumnInfo(event.tableId(), name));
        }
    }

    private void requirePhysicalTable(SchemaChangeEvent event) throws SQLException {
        JdbcTableInfo table = catalog.findTable(event.tableId())
                .orElseThrow(() -> new SQLException("Target table does not exist for schema evolution"));
        if (!"TABLE".equalsIgnoreCase(table.type()) && !"BASE TABLE".equalsIgnoreCase(table.type())) {
            throw new IllegalArgumentException("Schema evolution requires a physical target table");
        }
    }

    private void verifyAddedColumn(AddColumnEvent event, PhysicalColumn expected) throws SQLException {
        Column actual = findColumn(catalog.getTable(event.tableId()), expected.name());
        if (actual == null || !sameType(actual.dataType(), expected.type())) {
            throw new IllegalArgumentException("Target column does not match requested addition: " + expected.name());
        }
        JdbcColumnInfo physical = findColumnInfo(event.tableId(), expected.name());
        requirePlainColumn(physical);
        if (!physical.nullable() || (expected.comment() != null
                && !expected.comment().equals(physical.remarks()))) {
            throw new IllegalArgumentException("Target column properties do not match: " + expected.name());
        }
    }

    private JdbcColumnInfo findColumnInfo(TableId table, String name) throws SQLException {
        return catalog.getColumns(table).stream()
                .filter(column -> column.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target column is missing: " + name));
    }

    private static Column findColumn(TableSchema schema, String name) {
        return schema.columns().stream().filter(column -> column.name().equals(name)).findFirst().orElse(null);
    }

    private static void requirePlainColumn(PhysicalColumn column) {
        ColumnProperties properties = column.properties();
        if (properties != null) {
            String nativeType = properties.nativeType() == null
                    ? ""
                    : properties.nativeType().toLowerCase(java.util.Locale.ROOT);
            if (!properties.isKnown()
                    || properties.hasFeatures()
                    || nativeType.contains("unsigned")
                    || nativeType.startsWith("enum(")
                    || nativeType.startsWith("set(")) {
                throw new IllegalArgumentException(
                        "Automatic column evolution cannot preserve native properties: " + column.name());
            }
        }
    }

    private static void requirePlainColumn(JdbcColumnInfo column) {
        if (!Boolean.FALSE.equals(column.autoIncrement())
                || !Boolean.FALSE.equals(column.generated())
                || column.hasDefaultValue()
                || column.defaultExpression() != null
                || column.onUpdateExpression() != null
                || (column.nativeType() != null
                        && column.nativeType().toLowerCase(java.util.Locale.ROOT).contains("unsigned"))) {
            throw new IllegalArgumentException("Column has native properties that must be preserved: " + column.name());
        }
    }

    private static boolean sameType(LogicalType first, LogicalType second) {
        return first.equals(second);
    }

    private static boolean safeWidening(LogicalType previous, LogicalType next) {
        LogicalTypeRoot root = previous.getTypeRoot();
        if (root != next.getTypeRoot() || previous.isNullable() != next.isNullable()) return false;
        if (!Set.of(
                        LogicalTypeRoot.VARCHAR,
                        LogicalTypeRoot.VARBINARY,
                        LogicalTypeRoot.DECIMAL,
                        LogicalTypeRoot.TIME_WITHOUT_TIME_ZONE,
                        LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE)
                .contains(root)) return false;
        Column before = new Column("field", previous);
        Column after = new Column("field", next);
        if (before.length() == null && after.length() != null
                && (root == LogicalTypeRoot.VARCHAR || root == LogicalTypeRoot.VARBINARY)) return false;
        return JdbcSchemaCompatibility.isCompatible(before, after);
    }

    /** Executes auto-committed DDL while retaining cancellation and unknown-outcome semantics. */
    private void executeStatements(SchemaChangeEvent event, List<String> statements) throws SQLException {
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
                    for (String sql : statements) {
                        checkCanceled(event);
                        try {
                            statement.execute(sql);
                        } catch (SQLException failure) {
                            if (isOutcomeUnknown(failure)) {
                                throw new SchemaEvolveException(event, "DDL completion cannot be confirmed", failure, true);
                            }
                            throw failure;
                        } catch (RuntimeException failure) {
                            throw new SchemaEvolveException(event, "DDL completion cannot be confirmed", failure, true);
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

    private static boolean isColumnAlreadyExists(SQLException failure) {
        return "42701".equals(failure.getSQLState())
                || ("42S21".equals(failure.getSQLState()) && failure.getErrorCode() == 1060);
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
                            if (isOutcomeUnknown(failure)) {
                                throw new SchemaEvolveException(
                                        event, "DDL completion cannot be confirmed", failure, true);
                            }
                            throw failure;
                        } catch (RuntimeException failure) {
                            throw new SchemaEvolveException(event, "DDL completion cannot be confirmed", failure, true);
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
        TableSchema actual = catalog.getTable(event.tableId());
        validateSchema(event, actual);
        Map<String, String> comments = new HashMap<>();
        Map<String, ColumnProperties> expected = event.getColumnProperties();
        for (JdbcColumnInfo column : catalog.getColumns(event.tableId())) {
            ColumnProperties planned = expected.get(column.name());
            ColumnProperties actualProperties = new ColumnProperties(
                    column.defaultExpression(),
                    column.autoIncrement(),
                    column.generated(),
                    column.onUpdateExpression(),
                    column.defaultGenerated(),
                    column.nativeType());
            validateColumnProperties(column.name(), planned, actualProperties);
            comments.put(column.name(), column.remarks());
        }
        validateComments(event, table.remarks(), comments);
    }

    /**
     * Verifies every writable column attribute without accepting an altered target.
     * Default literals may be returned quoted by JDBC and are normalized for comparison.
     */
    public static void validateColumnProperties(String column, ColumnProperties expected, ColumnProperties actual) {
        if (!actual.isKnown()) {
            throw new IllegalArgumentException("Unknown target generation metadata for column: " + column);
        }
        if (expected == null) {
            if (actual.hasFeatures()) {
                throw new IllegalArgumentException("Unexpected target column properties: " + column);
            }
            return;
        }
        if (!expected.isKnown()
                || !java.util.Objects.equals(expected.autoIncrement(), actual.autoIncrement())
                || !java.util.Objects.equals(expected.generated(), actual.generated())
                || (expected.defaultExpression() != null
                        && expected.defaultGenerated() != null
                        && !java.util.Objects.equals(expected.defaultGenerated(), actual.defaultGenerated()))
                || !java.util.Objects.equals(
                        normalizeDefault(expected.onUpdateExpression(), true),
                        normalizeDefault(actual.onUpdateExpression(), true))
                || !java.util.Objects.equals(
                        normalizeDefault(expected.defaultExpression(), expected.defaultGenerated()),
                        normalizeDefault(actual.defaultExpression(), actual.defaultGenerated()))
                || (expected.nativeType() != null && !expected.nativeType().equals(actual.nativeType()))) {
            throw new IllegalArgumentException("Target column properties do not match: " + column);
        }
    }

    private static String normalizeDefault(String expression, Boolean generated) {
        if (expression == null) return null;
        if (Boolean.TRUE.equals(generated)
                && expression.replaceAll("[()\\s]+", "").matches("(?i)curdate(?:[+]interval[0-9]{1,3}day)?")) {
            return expression.replaceAll("[()\\s]+", "").toUpperCase(java.util.Locale.ROOT);
        }
        if (Boolean.TRUE.equals(generated) && expression.trim().matches("(?i)CURRENT_TIMESTAMP(?:\\([0-6]?\\))?")) {
            return expression
                    .trim()
                    .replaceAll("(?i)current_timestamp\\(\\)", "CURRENT_TIMESTAMP")
                    .toUpperCase(java.util.Locale.ROOT);
        }
        return expression;
    }

    /**
     * Validates the complete expected field set, key order and write compatibility.
     *
     * @param event expected complete target schema
     * @param actual physical schema obtained from the target Catalog
     */
    public static void validateSchema(CreateTableEvent event, TableSchema actual) {
        TableSchema expected = event.getSchema();
        Set<String> expectedNames =
                expected.columns().stream().map(Column::name).collect(Collectors.toSet());
        Set<String> actualNames = actual.columns().stream().map(Column::name).collect(Collectors.toSet());
        if (!expectedNames.equals(actualNames)) {
            throw new IllegalArgumentException("Target columns do not match the complete expected schema");
        }
        if (!expected.primaryKeys().equals(actual.primaryKeys())) {
            throw new IllegalArgumentException("Target primary keys do not match the expected key order");
        }
        JdbcSinkSchemaValidator.validate(
                expected, actual, !expected.primaryKeys().isEmpty());
    }

    /**
     * Verifies explicitly requested comments without writing to an existing object.
     *
     * @param event expected table and column comments
     * @param tableComment physical table comment
     * @param columnComments physical comments keyed by exact column name
     */
    public static void validateComments(
            CreateTableEvent event, String tableComment, Map<String, String> columnComments) {
        if (hasComment(event.getTableComment()) && !event.getTableComment().equals(tableComment)) {
            throw new IllegalArgumentException("Target table comment does not match the requested metadata");
        }
        for (var entry : event.getColumnComments().entrySet()) {
            if (hasComment(entry.getValue()) && !entry.getValue().equals(columnComments.get(entry.getKey()))) {
                throw new IllegalArgumentException("Target column comment does not match: " + entry.getKey());
            }
        }
    }

    private static boolean hasComment(String comment) {
        return comment != null && !comment.isBlank();
    }

    /** Distinguishes server-confirmed statement errors from a broken or timed-out response channel. */
    static boolean isOutcomeUnknown(SQLException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.io.IOException
                    || cause instanceof java.sql.SQLTimeoutException
                    || cause instanceof java.sql.SQLRecoverableException) return true;
            if (cause instanceof SQLException sql
                    && (sql.getSQLState() == null || sql.getSQLState().startsWith("08"))) return true;
        }
        return false;
    }

    static boolean isTableAlreadyExists(SQLException failure) {
        return "42P07".equals(failure.getSQLState())
                || ("42S01".equals(failure.getSQLState()) && failure.getErrorCode() == 1050);
    }

    private synchronized void begin(SchemaChangeEvent event) {
        if (closed || applying) {
            throw new SchemaEvolveException(event, "Metadata applier is closed or already applying an operation");
        }
        if (!accepted.contains(event.getType())) {
            throw new SchemaEvolveException(event, "Schema change is not enabled for this metadata applier");
        }
        checkCanceled(event);
        applying = true;
    }

    private synchronized void checkCanceled(SchemaChangeEvent event) {
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
    @Override
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
        enabled.retainAll(getSupportedSchemaEvolutionTypes());
        accepted = Set.copyOf(enabled);
        return this;
    }

    @Override
    public synchronized boolean acceptsSchemaEvolutionType(SchemaChangeEventType type) {
        return type != null && accepted.contains(type);
    }

    @Override
    public Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
        return dialect instanceof MySqlJdbcDialect || dialect instanceof PostgresJdbcDialect
                ? JDBC_EVOLUTION
                : CREATE_ONLY;
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
