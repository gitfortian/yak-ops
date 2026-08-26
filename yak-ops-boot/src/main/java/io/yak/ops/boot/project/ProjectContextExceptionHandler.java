package io.yak.ops.boot.project;

import io.yak.framework.common.Result;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts Project Space failures into stable HTTP and business error semantics. */
@RestControllerAdvice(basePackages = "io.yak.ops")
public class ProjectContextExceptionHandler {

  @ExceptionHandler(ProjectContextException.class)
  public ResponseEntity<Result<Void>> handle(ProjectContextException exception) {
    ProjectContextError error = exception.getError();
    Result<Void> body = Result.fail(error.getCode(), error.name() + ": " + error.getMessage());
    return ResponseEntity.status(statusOf(error)).body(body);
  }

  private HttpStatus statusOf(ProjectContextError error) {
    return switch (error) {
      case PROJECT_REQUIRED -> HttpStatus.BAD_REQUEST;
      case PROJECT_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case PROJECT_UNAVAILABLE -> HttpStatus.FORBIDDEN;
    };
  }
}
