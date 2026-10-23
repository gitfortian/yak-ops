package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Objects;

/** Read-only DDL and physical schema differences, never accepted as execution input. */
public record SchemaPreview(
        int mappingIndex, TableId target, String action, List<String> statements, List<Difference> differences) {
    public SchemaPreview {
        target = Objects.requireNonNull(target, "target");
        statements = List.copyOf(statements);
        differences = List.copyOf(differences);
    }

    /** Compatibility for older callers expecting SQL-only previews. */
    public SchemaPreview(int mappingIndex, TableId target, String action, List<String> statements) {
        this(mappingIndex, target, action, statements, List.of());
    }

    /** Fixed diagnostic codes and normalized types; no source defaults or raw expressions. */
    public record Difference(String column, String action, String reason, String sourceType, String targetType) {}

    @Override
    public String toString() {
        return "SchemaPreview{mappingIndex=" + mappingIndex + ", target=" + target + ", action=" + action + '}';
    }
}
