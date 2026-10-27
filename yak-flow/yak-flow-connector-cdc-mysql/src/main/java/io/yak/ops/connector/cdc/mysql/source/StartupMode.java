package io.yak.ops.connector.cdc.mysql.source;

/** Selects how a new MySQL CDC Source starts; completed checkpoints restore their saved position. */
public enum StartupMode {
    INITIAL("initial"),
    LATEST_OFFSET("latest-offset");

    private final String value;

    StartupMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static StartupMode from(String value) {
        for (StartupMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported MySQL CDC startup mode: " + value);
    }
}
