package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.schedule.ScheduleFireReason;
import io.yak.ops.plugin.scheduler.api.ScheduleTrigger;

/** Owns atomic admission of one authorized Task execution request. */
public interface AdmissionService {

    /**
     * Pins a published Definition version, persists its Instance, and acquires all
     * exclusive resource claims in one transaction before physical execution.
     *
     * @param request validated Task identity, parameters and read-only execution plan
     * @return newly admitted run or the original idempotent submission
     */
    SubmissionResult admit(AdmissionRequest request);

    /**
     * Serializes final schedule guards, terminal occurrence evidence, and Task admission.
     * @param trigger registered occurrence under a held owner-gate permit
     * @param request prepared input, absent when preparation was rejected or replay was observed
     * @param preparationFailure safe known pre-admission failure, or null
     * @return durable final occurrence result, including terminal replays
     */
    ScheduleFireResult admitScheduled(
            ScheduleTrigger trigger, AdmissionRequest request, ScheduleFireReason preparationFailure);
}
