package io.yak.ops.plugin.scheduler.api;

/** Receives a time notification and returns after durable submission resolution, not Task completion. */
@FunctionalInterface
public interface ScheduleTriggerListener {
    /**
     * Resolves an observed time occurrence through the business submission boundary.
     * @param trigger immutable notification copied from the fired registration
     */
    void onTrigger(ScheduleTrigger trigger);
}
