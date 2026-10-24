package io.yak.ops.business.modeling.publication;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;

/**
 * Decides whether a failed execution is a definite failure or an unknown outcome.
 *
 * <p>The distinction matters because the two lead to different product behaviour: a definite failure
 * is final and shown to the user, while an unknown outcome has to be reconciled by re-reading the
 * target, since retrying blindly could create the table twice.
 *
 * <p>Connection-class failures ({@code SQLState} {@code 08xxx}, transient connection, statement
 * timeout) are unknown: the statement may or may not have reached the engine. Everything else — a
 * syntax error, a permission denial, a duplicate name — is the engine refusing the statement, which
 * is a definite failure.
 */
public final class PublicationFailures {

    /** What the caller may conclude from a failure. */
    public enum Outcome {
        /** The engine refused the statement; nothing was created. */
        FAILED,
        /** The result could not be determined. */
        UNKNOWN
    }

    private PublicationFailures() {}

    /**
     * Classifies one failure.
     *
     * @param throwable failure raised while contacting or querying the engine
     * @return the outcome to record
     */
    public static Outcome classify(Throwable throwable) {
        if (!(throwable instanceof SQLException sql)) {
            // Raised before any statement reached the engine, or after it already returned.
            return Outcome.FAILED;
        }
        if (sql instanceof SQLTimeoutException || sql instanceof SQLTransientConnectionException) {
            return Outcome.UNKNOWN;
        }
        var state = sql.getSQLState();
        return state != null && state.startsWith("08") ? Outcome.UNKNOWN : Outcome.FAILED;
    }

    /**
     * Builds the persisted error detail.
     *
     * @param throwable failure to describe
     * @return bounded, single-line description
     */
    public static String describe(Throwable throwable) {
        var message = throwable.getMessage();
        var text = throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}
