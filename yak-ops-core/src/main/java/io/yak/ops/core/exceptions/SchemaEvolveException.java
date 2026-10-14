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
 * Modified for Yak Ops: RuntimeException base, standard message/cause
 * ownership, and no event or cause expansion in toString().
 */
package io.yak.ops.core.exceptions;

import io.yak.ops.core.event.SchemaChangeEvent;
import java.util.Objects;

/** A schema operation failure retaining the applying event and original cause. */
public class SchemaEvolveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final SchemaChangeEvent applyingEvent;

    /**
     * Creates a schema failure without an underlying exception.
     *
     * @param applyingEvent failed schema operation
     * @param exceptionMessage safe diagnostic description
     */
    public SchemaEvolveException(SchemaChangeEvent applyingEvent, String exceptionMessage) {
        this(applyingEvent, exceptionMessage, null);
    }

    /**
     * Creates a schema failure preserving the original exception.
     *
     * @param applyingEvent failed schema operation
     * @param exceptionMessage safe diagnostic description
     * @param cause original failure, or null
     */
    public SchemaEvolveException(SchemaChangeEvent applyingEvent, String exceptionMessage, Throwable cause) {
        super(exceptionMessage, cause);
        this.applyingEvent = Objects.requireNonNull(applyingEvent, "applyingEvent");
    }

    /** Returns the schema operation associated with the failure. */
    public SchemaChangeEvent getApplyingEvent() {
        return applyingEvent;
    }

    /** Returns the diagnostic description without expanding the cause. */
    public String getExceptionMessage() {
        return getMessage();
    }
}
