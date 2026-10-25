package io.yak.ops.core.data;

import java.util.Objects;

/**
 * Stable, source-scoped transport identity for one CDC change image.
 *
 * <p>Images in one UPDATE share the same eventId and imageCount; imageIndex
 * identifies their order. The eventId identifies an event, not a physical
 * database row or a business key. It must never enter RowData or JDBC columns.
 */
public record ChangeEventId(String sourceId, String eventId, int imageIndex, int imageCount) {
    public ChangeEventId {
        if (sourceId == null || sourceId.isBlank() || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("CDC event identity requires a source and event");
        }
        if (imageCount < 1 || imageIndex < 0 || imageIndex >= imageCount) {
            throw new IllegalArgumentException("Invalid CDC event image sequence");
        }
    }

    public static ChangeEventId snapshot(String sourceId, String splitId, long ordinal) {
        Objects.requireNonNull(splitId, "splitId");
        if (splitId.isBlank() || ordinal < 0) {
            throw new IllegalArgumentException("Snapshot event requires a split and nonnegative row ordinal");
        }
        return new ChangeEventId(sourceId, "snapshot:" + splitId + ":" + ordinal, 0, 1);
    }

    /** Both UPDATE images have the same source and event ID, but different indices. */
    public boolean sameEvent(ChangeEventId other) {
        return other != null && sourceId.equals(other.sourceId) && eventId.equals(other.eventId);
    }

    @Override
    public String toString() {
        return "ChangeEventId{identity=redacted, image=" + imageIndex + "/" + imageCount + "}";
    }
}
