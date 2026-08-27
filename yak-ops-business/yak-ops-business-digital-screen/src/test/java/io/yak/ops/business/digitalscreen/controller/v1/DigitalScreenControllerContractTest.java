package io.yak.ops.business.digitalscreen.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DigitalScreenControllerContractTest {

  @Test
  void baseRestPathRemainsStable() {
    RequestMapping mapping = DigitalScreenController.class.getAnnotation(RequestMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/digital-screens");
  }

  @Test
  void publishAndOfflineRoutesRemainExplicitLifecycleActions() throws Exception {
    Method publish = DigitalScreenController.class.getMethod("publish", long.class);
    Method offline = DigitalScreenController.class.getMethod("offline", long.class);
    assertThat(publish.getAnnotation(PostMapping.class).value())
        .containsExactly("/{screenId}/publish");
    assertThat(offline.getAnnotation(PostMapping.class).value())
        .containsExactly("/{screenId}/offline");
  }
}
