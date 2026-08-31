package io.yak.ops.boot.home;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeProjectScopeContractTest {

  @Test
  void shouldKeepEveryHomeEndpointProjectRequired() {
    for (Class<?> controller : List.of(
        HomeCockpitController.class,
        HomeDataCenterController.class,
        HomeAssetOverviewController.class,
        HomeQualityOverviewController.class,
        HomeScheduleCenterController.class)) {
      ProjectScope scope = controller.getAnnotation(ProjectScope.class);

      assertThat(scope)
          .as("%s must participate in Project Space", controller.getSimpleName())
          .isNotNull();
      assertThat(scope.value())
          .as("%s must require the current Project", controller.getSimpleName())
          .isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
    }
  }
}
