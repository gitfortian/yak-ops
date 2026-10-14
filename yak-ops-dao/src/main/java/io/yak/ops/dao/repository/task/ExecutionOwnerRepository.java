package io.yak.ops.dao.repository.task;

/** Records the process holding the durable embedded Task execution owner identity. */
public interface ExecutionOwnerRepository {

    /**
     * Holds shared ownership evidence until scheduled admission commits or rolls back.
     * @param incarnation verified local process identity
     * @return whether the database still names this owner
     */
    boolean isCurrentForAdmission(String incarnation);

    /**
     * Verifies the registered host and state directory while locking the owner row in a transaction.
     *
     * @param hostId current host identity
     * @param stateRoot canonical local state directory protected by the process lock
     * @param incarnation identity of the current owning process
     */
    void register(String hostId, String stateRoot, String incarnation);
}
