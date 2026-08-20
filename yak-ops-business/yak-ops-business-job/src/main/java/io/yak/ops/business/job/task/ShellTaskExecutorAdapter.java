package io.yak.ops.business.job.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.spi.resource.ResourceResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Executes Shell revisions through the shared Task Runtime and TaskPlugin contract. */
@Service
public class ShellTaskExecutorAdapter extends AbstractTaskExecutorAdapter {

  private static final String TYPE = "SHELL";

  @Autowired
  public ShellTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<ResourceResolver> resourceResolverProvider,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    super(pluginRegistry, resourceResolverProvider, objectMapper, contextFactory);
  }

  @Override
  public String taskType() {
    return TYPE;
  }

  @Override
  protected String executionIdPrefix() {
    return "shell";
  }

  @Override
  protected String displayName() {
    return "Shell";
  }
}
