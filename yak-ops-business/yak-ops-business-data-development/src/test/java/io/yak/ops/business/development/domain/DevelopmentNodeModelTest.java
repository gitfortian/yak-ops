package io.yak.ops.business.development.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DevelopmentNodeModelTest {

  @Test
  void classifiesProcessingAndOutputResponsibilities() {
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.SQL.category());
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.SHELL.category());
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.HTTP.category());
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.PYTHON.category());
    assertEquals(DevelopmentNodeCategory.OUTPUT, DevelopmentNodeType.DATASET.category());
    assertEquals(DevelopmentNodeCategory.OUTPUT, DevelopmentNodeType.DATA_SERVICE.category());
  }

  @Test
  void taskLifecycleIsAnExplicitCapability() {
    assertTrue(DevelopmentNodeType.SQL.supportsTaskLifecycle());
    assertTrue(DevelopmentNodeType.SHELL.supportsTaskLifecycle());
    assertTrue(DevelopmentNodeType.HTTP.supportsTaskLifecycle());
    assertTrue(DevelopmentNodeType.PYTHON.supportsTaskLifecycle());
    assertFalse(DevelopmentNodeType.DATASET.supportsTaskLifecycle());
    assertFalse(DevelopmentNodeType.DATA_SERVICE.supportsTaskLifecycle());
  }

  @Test
  void keepsOutputNodesOutsideProcessingLifecycle() {
    assertTrue(DevelopmentNodeType.SQL.isProcessing());
    assertTrue(DevelopmentNodeType.SHELL.isProcessing());
    assertFalse(DevelopmentNodeType.DATASET.isProcessing());
    assertFalse(DevelopmentNodeType.DATA_SERVICE.isProcessing());
    assertTrue(DevelopmentNodeType.DATASET.isOutput());
    assertTrue(DevelopmentNodeType.DATA_SERVICE.isOutput());
  }
}
