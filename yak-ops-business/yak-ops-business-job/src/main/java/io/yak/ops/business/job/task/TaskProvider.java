package io.yak.ops.business.job.task;

import java.util.List;

/** Supplies workflow-visible tasks without making the Job module depend on concrete business domains. */
public interface TaskProvider {

  List<TaskDefinition> list();

  TaskVersionSnapshot snapshot(String taskId);
}
