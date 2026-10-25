package io.yak.ops.business.modeling.publication;

import java.util.List;

/**
 * Generated publication script.
 *
 * <p>{@code statements} is what the executor runs in order; {@code script} is the same content as one
 * text, persisted with the publication so the history shows exactly what was attempted.
 *
 * @param statements ordered statements, executed in auto-commit mode
 * @param script the statements joined into one readable script
 */
public record PhysicalScript(List<String> statements, String script) {}
