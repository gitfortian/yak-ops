package io.yak.ops.business.development.domain;

import java.util.Locale;
import java.util.Optional;

/** Domain-level node types for data development. */
public enum DevelopmentNodeType {
  SQL(DevelopmentNodeCategory.PROCESSING),
  SHELL(DevelopmentNodeCategory.PROCESSING),
  HTTP(DevelopmentNodeCategory.PROCESSING),
  PYTHON(DevelopmentNodeCategory.PROCESSING),
  DATASET(DevelopmentNodeCategory.OUTPUT),
  DATA_SERVICE(DevelopmentNodeCategory.OUTPUT);

  private final DevelopmentNodeCategory category;

  DevelopmentNodeType(DevelopmentNodeCategory category) {
    this.category = category;
  }

  public DevelopmentNodeCategory category() {
    return category;
  }

  public boolean isProcessing() {
    return category == DevelopmentNodeCategory.PROCESSING;
  }

  public boolean isOutput() {
    return category == DevelopmentNodeCategory.OUTPUT;
  }

  public static Optional<DevelopmentNodeType> tryParse(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    try {
      return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
