package io.yak.ops.plugin.task.datasync.plan;

import io.yak.ops.core.data.TableId;
import java.util.List;

/** Authorized read-only DDL preview. Statements are never accepted back as execution inputs. */
public record SchemaPreview(int mappingIndex, TableId target, String action, List<String> statements) {
    public SchemaPreview {
        statements = List.copyOf(statements);
    }

    @Override
    public String toString() {
        return "SchemaPreview{mappingIndex=" + mappingIndex + ", target=" + target + ", action=" + action + '}';
    }
}
