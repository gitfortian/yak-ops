/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** A serialization util class for the {@link SourceCoordinator}. */
public class SourceCoordinatorSerdeUtils {

    public static final int VERSION_0 = 0;
    public static final int VERSION_1 = 1;

    /** The current source coordinator serde version. */
    private static final int CURRENT_VERSION = VERSION_1;

    /** Private constructor for utility class. */
    private SourceCoordinatorSerdeUtils() {}

    /** Write the current serde version. */
    static void writeCoordinatorSerdeVersion(DataOutputStream out) throws IOException {
        out.writeInt(CURRENT_VERSION);
    }

    /** Read and verify the serde version. */
    static int readAndVerifyCoordinatorSerdeVersion(DataInputStream in) throws IOException {
        int version = in.readInt();
        if (version < VERSION_0 || version > CURRENT_VERSION) {
            throw new IOException("Unsupported source coordinator serde version " + version);
        }
        return version;
    }

    static byte[] readBytes(DataInputStream in, int size) throws IOException {
        if (size < 0 || size > 32 * 1024 * 1024) {
            throw new IOException("Invalid Source Coordinator state length: " + size);
        }
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        return bytes;
    }

    static <SplitT> byte[] serializeAssignments(
            Map<Integer, LinkedHashSet<SplitT>> assignments,
            SimpleVersionedSerializer<SplitT> splitSerializer)
            throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(splitSerializer.getVersion());

            int numSubtasks = assignments.size();
            out.writeInt(numSubtasks);
            for (Map.Entry<Integer, LinkedHashSet<SplitT>> assignment : assignments.entrySet()) {
                int subtaskId = assignment.getKey();
                out.writeInt(subtaskId);

                int numAssignedSplits = assignment.getValue().size();
                out.writeInt(numAssignedSplits);
                for (SplitT split : assignment.getValue()) {
                    byte[] serializedSplit = splitSerializer.serialize(split);
                    out.writeInt(serializedSplit.length);
                    out.write(serializedSplit);
                }
            }
            out.flush();
            return baos.toByteArray();
        }
    }

    static <SplitT> Map<Integer, LinkedHashSet<SplitT>> deserializeAssignments(
            byte[] assignmentData, SimpleVersionedSerializer<SplitT> splitSerializer)
            throws IOException {

        try (ByteArrayInputStream bais = new ByteArrayInputStream(assignmentData);
                DataInputStream in = new DataInputStream(bais)) {
            int splitSerializerVersion = in.readInt();

            int numSubtasks = in.readInt();
            if (numSubtasks < 0 || numSubtasks > 100_000) {
                throw new IOException("Invalid Source Coordinator subtask count: " + numSubtasks);
            }
            Map<Integer, LinkedHashSet<SplitT>> assignments = new HashMap<>();
            for (int j = 0; j < numSubtasks; j++) {
                int subtaskId = in.readInt();
                int numAssignedSplits = in.readInt();
                if (subtaskId < 0 || numAssignedSplits < 0 || numAssignedSplits > 100_000
                        || assignments.containsKey(subtaskId)) {
                    throw new IOException("Invalid Source Coordinator split assignment section");
                }
                LinkedHashSet<SplitT> splits = new LinkedHashSet<>(numAssignedSplits);
                assignments.put(subtaskId, splits);
                for (int k = 0; k < numAssignedSplits; k++) {
                    int serializedSplitSize = in.readInt();
                    byte[] serializedSplit = readBytes(in, serializedSplitSize);
                    SplitT split =
                            splitSerializer.deserialize(splitSerializerVersion, serializedSplit);
                    if (!splits.add(split)) {
                        throw new IOException("Duplicate Source Coordinator split in checkpoint");
                    }
                }
            }

            if (in.available() != 0) {
                throw new IOException("Unexpected trailing bytes in Source Coordinator split assignments");
            }
            return assignments;
        }
    }
}
