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
 * Modified for Yak Ops: package and existing TableId value contract.
 */
package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import java.io.Serializable;

/** A structured change to the schema of an external table. */
public interface SchemaChangeEvent extends ChangeEvent, Serializable {

    /** Returns the schema operation represented by this event. */
    SchemaChangeEventType getType();

    /**
     * Copies the event for another table without modifying its schema.
     *
     * @param newTableId target table identity
     * @return an independent event with the same schema semantics
     */
    SchemaChangeEvent copy(TableId newTableId);
}
