package io.yak.ops.plugin.task.api;

/** Safe, structured publication validation failure without raw SQL or datasource secrets. */
public final class TaskValidationException extends TaskPluginException {
    private final String stage;
    private final String publicMessage;

    public TaskValidationException(String stage, String publicMessage, Throwable cause) {
        super("Task " + stage + " validation failed", cause);
        this.stage = stage;
        this.publicMessage = publicMessage;
    }

    public String stage() {
        return stage;
    }

    public String publicMessage() {
        return publicMessage;
    }
}
