package io.yak.ops.business.modeling.publication;

import java.util.List;

/**
 * Raised when a DDL statement failed; carries the engine failure for classification plus the
 * statements that completed before it, so the execution log can show exactly how far the script
 * got.
 */
public class PhysicalExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<PhysicalExecutor.StatementResult> completed;
    private final transient String failedStatement;

    /**
     * Wraps one engine failure raised before any statement was submitted.
     *
     * @param cause engine failure
     */
    public PhysicalExecutionException(Throwable cause) {
        this(cause, List.of(), null);
    }

    /**
     * Wraps one engine failure raised part-way through the script.
     *
     * @param cause engine failure
     * @param completed statements that finished before the failure
     * @param failedStatement statement the engine refused, null when the connection itself failed
     */
    public PhysicalExecutionException(
            Throwable cause, List<PhysicalExecutor.StatementResult> completed, String failedStatement) {
        super(cause);
        this.completed = completed == null ? List.of() : List.copyOf(completed);
        this.failedStatement = failedStatement;
    }

    /** Statements that finished before the failure, in order. */
    public List<PhysicalExecutor.StatementResult> completed() {
        return completed;
    }

    /** Statement the engine refused, null when the connection failed before one was submitted. */
    public String failedStatement() {
        return failedStatement;
    }
}
