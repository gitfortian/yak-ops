package io.yak.ops.business.development.service;

/** Raised when a client tries to overwrite a newer server-side draft revision. */
public class DevelopmentDraftConflictException extends RuntimeException {

  public DevelopmentDraftConflictException(String message) {
    super(message);
  }
}
