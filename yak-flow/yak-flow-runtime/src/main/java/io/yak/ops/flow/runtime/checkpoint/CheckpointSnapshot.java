package io.yak.ops.flow.runtime.checkpoint;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, aligned snapshot of one local job's Source, Reader and operator state.
 *
 * <p>Contains versioned serialized state only, never live readers, task threads,
 * connections or in-flight channel messages.
 */
public record CheckpointSnapshot(
        long checkpointId,
        String graphSignature,
        SerializedState enumeratorState,
        Map<Integer, List<SerializedState>> readerSplits,
        Map<Integer, List<SerializedState>> assignments,
        long completedAtMillis,
        Map<OperatorSubtask, Map<String, SerializedState>> operatorStates,
        Set<String> fullyFinishedOperators,
        Set<OperatorSubtask> finishedSubtasks,
        SerializedState schemaManagerState) {

    public CheckpointSnapshot {
        if (checkpointId <= 0 || completedAtMillis <= 0) {
            throw new IllegalArgumentException("Checkpoint ID and completion timestamp must be positive");
        }
        if (graphSignature == null || graphSignature.isBlank()) {
            throw new IllegalArgumentException("Checkpoint graph signature must not be blank");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState must not be null");
        readerSplits = freeze(readerSplits);
        assignments = freeze(assignments);
        Objects.requireNonNull(operatorStates, "operatorStates");
        Map<OperatorSubtask, Map<String, SerializedState>> copy = new LinkedHashMap<>();
        operatorStates.forEach((key, states) -> {
            Objects.requireNonNull(key, "OperatorSubtask");
            Objects.requireNonNull(states, "operator states");
            if (copy.putIfAbsent(key, Map.copyOf(states)) != null) {
                throw new IllegalArgumentException("Duplicate operator subtask");
            }
            states.keySet().forEach(name -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("State name must not be blank");
                }
            });
        });
        operatorStates = Map.copyOf(copy);
        Objects.requireNonNull(fullyFinishedOperators, "fullyFinishedOperators");
        for (String uid : fullyFinishedOperators) {
            if (uid == null || uid.isBlank()) {
                throw new IllegalArgumentException("Invalid fully finished operator UID");
            }
        }
        fullyFinishedOperators = Set.copyOf(fullyFinishedOperators);
        finishedSubtasks = Set.copyOf(Objects.requireNonNull(finishedSubtasks, "finishedSubtasks"));
        for (OperatorSubtask subtask : finishedSubtasks) {
            if (fullyFinishedOperators.contains(subtask.uid()) || operatorStates.containsKey(subtask)) {
                throw new IllegalArgumentException("Finished subtask conflicts with checkpoint state");
            }
        }
        for (OperatorSubtask subtask : operatorStates.keySet()) {
            if (fullyFinishedOperators.contains(subtask.uid())) {
                throw new IllegalArgumentException("Fully finished operator cannot contain subtask state");
            }
        }
    }

    /** Creates a snapshot from format v1 Source and Sink state, with empty operator state. */
    public CheckpointSnapshot(
            long checkpointId,
            String graphSignature,
            SerializedState enumeratorState,
            Map<Integer, List<SerializedState>> readerSplits,
            Map<Integer, List<SerializedState>> assignments,
            long completedAtMillis) {
        this(checkpointId, graphSignature, enumeratorState, readerSplits, assignments, completedAtMillis, Map.of());
    }

    /** Retains the original source/operator state constructor without inferred finish markers. */
    public CheckpointSnapshot(
            long checkpointId,
            String graphSignature,
            SerializedState enumeratorState,
            Map<Integer, List<SerializedState>> readerSplits,
            Map<Integer, List<SerializedState>> assignments,
            long completedAtMillis,
            Map<OperatorSubtask, Map<String, SerializedState>> operatorStates) {
        this(
                checkpointId,
                graphSignature,
                enumeratorState,
                readerSplits,
                assignments,
                completedAtMillis,
                operatorStates,
                Set.of(),
                Set.of(),
                null);
    }

    /** Compatibility constructor for format v3 checkpoints without runtime schema state. */
    public CheckpointSnapshot(
            long checkpointId,
            String graphSignature,
            SerializedState enumeratorState,
            Map<Integer, List<SerializedState>> readerSplits,
            Map<Integer, List<SerializedState>> assignments,
            long completedAtMillis,
            Map<OperatorSubtask, Map<String, SerializedState>> operatorStates,
            Set<String> fullyFinishedOperators,
            Set<OperatorSubtask> finishedSubtasks) {
        this(
                checkpointId,
                graphSignature,
                enumeratorState,
                readerSplits,
                assignments,
                completedAtMillis,
                operatorStates,
                fullyFinishedOperators,
                finishedSubtasks,
                null);
    }

    public boolean hasFinishedOperators() {
        return !fullyFinishedOperators.isEmpty() || !finishedSubtasks.isEmpty();
    }

    /** Stable logical operator identity, never a graph-local numeric operator ID. */
    public record OperatorSubtask(String uid, int subtaskIndex) {
        public OperatorSubtask {
            if (uid == null || uid.isBlank() || subtaskIndex < 0) {
                throw new IllegalArgumentException("Invalid checkpoint operator identity");
            }
        }
    }

    private static Map<Integer, List<SerializedState>> freeze(Map<Integer, List<SerializedState>> input) {
        Objects.requireNonNull(input, "Checkpoint subtask state must not be null");
        Map<Integer, List<SerializedState>> copy = new LinkedHashMap<>();
        input.forEach((index, splits) -> {
            if (index == null || index < 0) {
                throw new IllegalArgumentException("Invalid checkpoint subtask index");
            }
            copy.put(index, List.copyOf(splits));
        });
        return Map.copyOf(copy);
    }

    /** Versioned state bytes with immutable ownership and content-based value equality. */
    public static final class SerializedState {

        private final int version;
        private final byte[] bytes;

        /**
         * Captures serializer version and a detached copy of its encoded state.
         *
         * @param version nonnegative state serializer version
         * @param bytes serialized checkpoint data
         */
        public SerializedState(int version, byte[] bytes) {
            if (version < 0) {
                throw new IllegalArgumentException("State serializer version must not be negative");
            }
            this.version = version;
            this.bytes = Objects.requireNonNull(bytes, "Serialized state bytes must not be null")
                    .clone();
        }

        public int version() {
            return version;
        }

        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof SerializedState state
                            && version == state.version
                            && Arrays.equals(bytes, state.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(version) + Arrays.hashCode(bytes);
        }

        /** Exposes metadata without rendering checkpoint state bytes. */
        @Override
        public String toString() {
            return "SerializedState{version=" + version + ", bytes=redacted}";
        }
    }
}
