package io.yak.ops.connector.jdbc.database.schema;

import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import java.util.ArrayList;
import java.util.HashMap;
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
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(dialect, "dialect");
        List<SchemaChangePlan.Difference> differences = new ArrayList<>();
        List<SchemaChangeEvent> changes = new ArrayList<>();

        if (target.isEmpty()) {
            List<PhysicalColumn> columns = new ArrayList<>();
            for (PhysicalColumn column : source.columns()) {
                LogicalType normalized = normalize(dialect, column, differences);
                if (normalized != null) {
                    columns.add(column.copy(normalized));
                }
            }
            checkKeys(source, source, differences);
            if (differences.stream().noneMatch(d -> d.kind() == SchemaChangePlan.Kind.INCOMPATIBLE)) {
                Schema targetSchema = new Schema(columns, source.primaryKeys(), source.comment());
                changes.add(new CreateTableEvent(tableId, targetSchema));
                differences.add(difference(null, SchemaChangePlan.Kind.CREATE_TABLE,
                        SchemaChangePlan.Reason.TABLE_MISSING, null, null));
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
                LogicalType normalized = normalize(dialect, incoming, differences);
                if (normalized == null) continue;
                if (source.primaryKeys().contains(incoming.name()) || !normalized.isNullable()
                        || !ordinaryColumn(incoming)) {
                    differences.add(difference(incoming.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                            source.primaryKeys().contains(incoming.name())
                                    ? SchemaChangePlan.Reason.MISSING_KEY_COLUMN
                                    : SchemaChangePlan.Reason.UNSAFE_COLUMN_PROPERTIES,
                            normalized, null));
                } else {
                    additions.add(AddColumnEvent.last(incoming.copy(normalized)));
                    differences.add(difference(incoming.name(), SchemaChangePlan.Kind.ADD_COLUMN,
                            SchemaChangePlan.Reason.COLUMN_MISSING, normalized, null));
                }
                continue;
            }

            if (!incoming.type().isResolved() || !actual.type().isResolved()) {
                differences.add(difference(incoming.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                        SchemaChangePlan.Reason.UNRESOLVED_TYPE, incoming.type(), actual.type()));
                continue;
            }
            if (incoming.type().isNullable() && !actual.type().isNullable()) {
                differences.add(difference(incoming.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                        SchemaChangePlan.Reason.NULLABILITY_MISMATCH, incoming.type(), actual.type()));
                continue;
            }
            LogicalType planned = normalize(dialect, incoming, differences);
            if (planned == null) continue;
            Column expected = new Column(incoming.name(), planned);
            Column actualColumn = new Column(actual.name(), actual.type());
            if (JdbcSchemaCompatibility.isCompatible(expected, actualColumn)) {
                differences.add(difference(incoming.name(), SchemaChangePlan.Kind.COMPATIBLE,
                        SchemaChangePlan.Reason.COMPATIBLE, planned, actual.type()));
            } else if (!source.primaryKeys().contains(incoming.name())
                    && !current.primaryKeys().contains(incoming.name())
                    && ordinaryColumn(incoming)
                    && ordinaryColumn(actual)
                    && canWiden(planned, actual.type())) {
                widened.put(incoming.name(), planned.copy(actual.type().isNullable()));
                previous.put(incoming.name(), actual.type());
                differences.add(difference(incoming.name(), SchemaChangePlan.Kind.ALTER_COLUMN_TYPE,
                        SchemaChangePlan.Reason.COLUMN_WIDENING, planned, actual.type()));
            } else {
                differences.add(difference(incoming.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                        SchemaChangePlan.Reason.UNSAFE_TYPE_CHANGE, planned, actual.type()));
            }
            if (!Objects.equals(incoming.properties(), actual.properties())
                    || !Objects.equals(incoming.comment(), actual.comment())) {
                differences.add(difference(incoming.name(), SchemaChangePlan.Kind.METADATA_DIFFERENCE,
                        SchemaChangePlan.Reason.COLUMN_METADATA_DIFFERENCE, incoming.type(), actual.type()));
            }
        }

        Set<String> names = byName(source).keySet();
        for (PhysicalColumn extra : current.columns()) {
            if (!names.contains(extra.name())) {
                differences.add(difference(extra.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                        SchemaChangePlan.Reason.COLUMN_ONLY_IN_TARGET, null, extra.type()));
            }
        }
        if (differences.stream().noneMatch(d -> d.kind() == SchemaChangePlan.Kind.INCOMPATIBLE)) {
            if (!additions.isEmpty()) changes.add(new AddColumnEvent(tableId, additions));
            if (!widened.isEmpty()) changes.add(new AlterColumnTypeEvent(tableId, widened, previous));
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
                        LogicalTypeRoot.TINYINT,
                        LogicalTypeRoot.SMALLINT,
                        LogicalTypeRoot.INTEGER,
                        LogicalTypeRoot.BIGINT,
                        LogicalTypeRoot.TIME_WITHOUT_TIME_ZONE,
                        LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE)
                .contains(from)) {
            return false;
        }
        Column old = new Column("column", current.copy(true));
        Column candidate = new Column("column", proposed.copy(true));
        return JdbcSchemaCompatibility.isCompatible(old, candidate)
                && JdbcSchemaCompatibility.isCompatible(candidate, candidate);
    }

    private static LogicalType normalize(
            JdbcDialect dialect, PhysicalColumn column, List<SchemaChangePlan.Difference> differences) {
        try {
            if (!column.type().isResolved()) {
                differences.add(difference(column.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                        SchemaChangePlan.Reason.UNRESOLVED_TYPE, column.type(), null));
                return null;
            }
            Column logical = new Column(column.name(), column.type());
            var nativeType = dialect.nativeType(logical);
            if (!nativeType.primaryKeySupported()) {
                // A column may be a valid non-key type; callers verify key restrictions separately.
            }
            return dialect.normalizeTargetType(logical);
        } catch (IllegalArgumentException | UnsupportedOperationException failure) {
            differences.add(difference(column.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                    SchemaChangePlan.Reason.UNSUPPORTED_TYPE, column.type(), null));
            return null;
        }
    }

    private static void checkKeys(
            Schema source, Schema target, List<SchemaChangePlan.Difference> differences) {
        if (!source.primaryKeys().equals(target.primaryKeys())) {
            differences.add(difference(null, SchemaChangePlan.Kind.INCOMPATIBLE,
                    SchemaChangePlan.Reason.PRIMARY_KEY_MISMATCH, null, null));
        }
    }

    private static void checkIdentifiers(
            Schema source, Schema target, List<SchemaChangePlan.Difference> differences) {
        for (Schema schema : List.of(source, target)) {
            Set<String> unique = new HashSet<>();
            for (PhysicalColumn column : schema.columns()) {
                if (!unique.add(column.name().toLowerCase(Locale.ROOT))) {
                    differences.add(difference(column.name(), SchemaChangePlan.Kind.INCOMPATIBLE,
                            SchemaChangePlan.Reason.AMBIGUOUS_IDENTIFIER, column.type(), null));
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

    private static SchemaChangePlan.Difference difference(
            String name,
            SchemaChangePlan.Kind kind,
            SchemaChangePlan.Reason reason,
            LogicalType sourceType,
            LogicalType targetType) {
        return new SchemaChangePlan.Difference(name, kind, reason, sourceType, targetType);
    }
}
