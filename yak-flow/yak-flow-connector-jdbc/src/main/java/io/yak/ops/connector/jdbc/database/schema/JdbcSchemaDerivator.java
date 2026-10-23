package io.yak.ops.connector.jdbc.database.schema;

import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan.Kind;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan.Reason;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DropColumnEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Derives supported JDBC schema changes from source and physical target metadata.
 *
 * <p>Only non-key, nullable ordinary columns can be added to an existing table. Existing column
 * types may be widened when both the original and new representations can be contained safely.
 * The current JDBC Sink binds every target field: target-only fields are therefore blocking,
 * even when the database could accept an omitted value. No database connections or SQL are used.
 */
public final class JdbcSchemaDerivator {

    private JdbcSchemaDerivator() {}

    /**
     * Compares a complete physical source schema with a possibly absent target.
     *
     * <p>Returns a deterministic list of differences and candidate events. Incompatible tables
     * yield an empty event list, so no partial DDL can be mistaken for a safe migration plan.
     * Target-side type mapping is supplied by the dialect used to create the target objects.
     */
    public static SchemaChangePlan derive(
            TableId tableId, Schema source, Optional<Schema> target, JdbcDialect dialect) {
        return derive(tableId, source, target, dialect, Set.of());
    }

    /** Allows deleting only the columns explicitly removed by an authenticated source transition. */
    public static SchemaChangePlan derive(
            TableId tableId, Schema source, Optional<Schema> target,
            JdbcDialect dialect, Set<String> authorizedDroppedColumns) {
        Objects.requireNonNull(authorizedDroppedColumns, "authorizedDroppedColumns");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(dialect, "dialect");
        List<SchemaChangePlan.Difference> differences = new ArrayList<>();
        List<SchemaChangeEvent> changes = new ArrayList<>();

        if (target.isEmpty()) {
            checkIdentifiers(source, source, differences);
            List<PhysicalColumn> columns = new ArrayList<>();
            for (PhysicalColumn column : source.columns()) {
                LogicalType normalized =
                        normalize(dialect, column, source.primaryKeys().contains(column.name()), differences);
                if (normalized != null) {
                    columns.add(column.copy(normalized));
                }
            }
            checkKeys(source, source, differences);
            if (differences.stream().noneMatch(d -> d.kind() == Kind.INCOMPATIBLE)) {
                Schema targetSchema = new Schema(columns, source.primaryKeys(), source.comment());
                changes.add(new CreateTableEvent(tableId, targetSchema));
                add(differences, null, Kind.CREATE_TABLE, Reason.TABLE_MISSING, null, null);
            }
            return new SchemaChangePlan(tableId, differences, changes);
        }

        Schema current = target.orElseThrow();
        checkIdentifiers(source, current, differences);
        checkKeys(source, current, differences);

        Map<String, PhysicalColumn> existing = byName(current);
        List<AddColumnEvent.ColumnWithPosition> additions = new ArrayList<>();
        Map<String, LogicalType> widened = new LinkedHashMap<>();
        Map<String, LogicalType> previous = new LinkedHashMap<>();

        for (PhysicalColumn incoming : source.columns()) {
            PhysicalColumn actual = existing.get(incoming.name());
            if (actual == null) {
                LogicalType normalized =
                        normalize(dialect, incoming, source.primaryKeys().contains(incoming.name()), differences);
                if (normalized == null) continue;
                if (source.primaryKeys().contains(incoming.name())
                        || !normalized.isNullable()
                        || !ordinaryColumn(incoming)) {
                    add(
                            differences,
                            incoming.name(),
                            Kind.INCOMPATIBLE,
                            source.primaryKeys().contains(incoming.name())
                                    ? Reason.MISSING_KEY_COLUMN
                                    : Reason.UNSAFE_COLUMN_PROPERTIES,
                            normalized,
                            null);
                } else {
                    additions.add(AddColumnEvent.last(incoming.copy(normalized)));
                    add(differences, incoming.name(), Kind.ADD_COLUMN, Reason.COLUMN_MISSING, normalized, null);
                }
                continue;
            }

            if (actual.properties() != null
                    && Boolean.TRUE.equals(actual.properties().generated())) {
                add(
                        differences,
                        incoming.name(),
                        Kind.INCOMPATIBLE,
                        Reason.UNSAFE_COLUMN_PROPERTIES,
                        incoming.type(),
                        actual.type());
                continue;
            }
            if (!incoming.type().isResolved() || !actual.type().isResolved()) {
                add(
                        differences,
                        incoming.name(),
                        Kind.INCOMPATIBLE,
                        Reason.UNRESOLVED_TYPE,
                        incoming.type(),
                        actual.type());
                continue;
            }
            if (incoming.type().isNullable() && !actual.type().isNullable()) {
                add(
                        differences,
                        incoming.name(),
                        Kind.INCOMPATIBLE,
                        Reason.NULLABILITY_MISMATCH,
                        incoming.type(),
                        actual.type());
                continue;
            }
            LogicalType planned =
                    normalize(dialect, incoming, source.primaryKeys().contains(incoming.name()), differences);
            if (planned == null) continue;
            if (canContain(planned, actual.type())) {
                add(differences, incoming.name(), Kind.COMPATIBLE, Reason.COMPATIBLE, planned, actual.type());
            } else if (!source.primaryKeys().contains(incoming.name())
                    && !current.primaryKeys().contains(incoming.name())
                    && ordinaryColumn(incoming)
                    && ordinaryColumn(actual)
                    && canWiden(planned, actual.type())) {
                widened.put(incoming.name(), planned.copy(actual.type().isNullable()));
                previous.put(incoming.name(), actual.type());
                add(
                        differences,
                        incoming.name(),
                        Kind.ALTER_COLUMN_TYPE,
                        Reason.COLUMN_WIDENING,
                        planned,
                        actual.type());
            } else {
                add(differences, incoming.name(), Kind.INCOMPATIBLE, Reason.UNSAFE_TYPE_CHANGE, planned, actual.type());
            }
            if (!Objects.equals(incoming.properties(), actual.properties())
                    || !Objects.equals(incoming.comment(), actual.comment())) {
                add(
                        differences,
                        incoming.name(),
                        Kind.METADATA_DIFFERENCE,
                        Reason.COLUMN_METADATA_DIFFERENCE,
                        incoming.type(),
                        actual.type());
            }
        }

        if (!Objects.equals(source.comment(), current.comment())) {
            add(differences, null, Kind.METADATA_DIFFERENCE, Reason.COLUMN_METADATA_DIFFERENCE, null, null);
        }
        Set<String> names = byName(source).keySet();
        List<String> dropped = new ArrayList<>();
        Map<String, LogicalType> droppedTypes = new LinkedHashMap<>();
        for (PhysicalColumn extra : current.columns()) {
            if (!names.contains(extra.name())) {
                if (authorizedDroppedColumns.contains(extra.name())
                        && !current.primaryKeys().contains(extra.name())
                        && ordinaryColumn(extra)
                        && extra.type().isResolved()) {
                    dropped.add(extra.name());
                    droppedTypes.put(extra.name(), extra.type());
                    add(differences, extra.name(), Kind.DROP_COLUMN, Reason.COLUMN_ONLY_IN_TARGET, null, extra.type());
                } else {
                    add(differences, extra.name(), Kind.INCOMPATIBLE, Reason.COLUMN_ONLY_IN_TARGET, null, extra.type());
                }
            }
        }
        if (differences.stream().noneMatch(d -> d.kind() == Kind.INCOMPATIBLE)) {
            if (!additions.isEmpty()) changes.add(new AddColumnEvent(tableId, additions));
            if (!widened.isEmpty()) changes.add(new AlterColumnTypeEvent(tableId, widened, previous));
            if (!dropped.isEmpty()) changes.add(new DropColumnEvent(tableId, dropped, droppedTypes));
        }
        return new SchemaChangePlan(tableId, differences, changes);
    }

    private static boolean canWiden(LogicalType proposed, LogicalType current) {
        LogicalTypeRoot from = current.getTypeRoot();
        LogicalTypeRoot to = proposed.getTypeRoot();
        if (from != to || !proposed.isResolved() || !current.isResolved()) return false;
        if (!Set.of(
                        LogicalTypeRoot.VARCHAR,
                        LogicalTypeRoot.VARBINARY,
                        LogicalTypeRoot.DECIMAL,
                        LogicalTypeRoot.TIME_WITHOUT_TIME_ZONE,
                        LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE)
                .contains(from)) {
            return false;
        }
        return canContain(current, proposed);
    }

    /**
     * Requires explicit containment for unbounded variable-length fields. The older preview
     * compatibility rule tolerates unknown lengths, which is unsafe for automatic DDL planning.
     */
    private static boolean canContain(LogicalType source, LogicalType target) {
        Column from = new Column("field", source);
        Column to = new Column("field", target);
        LogicalTypeRoot sourceRoot = source.getTypeRoot();
        LogicalTypeRoot targetRoot = target.getTypeRoot();
        boolean variable = (sourceRoot == LogicalTypeRoot.VARCHAR && targetRoot == LogicalTypeRoot.VARCHAR)
                || (sourceRoot == LogicalTypeRoot.VARBINARY && targetRoot == LogicalTypeRoot.VARBINARY);
        if (variable && from.length() == null && to.length() != null) {
            return false;
        }
        return JdbcSchemaCompatibility.isCompatible(from, to);
    }

    private static LogicalType normalize(
            JdbcDialect dialect, PhysicalColumn column, boolean key, List<SchemaChangePlan.Difference> differences) {
        try {
            if (!column.type().isResolved()) {
                add(differences, column.name(), Kind.INCOMPATIBLE, Reason.UNRESOLVED_TYPE, column.type(), null);
                return null;
            }
            ColumnProperties physical = column.properties();
            if (physical != null
                    && physical.nativeType() != null
                    && physical.nativeType().toLowerCase(Locale.ROOT).contains("unsigned")) {
                add(differences, column.name(), Kind.INCOMPATIBLE, Reason.UNSUPPORTED_TYPE, column.type(), null);
                return null;
            }
            Column logical = new Column(column.name(), column.type());
            var nativeType = dialect.nativeType(logical);
            if (key && !nativeType.primaryKeySupported()) {
                add(differences, column.name(), Kind.INCOMPATIBLE, Reason.UNSUPPORTED_TYPE, column.type(), null);
                return null;
            }
            return dialect.normalizeTargetType(logical);
        } catch (IllegalArgumentException | UnsupportedOperationException failure) {
            add(differences, column.name(), Kind.INCOMPATIBLE, Reason.UNSUPPORTED_TYPE, column.type(), null);
            return null;
        }
    }

    private static void checkKeys(Schema source, Schema target, List<SchemaChangePlan.Difference> differences) {
        if (!source.primaryKeys().equals(target.primaryKeys())) {
            add(differences, null, Kind.INCOMPATIBLE, Reason.PRIMARY_KEY_MISMATCH, null, null);
        }
    }

    private static void checkIdentifiers(Schema source, Schema target, List<SchemaChangePlan.Difference> differences) {
        for (Schema schema : List.of(source, target)) {
            Set<String> unique = new HashSet<>();
            for (PhysicalColumn column : schema.columns()) {
                if (!unique.add(column.name().toLowerCase(Locale.ROOT))) {
                    add(
                            differences,
                            column.name(),
                            Kind.INCOMPATIBLE,
                            Reason.AMBIGUOUS_IDENTIFIER,
                            column.type(),
                            null);
                }
            }
        }
    }

    private static boolean ordinaryColumn(PhysicalColumn column) {
        ColumnProperties props = column.properties();
        return props == null
                || (!Boolean.TRUE.equals(props.autoIncrement())
                        && !Boolean.TRUE.equals(props.generated())
                        && props.onUpdateExpression() == null
                        && props.defaultExpression() == null
                        && !Boolean.TRUE.equals(props.defaultGenerated()));
    }

    private static Map<String, PhysicalColumn> byName(Schema schema) {
        Map<String, PhysicalColumn> result = new LinkedHashMap<>();
        for (PhysicalColumn column : schema.columns()) result.put(column.name(), column);
        return result;
    }

    private static void add(
            List<SchemaChangePlan.Difference> differences,
            String name,
            Kind kind,
            Reason reason,
            LogicalType sourceType,
            LogicalType targetType) {
        differences.add(new SchemaChangePlan.Difference(name, kind, reason, sourceType, targetType));
    }
}
