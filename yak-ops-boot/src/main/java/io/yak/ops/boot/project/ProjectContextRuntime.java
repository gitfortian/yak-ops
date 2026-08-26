package io.yak.ops.boot.project;

import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Request-thread implementation of {@link CurrentProject}. */
@Component
public class ProjectContextRuntime implements CurrentProject {

  private final ThreadLocal<ProjectContext> holder = new ThreadLocal<>();

  @Override
  public Optional<ProjectContext> current() {
    return Optional.ofNullable(holder.get());
  }

  void bind(ProjectContext context) {
    holder.set(context);
  }

  void clear() {
    holder.remove();
  }
}
