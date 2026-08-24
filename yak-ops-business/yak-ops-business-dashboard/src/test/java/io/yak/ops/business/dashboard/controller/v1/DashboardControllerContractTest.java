package io.yak.ops.business.dashboard.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DashboardControllerContractTest {

  @Test
  void baseRestPathRemainsStable() {
    RequestMapping mapping = DashboardController.class.getAnnotation(RequestMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/dashboards");
  }

  @Test
  void deprecatedActivateRouteRemainsCompatibilityAlias() throws Exception {
    Method method = DashboardController.class.getMethod("activateVersion", long.class, int.class);
    assertThat(method.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(method.getAnnotation(PostMapping.class).value())
        .containsExactly("/{dashboardId}/activate/{versionNo}");
  }
}
