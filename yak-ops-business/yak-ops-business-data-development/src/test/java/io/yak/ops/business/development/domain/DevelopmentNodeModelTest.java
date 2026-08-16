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
  void allowsOnlyThePhaseOneDagContract() {
    assertTrue(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.SQL, DevelopmentNodeType.SQL));
    assertTrue(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.SQL, DevelopmentNodeType.DATASET));
    assertTrue(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.SQL, DevelopmentNodeType.DATA_SERVICE));
    assertTrue(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.DATASET, DevelopmentNodeType.DATA_SERVICE));

    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.DATASET, DevelopmentNodeType.SQL));
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.DATASET, DevelopmentNodeType.DATASET));
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.DATA_SERVICE, DevelopmentNodeType.SQL));
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.DATA_SERVICE, DevelopmentNodeType.DATASET));
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.DATA_SERVICE, DevelopmentNodeType.DATA_SERVICE));
  }

  @Test
  void leavesUnspecifiedProcessingEdgesClosed() {
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.SHELL, DevelopmentNodeType.DATASET));
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.HTTP, DevelopmentNodeType.DATA_SERVICE));
    assertFalse(DevelopmentNodeConnectionPolicy.canConnect(
        DevelopmentNodeType.PYTHON, DevelopmentNodeType.SQL));
  }
}
