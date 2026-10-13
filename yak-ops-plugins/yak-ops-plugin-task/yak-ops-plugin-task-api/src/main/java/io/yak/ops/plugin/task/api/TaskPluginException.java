package io.yak.ops.plugin.task.api;

/**
 * Reports Task plugin loading or parameter-validation failures without exposing raw parameters or credentials.
 */
public class TaskPluginException extends RuntimeException {

    public TaskPluginException(String message) {
        super(message);
    }

    public TaskPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
