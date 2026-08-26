package io.yak.ops.boot.project;

import io.yak.framework.security.common.dto.project.ProjectSaveDTO;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.UserService;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Coordinates the opt-in default Project Space used by later Expand/Backfill/Contract migrations.
 *
 * <p>The bootstrap is disabled by default. Merely adding PR2 therefore never mutates Yak Security
 * data and never injects a default project into legacy global queries.</p>
 */
@Component
public class ProjectCompatibilityCoordinator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProjectCompatibilityCoordinator.class);

  private final ProjectSpaceProperties properties;
  private final ProjectService projectService;
  private final UserService userService;

  public ProjectCompatibilityCoordinator(
      ProjectSpaceProperties properties,
      ProjectService projectService,
      UserService userService) {
    this.properties = properties;
    this.projectService = projectService;
    this.userService = userService;
  }

  /**
   * Runs only after Spring Boot runners have completed, so Yak Security's optional administrator
   * bootstrap has already had a chance to create the configured owner.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void bootstrapDefaultProjectAfterStartup() {
    if (!properties.getCompatibility().isBootstrapDefaultProject()) {
      return;
    }
    long projectId = ensureDefaultProject();
    LOGGER.info("Project Space compatibility default project is ready: projectId={}", projectId);
  }

  /** Returns the configured compatibility project when it already exists. */
  public OptionalLong findDefaultProjectId() {
    String name = normalizedProjectName();
    return projectService.getProjectBriefList().stream()
        .filter(project -> name.equals(project.getProjectName()))
        .map(ProjectBriefVO::getId)
        .filter(id -> id != null && id > 0)
        .mapToLong(Long::longValue)
        .findFirst();
  }

  /** Creates the compatibility project only when the explicit bootstrap switch is enabled. */
  public synchronized long ensureDefaultProject() {
    OptionalLong existing = findDefaultProjectId();
    if (existing.isPresent()) {
      return existing.getAsLong();
    }
    if (!properties.getCompatibility().isBootstrapDefaultProject()) {
      throw new IllegalStateException(
          "Default Project Space bootstrap is disabled; enable yak.project-space.compatibility.bootstrap-default-project explicitly");
    }

    String ownerUsername = properties.getCompatibility().getDefaultOwnerUsername();
    UserBriefVO owner = userService.getUserBriefByUsername(ownerUsername);
    if (owner == null || owner.getId() == null) {
      throw new IllegalStateException("Default Project Space owner does not exist: " + ownerUsername);
    }

    List<UserBriefVO> users = userService.getAllUserBriefList();
    List<Long> userIds = (users == null ? Collections.<UserBriefVO>emptyList() : users).stream()
        .map(UserBriefVO::getId)
        .filter(id -> id != null && id > 0)
        .distinct()
        .toList();

    ProjectSaveDTO input = new ProjectSaveDTO();
    input.setProjectName(normalizedProjectName());
    input.setDescription("Yak Ops Project Space compatibility default project");
    input.setRunning(Boolean.TRUE);
    input.setOwnerIdList(Collections.singletonList(owner.getId()));
    input.setUserIdList(userIds);

    try {
      ProjectVO created = projectService.createProject(input, ownerUsername);
      return created.getId();
    } catch (RuntimeException exception) {
      // Multi-instance startup can race on project creation. Re-read once before surfacing the error.
      OptionalLong concurrent = findDefaultProjectId();
      if (concurrent.isPresent()) {
        return concurrent.getAsLong();
      }
      throw exception;
    }
  }

  private String normalizedProjectName() {
    String name = properties.getCompatibility().getDefaultProjectName();
    if (!StringUtils.hasText(name)) {
      throw new IllegalStateException("Default Project Space name must not be blank");
    }
    return name.trim();
  }
}
