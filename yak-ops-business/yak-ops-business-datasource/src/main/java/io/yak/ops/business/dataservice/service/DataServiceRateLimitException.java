package io.yak.ops.business.dataservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class DataServiceRateLimitException extends RuntimeException {

  public DataServiceRateLimitException(String message) {
    super(message);
  }
}
