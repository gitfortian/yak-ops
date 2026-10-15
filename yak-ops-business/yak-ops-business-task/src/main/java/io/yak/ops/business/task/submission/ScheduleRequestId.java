package io.yak.ops.business.task.submission;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Builds stable request identities from unambiguously delimited logical occurrence fields. */
public final class ScheduleRequestId {
    private ScheduleRequestId() {}

    /**
     * Computes a request key without actual callback time or the current Definition version.
     * @param workspaceId owning Workspace
     * @param scheduleId originating schedule
     * @param revision originating registration revision
     * @param scheduledTime logical UTC occurrence
     * @return stable key within the Task SCHEDULE namespace
     */
    public static String of(String workspaceId, String scheduleId, long revision, Instant scheduledTime) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : new String[] {
                workspaceId, scheduleId, Long.toString(revision), Long.toString(scheduledTime.toEpochMilli())
            }) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(
                        ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return "schedule_" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is not available", failure);
        }
    }
}
