package io.yak.ops.business.development.domain;

/** Raised when an authoring command uses a stale optimistic draft revision. */
public class DevelopmentDraftConflictException extends RuntimeException {

  public DevelopmentDraftConflictException(String message) {
    super(message);
  }
}
