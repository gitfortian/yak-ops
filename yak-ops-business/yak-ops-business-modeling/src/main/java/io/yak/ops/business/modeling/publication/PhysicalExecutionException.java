package io.yak.ops.business.modeling.publication;

/** Raised when a DDL statement failed; carries the engine failure for classification. */
public class PhysicalExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Wraps one engine failure.
     *
     * @param cause engine failure
     */
    public PhysicalExecutionException(Throwable cause) {
        super(cause);
    }
}
