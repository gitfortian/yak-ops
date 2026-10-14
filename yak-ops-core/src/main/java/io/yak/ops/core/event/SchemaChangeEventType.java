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
 * Modified for Yak Ops: package; resolve events through their declared type
 * without importing unimplemented event classes.
 */
package io.yak.ops.core.event;

import java.util.Objects;

/** Schema operation identifiers; an identifier does not imply connector support. */
public enum SchemaChangeEventType {
    ADD_COLUMN("add.column"),
    ALTER_COLUMN_TYPE("alter.column.type"),
    CREATE_TABLE("create.table"),
    DROP_COLUMN("drop.column"),
    DROP_TABLE("drop.table"),
    RENAME_COLUMN("rename.column"),
    TRUNCATE_TABLE("truncate.table"),
    ALTER_TABLE_COMMENT("alter.table.comment");

    private final String tag;

    SchemaChangeEventType(String tag) {
        this.tag = tag;
    }

    /** Returns the stable external operation tag. */
    public String getTag() {
        return tag;
    }

    /**
     * Resolves an event's declared type.
     *
     * @param event schema event
     * @return its non-null operation type
     */
    public static SchemaChangeEventType ofEvent(SchemaChangeEvent event) {
        return Objects.requireNonNull(Objects.requireNonNull(event, "event").getType(), "event type");
    }

    /**
     * Resolves a stable external tag, rejecting unknown operations.
     *
     * @param tag schema operation tag
     * @return the matching operation type
     */
    public static SchemaChangeEventType ofTag(String tag) {
        for (SchemaChangeEventType type : values()) {
            if (type.tag.equals(tag)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown schema change event type: " + tag);
    }
}
