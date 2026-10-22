package io.yak.ops.testutils;

import java.util.UUID;

/** SQL-safe names that remain below the identifier limits of the supported databases. */
public final class TestNames {
    private TestNames() {}

    public static String unique(String prefix) {
        if (!prefix.matches("[a-z][a-z0-9_]{0,15}")) {
            throw new IllegalArgumentException("Use a short lower-case SQL identifier prefix");
        }
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
