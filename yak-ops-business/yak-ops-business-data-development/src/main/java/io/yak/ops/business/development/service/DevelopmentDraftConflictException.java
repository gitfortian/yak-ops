package io.yak.ops.business.development.service;

/** @deprecated Use the shared authoring conflict type from the domain boundary. */
@Deprecated
public class DevelopmentDraftConflictException
    extends io.yak.ops.business.development.domain.DevelopmentDraftConflictException {

  public DevelopmentDraftConflictException(String message) {
    super(message);
  }
}
