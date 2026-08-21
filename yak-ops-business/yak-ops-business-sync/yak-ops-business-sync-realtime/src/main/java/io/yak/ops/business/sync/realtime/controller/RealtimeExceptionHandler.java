package io.yak.ops.business.sync.realtime.controller;
import io.yak.framework.common.Result;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=RealtimeJobController.class)
public class RealtimeExceptionHandler {@ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<Result<Void>> handle(RuntimeException e){return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail(e.getMessage()));}}
