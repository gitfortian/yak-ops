package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import java.util.Objects;

/**
 * One unbounded MySQL Binlog subscription and its last mailbox-emitted offset.
 *
 * <p>Internal Debezium Schema History bytes are part of the checkpoint state. They are
 * restored together with the offset to avoid starting from an incompatible schema.
 * A null offset means the initial stream has not yet established a safe resume point.
 */
public final class MySqlBinlogSplit extends MySqlSplit {

    public static final String ID = "mysql-binlog";

    private final String definitionFingerprint;
    private final DebeziumOffset offset;
    private final byte[] schemaHistory;

    public MySqlBinlogSplit(String definitionFingerprint, DebeziumOffset offset, byte[] schemaHistory) {
        super(ID);
        this.definitionFingerprint = Objects.requireNonNull(definitionFingerprint, "definitionFingerprint");
        if (definitionFingerprint.isBlank()) {
            throw new IllegalArgumentException("MySQL source fingerprint must not be blank");
        }
        this.offset = offset;
        this.schemaHistory =
                Objects.requireNonNull(schemaHistory, "schemaHistory").clone();
    }

    @Override
    public String splitId() {
        return ID;
    }

    public String definitionFingerprint() {
        return definitionFingerprint;
    }

    public DebeziumOffset offset() {
        return offset;
    }

    public byte[] schemaHistory() {
        return schemaHistory.clone();
    }

    public MySqlBinlogSplit withProgress(DebeziumOffset position, byte[] history) {
        return new MySqlBinlogSplit(definitionFingerprint, Objects.requireNonNull(position, "position"), history);
    }
}
