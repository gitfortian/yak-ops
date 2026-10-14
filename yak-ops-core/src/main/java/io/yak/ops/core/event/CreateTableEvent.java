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
 * Modified for Yak Ops: existing TableId/TableSchema, immutable comment
 * metadata, constructor validation and comment-free diagnostics.
 */
package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Requests preparation of a table with an explicit logical schema.
 *
 * <p>The target schema must be applied and verified before rows using it are written.
 * Comment values are optional metadata, not executable SQL.
 */
public class CreateTableEvent implements SchemaChangeEvent {

    private static final long serialVersionUID = 1L;

    private final TableId tableId;
    private final TableSchema schema;
    private final String tableComment;
    private final Map<String, String> columnComments;

    /**
     * Creates an event without comment metadata.
     *
     * @param tableId target identity
     * @param schema complete expected target schema
     */
    public CreateTableEvent(TableId tableId, TableSchema schema) {
        this(tableId, schema, null, Map.of());
    }

    /**
     * Creates an event with detached, column-scoped comments.
     *
     * @param tableId target identity
     * @param schema complete expected target schema
     * @param tableComment optional table comment
     * @param columnComments non-null comments keyed by exact column name
     */
    public CreateTableEvent(
            TableId tableId, TableSchema schema, String tableComment, Map<String, String> columnComments) {
        this.tableId = Objects.requireNonNull(tableId, "tableId");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.tableComment = tableComment;
        this.columnComments = Map.copyOf(Objects.requireNonNull(columnComments, "columnComments"));
        Set<String> columns =
                schema.columns().stream().map(column -> column.name()).collect(Collectors.toSet());
        if (!columns.containsAll(this.columnComments.keySet())) {
            throw new IllegalArgumentException("Column comments must reference declared columns");
        }
    }

    /** Returns the complete expected target schema. */
    public TableSchema getSchema() {
        return schema;
    }

    /** Returns the optional table comment. */
    public String getTableComment() {
        return tableComment;
    }

    /** Returns immutable comments keyed by exact column name. */
    public Map<String, String> getColumnComments() {
        return columnComments;
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.CREATE_TABLE;
    }

    @Override
    public CreateTableEvent copy(TableId newTableId) {
        return new CreateTableEvent(newTableId, schema, tableComment, columnComments);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CreateTableEvent that)) {
            return false;
        }
        return Objects.equals(tableId, that.tableId)
                && Objects.equals(schema, that.schema)
                && Objects.equals(tableComment, that.tableComment)
                && Objects.equals(columnComments, that.columnComments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, schema, tableComment, columnComments);
    }

    @Override
    public String toString() {
        return "CreateTableEvent{tableId=" + tableId + ", columnCount=" + schema.columnCount() + '}';
    }
}
