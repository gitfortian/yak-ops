package io.yak.ops.flow.runtime.schema;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.event.ColumnProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HexFormat;

/**
 * Mailbox-independent state model for source and target schema versions.
 *
 * <p>The coordinator serializes changes. This class still synchronizes every transition because
 * checkpoint snapshots and diagnostics can be requested from another runtime thread.
 */
public final class SchemaManager {

    private static final int MAX_REPLAY_HISTORY = 256;

    private final Map<TableId, SchemaManagerSnapshot.TableState> tables = new LinkedHashMap<>();
    private final Map<TableId, SchemaChangeRequest> pending = new LinkedHashMap<>();
    private final Map<TableId, Long> pendingVersions = new LinkedHashMap<>();

    /** Begins a target transition or reports an already committed, identical replay. */
    public synchronized Change begin(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        String fingerprint = fingerprint(request);
        TableId target = request.targetTable();
        SchemaManagerSnapshot.TableState state = tables.get(target);
        if (state == null) {
            state = new SchemaManagerSnapshot.TableState(
                    request.sourceTable(),
                    target,
                    request.sourceSchemaBefore(),
                    request.targetSchemaBefore(),
                    0,
                    Map.of());
            tables.put(target, state);
        }
        if (!state.sourceTable().equals(request.sourceTable())) {
            throw new IllegalStateException("Schema request changes the source identity for an existing target");
        }
        String appliedFingerprint = state.appliedEventFingerprints().get(request.eventId());
        if (appliedFingerprint != null) {
            if (!appliedFingerprint.equals(fingerprint)) {
                throw new IllegalStateException("Schema event ID was reused for a different transition");
            }
            return new Change(true, state.version());
        }
        SchemaChangeRequest active = pending.get(target);
        if (active != null) {
            if (active.eventId().equals(request.eventId()) && fingerprint(active).equals(fingerprint)) {
                return new Change(false, pendingVersions.get(target));
            }
            throw new IllegalStateException("Another schema transition is already active for this target");
        }
        if (!state.sourceSchema().equals(request.sourceSchemaBefore())
                || !state.targetSchema().equals(request.targetSchemaBefore())) {
            throw new IllegalStateException("Schema request is stale relative to the committed schema version");
        }
        long nextVersion = Math.addExact(state.version(), 1);
        pending.put(target, request);
        pendingVersions.put(target, nextVersion);
        return new Change(false, nextVersion);
    }

    /** Commits both source and target shapes only after DDL and every Writer update succeed. */
    public synchronized long complete(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        TableId target = request.targetTable();
        SchemaChangeRequest active = pending.get(target);
        if (active == null || !active.equals(request)) {
            throw new IllegalStateException("Schema transition is not pending for this target");
        }
        SchemaManagerSnapshot.TableState previous = tables.get(target);
        long nextVersion = pendingVersions.get(target);
        Map<String, String> history = new LinkedHashMap<>(previous.appliedEventFingerprints());
        history.put(request.eventId(), fingerprint(request));
        while (history.size() > MAX_REPLAY_HISTORY) {
            history.remove(history.keySet().iterator().next());
        }
        tables.put(target, new SchemaManagerSnapshot.TableState(
                request.sourceTable(),
                target,
                request.sourceSchemaAfter(),
                request.targetSchemaAfter(),
                nextVersion,
                history));
        pending.remove(target);
        pendingVersions.remove(target);
        return nextVersion;
    }

    /** Aborts a pre-DDL plan. Once DDL starts, the owning Job must fail and recover from a checkpoint. */
    public synchronized void abort(SchemaChangeRequest request) {
        Objects.requireNonNull(request, "request");
        TableId target = request.targetTable();
        if (request.equals(pending.get(target))) {
            pending.remove(target);
            pendingVersions.remove(target);
        }
    }

    public synchronized long currentVersion(TableId target) {
        SchemaManagerSnapshot.TableState state = tables.get(Objects.requireNonNull(target, "target"));
        return state == null ? 0 : state.version();
    }

    public synchronized Optional<SchemaManagerSnapshot.TableState> state(TableId target) {
        return Optional.ofNullable(tables.get(Objects.requireNonNull(target, "target")));
    }

    /** A checkpoint may only include a committed version, never a partially applied DDL. */
    public synchronized SchemaManagerSnapshot snapshot() {
        if (!pending.isEmpty()) {
            throw new IllegalStateException("Cannot checkpoint while a schema change is in progress");
        }
        return new SchemaManagerSnapshot(tables.values().stream()
                .sorted(java.util.Comparator.comparing(table -> table.targetTable().toString()))
                .toList());
    }

    /** Replaces all state from a completed checkpoint before tasks are started. */
    public synchronized void restore(SchemaManagerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!pending.isEmpty()) {
            throw new IllegalStateException("Cannot restore SchemaManager with an active transition");
        }
        Map<TableId, SchemaManagerSnapshot.TableState> restored = new LinkedHashMap<>();
        for (SchemaManagerSnapshot.TableState state : snapshot.tables()) {
            if (restored.putIfAbsent(state.targetTable(), state) != null) {
                throw new IllegalArgumentException("Duplicate target in schema manager checkpoint");
            }
        }
        tables.clear();
        tables.putAll(restored);
    }

    private static String fingerprint(SchemaChangeRequest request) {
        StringBuilder value = new StringBuilder();
        value.append(request.sourceTable()).append('|').append(request.targetTable()).append('|');
        appendSchema(value, request.sourceSchemaBefore());
        appendSchema(value, request.sourceSchemaAfter());
        appendSchema(value, request.targetSchemaBefore());
        appendSchema(value, request.targetSchemaAfter());
        for (SchemaChangeEvent event : request.targetChanges()) {
            value.append(event.getType()).append('|').append(event.tableId()).append('|');
            if (event instanceof CreateTableEvent create) {
                appendSchema(value, create.getPhysicalSchema());
            } else if (event instanceof AddColumnEvent add) {
                for (AddColumnEvent.ColumnWithPosition addition : add.addedColumns()) {
                    appendColumn(value, addition.column());
                    value.append(addition.position()).append('|').append(addition.referenceColumn()).append('|');
                }
            } else if (event instanceof AlterColumnTypeEvent alter) {
                alter.typeMapping().keySet().stream().sorted().forEach(name -> value
                        .append(name).append(':')
                        .append(alter.oldTypeMapping().get(name) == null
                                ? ""
                                : alter.oldTypeMapping().get(name).asSerializableString())
                        .append("->")
                        .append(alter.typeMapping().get(name).asSerializableString())
                        .append('|'));
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendSchema(StringBuilder value, Schema schema) {
        value.append("schema{").append(schema.comment()).append('|');
        schema.columns().forEach(column -> appendColumn(value, column));
        value.append("keys=").append(schema.primaryKeys()).append('}');
    }

    private static void appendColumn(StringBuilder value, PhysicalColumn column) {
        value.append(column.name()).append(':').append(column.type().asSerializableString()).append(':')
                .append(column.comment()).append(':');
        ColumnProperties properties = column.properties();
        if (properties != null) {
            value.append(properties.defaultExpression()).append('|')
                    .append(properties.autoIncrement()).append('|')
                    .append(properties.generated()).append('|')
                    .append(properties.onUpdateExpression()).append('|')
                    .append(properties.defaultGenerated()).append('|')
                    .append(properties.nativeType());
        }
        value.append(';');
    }

    public record Change(boolean duplicate, long version) {
        public Change {
            if (version < 0) {
                throw new IllegalArgumentException("Schema version must not be negative");
            }
        }
    }
}
