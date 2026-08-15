package io.yak.ops.business.dataservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when a Data Service circuit breaker is open and the datasource is temporarily protected. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class DataServiceCircuitOpenException extends RuntimeException {

  public DataServiceCircuitOpenException(String message) {
    super(message);
  }
}
