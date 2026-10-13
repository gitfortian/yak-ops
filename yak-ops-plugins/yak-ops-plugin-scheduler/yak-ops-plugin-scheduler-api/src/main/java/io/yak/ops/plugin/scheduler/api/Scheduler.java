package io.yak.ops.plugin.scheduler.api;

import java.util.List;
import java.util.Optional;

/** Application-owned time scheduler; initialization requires verified process ownership. */
public interface Scheduler extends AutoCloseable {
    /** Opens the persistent store in standby after ownership and schema verification. */
    void initialize();
    /** Starts delivering time notifications after initial reconciliation. */
    void start();
    /** Stops acquiring triggers without interrupting already delivered notifications. */
    void standby();
    /**
     * Applies one registration without resetting an identical trigger.
     * @param registration immutable identity, revision, and time rule
     */
    void register(ScheduleRegistration registration);
    /**
     * Removes only this application's registration for the specified schedule.
     * @param workspaceId owning Workspace
     * @param scheduleId schedule identity
     */
    void remove(String workspaceId, String scheduleId);
    /**
     * Reads one engine registration without modifying its next-fire state.
     * @param workspaceId owning Workspace
     * @param scheduleId schedule identity
     * @return registration when present
     */
    Optional<RegisteredSchedule> registration(String workspaceId, String scheduleId);
    /** @return immutable observations of this application's registrations */
    List<RegisteredSchedule> registrations();
    /** Drains notification jobs and releases engine-owned resources. */
    @Override
    void close();
}
