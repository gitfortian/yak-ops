package io.yak.ops.connector.jdbc.xa;

import io.yak.ops.core.api.connector.sink.Committer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * Transactional MySQL XA committer. Only XIDs scoped to the configured persistent instance
 * may be committed. No retry of ambiguous XA results is performed by this component.
 *
 * <p>Only a recovered committable from a durable checkpoint may treat XAER_NOTA as
 * an already acknowledged completion, matching Flink JDBC's restored-transaction boundary.
 * A fresh missing XID is always a hard failure.
 */
public final class MySqlXaCommitter implements Committer<MySqlXaXid> {

    private static final int MAX_RECOVER_CALLS = 64;
    private static final int MAX_RECOVERED_TRANSACTIONS = 100_000;

    private final XADataSource dataSource;
    private final String instanceId;

    public MySqlXaCommitter(XADataSource dataSource, String instanceId) {
        this.dataSource = Objects.requireNonNull(dataSource, "XA datasource");
        if (instanceId == null || !instanceId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Invalid persistent XA instance identity");
        }
        this.instanceId = instanceId;
    }

    @Override
    public void commit(Collection<MySqlXaXid> committables, boolean recovered) throws SQLException, XAException {
        Objects.requireNonNull(committables, "committables");
        for (MySqlXaXid xid : committables) {
            if (xid == null || !xid.belongsTo(instanceId)) {
                throw new IllegalArgumentException("XA committable does not belong to this task instance");
            }
        }
        if (committables.isEmpty()) return;
        XAConnection connection = dataSource.getXAConnection();
        Throwable failure = null;
        try {
            XAResource resource = connection.getXAResource();
            for (MySqlXaXid xid : committables) {
                // Prepared transactions may outlive the writer connection on supported MySQL.
                try {
                    resource.commit(xid, false);
                } catch (XAException commitError) {
                    if (!recovered || commitError.errorCode != XAException.XAER_NOTA) {
                        throw commitError;
                    }
                }
            }
        } catch (SQLException | XAException | RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            try {
                connection.close();
            } catch (SQLException closeError) {
                if (failure != null) failure.addSuppressed(closeError);
                else throw closeError;
            }
        }
    }

    @Override
    public void recover(Collection<MySqlXaXid> keep) throws SQLException, XAException {
        Objects.requireNonNull(keep, "restored committables");
        for (MySqlXaXid xid : keep) {
            if (xid == null || !xid.belongsTo(instanceId)) {
                throw new IllegalArgumentException("Recovered XA state belongs to a different task");
            }
        }
        XAConnection connection = dataSource.getXAConnection();
        Throwable failure = null;
        try {
            XAResource resource = connection.getXAResource();
            java.util.Set<MySqlXaXid> expected = java.util.Set.copyOf(keep);
            for (Xid foreign : scanPreparedTransactions(resource)) {
                if (foreign.getFormatId() != MySqlXaXid.FORMAT_ID) continue;
                byte[] global = foreign.getGlobalTransactionId();
                if (!new String(global, java.nio.charset.StandardCharsets.US_ASCII).startsWith("yakflow-xa:")) {
                    continue;
                }
                MySqlXaXid candidate = new MySqlXaXid(global, foreign.getBranchQualifier());
                if (candidate.belongsTo(instanceId) && !expected.contains(candidate)) {
                    // Never touch prepared transactions of another persistent task instance.
                    resource.rollback(candidate);
                }
            }
        } catch (SQLException | XAException | RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            try {
                connection.close();
            } catch (SQLException closeError) {
                if (failure != null) failure.addSuppressed(closeError);
                else throw closeError;
            }
        }
    }

    /**
     * Completes the XA recovery cursor before applying rollback decisions.
     *
     * <p>Connector/J normally returns all prepared XIDs on TMSTARTRSCAN. Other compliant
     * resources may return additional pages. Bound both the calls and results so a broken
     * resource cannot silently return a partial transaction set or loop forever.
     */
    private static List<Xid> scanPreparedTransactions(XAResource resource) throws XAException {
        List<Xid> found = new ArrayList<>();
        Xid[] batch = resource.recover(XAResource.TMSTARTRSCAN);
        int calls = 0;
        try {
            while (true) {
                if (batch == null) {
                    throw new IllegalStateException("MySQL XA recovery returned a null transaction batch");
                }
                if (batch.length > MAX_RECOVERED_TRANSACTIONS - found.size()) {
                    throw new IllegalStateException("MySQL XA recovery scan exceeds safety limit");
                }
                java.util.Collections.addAll(found, batch);
                if (batch.length == 0) {
                    return found;
                }
                if (++calls > MAX_RECOVER_CALLS) {
                    throw new IllegalStateException("MySQL XA recovery scan did not finish");
                }
                batch = resource.recover(XAResource.TMNOFLAGS);
            }
        } finally {
            resource.recover(XAResource.TMENDRSCAN);
        }
    }

    @Override
    public void close() {
        // Each commit acquires and closes an independent XAConnection.
    }
}
