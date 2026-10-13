package io.yak.ops.connector.base.factory;

import java.util.Objects;

/** A deterministic exclusive claim declared by a Connector before job admission. */
public record ConnectorResourceClaim(String type, String key) {

    public ConnectorResourceClaim {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
    }
}
