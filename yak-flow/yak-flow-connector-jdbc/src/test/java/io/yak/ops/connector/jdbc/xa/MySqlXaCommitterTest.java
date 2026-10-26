package io.yak.ops.connector.jdbc.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.junit.jupiter.api.Test;

/** Guard against foreign transaction commits and ambiguous outcomes. */
class MySqlXaCommitterTest {

    @Test
    void commitsOnlyPreparedTransactionsForTheConfiguredInstance() throws Exception {
        AtomicInteger commits = new AtomicInteger();
        XAResource resource = resource(commits, false);
        XADataSource dataSource = dataSource(resource);
        var committer = new MySqlXaCommitter(dataSource, "instance-1");
        MySqlXaXid own = MySqlXaXid.next("instance-1", 0, 1);
        committer.commit(List.of(own), false);
        assertEquals(1, commits.get());

        MySqlXaXid foreign = MySqlXaXid.next("instance-2", 0, 1);
        assertThrows(IllegalArgumentException.class, () -> committer.commit(List.of(foreign), true));
        assertEquals(1, commits.get());
    }

    @Test
    void ambiguousCommitIsNotSilentlyAcknowledged() {
        var committer = new MySqlXaCommitter(dataSource(resource(new AtomicInteger(), true)), "instance-1");
        assertThrows(XAException.class, () -> committer.commit(
                List.of(MySqlXaXid.next("instance-1", 0, 8)), true));
    }

    @Test
    void recoveryReadsAllXaPagesBeforeRollingBackOnlyOwnedOrphans() throws Exception {
        MySqlXaXid retained = MySqlXaXid.next("instance-1", 0, 1);
        MySqlXaXid orphan = MySqlXaXid.next("instance-1", 0, 2);
        MySqlXaXid foreign = MySqlXaXid.next("instance-2", 0, 3);
        List<Integer> scanFlags = new ArrayList<>();
        List<Xid> rolledBack = new ArrayList<>();
        AtomicInteger pages = new AtomicInteger();
        XAResource resource = (XAResource) Proxy.newProxyInstance(
                XAResource.class.getClassLoader(),
                new Class<?>[] {XAResource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("recover")) {
                        int flags = (int) args[0];
                        scanFlags.add(flags);
                        if (flags == XAResource.TMSTARTRSCAN) return new Xid[] {retained};
                        if (flags == XAResource.TMNOFLAGS && pages.getAndIncrement() == 0) {
                            return new Xid[] {orphan, foreign};
                        }
                        return new Xid[0];
                    }
                    if (method.getName().equals("rollback")) rolledBack.add((Xid) args[0]);
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });

        new MySqlXaCommitter(dataSource(resource), "instance-1").recover(List.of(retained));

        assertEquals(
                List.of(
                        XAResource.TMSTARTRSCAN,
                        XAResource.TMNOFLAGS,
                        XAResource.TMNOFLAGS,
                        XAResource.TMENDRSCAN),
                scanFlags);
        assertEquals(List.of(orphan), rolledBack);
    }

    @Test
    void onlyRestoredTransactionsMayAcceptMissingXaIdentifier() throws Exception {
        XAResource resource = (XAResource) Proxy.newProxyInstance(
                XAResource.class.getClassLoader(),
                new Class<?>[] {XAResource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("commit")) {
                        throw new XAException(XAException.XAER_NOTA);
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType().isArray()) return new Xid[0];
                    return null;
                });
        var committer = new MySqlXaCommitter(dataSource(resource), "instance-1");
        MySqlXaXid xid = MySqlXaXid.next("instance-1", 0, 4);

        committer.commit(List.of(xid), true);
        XAException failure = assertThrows(XAException.class, () -> committer.commit(List.of(xid), false));
        assertEquals(XAException.XAER_NOTA, failure.errorCode);
    }

    private static XAResource resource(AtomicInteger commits, boolean fail) {
        return (XAResource) Proxy.newProxyInstance(
                XAResource.class.getClassLoader(),
                new Class<?>[] {XAResource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("commit")) {
                        if (fail) {
                            XAException error = new XAException(XAException.XAER_RMFAIL);
                            throw error;
                        }
                        commits.incrementAndGet();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType().isArray()) return new javax.transaction.xa.Xid[0];
                    return null;
                });
    }

    private static XADataSource dataSource(XAResource resource) {
        XAConnection connection = (XAConnection) Proxy.newProxyInstance(
                XAConnection.class.getClassLoader(),
                new Class<?>[] {XAConnection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getXAResource")) return resource;
                    return null;
                });
        return (XADataSource) Proxy.newProxyInstance(
                XADataSource.class.getClassLoader(),
                new Class<?>[] {XADataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getXAConnection")) return connection;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }
}
