package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.event.SchemaChangeRequest;

/** Optional Writer capability required before runtime schema evolution can be enabled. */
public interface SchemaChangeAwareSinkWriter<T> {

    /** Validates that this Writer can adopt the planned schema without mutating live state. */
    void validateSchemaChange(SchemaChangeRequest request) throws Exception;

    /**
     * Replaces the Writer's immutable write plan after the target DDL has been applied and verified.
     *
     * @param request committed source/target schema transition
     * @param schemaVersion monotonically increasing target schema version
     */
    void onSchemaChange(SchemaChangeRequest request, long schemaVersion) throws Exception;
}
