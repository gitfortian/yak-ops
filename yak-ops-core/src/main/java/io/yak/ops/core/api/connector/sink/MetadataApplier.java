/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Yak Ops: package, fail-closed default capabilities, and
 * omission of the experimental existing-table expansion SPI, and best-effort cancellation.
 */
package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Applies structured metadata changes to an external target independently of row writes. */
public interface MetadataApplier extends Serializable, AutoCloseable {

    /**
     * Applies and verifies a supported schema operation.
     *
     * @param schemaChangeEvent operation with the expected target schema
     * @throws SchemaEvolveException when the operation is rejected or cannot be verified
     */
    void applySchemaChange(SchemaChangeEvent schemaChangeEvent) throws SchemaEvolveException;

    /**
     * Configures accepted operations. Implementations with filtering support override this method.
     *
     * @param schemaEvolutionTypes enabled operation types
     * @return this applier
     */
    default MetadataApplier setAcceptedSchemaEvolutionTypes(Set<SchemaChangeEventType> schemaEvolutionTypes) {
        if (!getSupportedSchemaEvolutionTypes().equals(Objects.requireNonNull(schemaEvolutionTypes))) {
            throw new UnsupportedOperationException("Metadata applier does not support configurable event filtering");
        }
        return this;
    }

    /**
     * Checks whether an operation is enabled.
     *
     * @param schemaChangeEventType operation type
     * @return true only for explicitly supported operations
     */
    default boolean acceptsSchemaEvolutionType(SchemaChangeEventType schemaChangeEventType) {
        return schemaChangeEventType != null
                && getSupportedSchemaEvolutionTypes().contains(schemaChangeEventType);
    }

    /** Returns explicitly supported operations; implementations declare their capabilities. */
    default Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
        return Set.of();
    }

    /**
     * Requests cancellation of active metadata work when the implementation supports it.
     *
     * <p>This method does not prove that the external operation stopped. The owner must await
     * applySchemaChange returning before closing resources or releasing target ownership.
     *
     * @throws Exception if the underlying system rejects the cancellation request
     */
    default void cancel() throws Exception {}

    /** Releases resources owned by this applier. */
    @Override
    default void close() throws Exception {}
}
